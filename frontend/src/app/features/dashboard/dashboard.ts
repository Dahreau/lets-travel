import { SlicePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth';
import { extractErrorMessage } from '../../core/http/api-error';
import { ManagerStats } from '../../core/models/manager-stats';
import { Subscription } from '../../core/models/subscription';
import { Travel } from '../../core/models/travel';
import { TravelerStats } from '../../core/models/traveler-stats';
import { ToastService } from '../../core/notifications/toast';
import { Badge } from '../../shared/ui/badge';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { ManagerStatsService } from '../manager/manager-stats';
import { PaymentMethodsService } from '../payments/payment-methods';
import { PaymentsService } from '../payments/payments';
import { TravelerStatsService } from '../travelers/traveler-stats';
import { TravelsService } from '../travels/travels';
import { UsersService } from '../users/users';

interface Stats {
  users: number;
  travels: number;
  payments: number;
  paymentMethods: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [PageHeader, Spinner, Badge, RouterLink, SlicePipe],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly usersService = inject(UsersService);
  private readonly travelsService = inject(TravelsService);
  private readonly paymentsService = inject(PaymentsService);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly managerStatsService = inject(ManagerStatsService);
  private readonly travelerStatsService = inject(TravelerStatsService);
  private readonly toastService = inject(ToastService);

  // GET /api/users, /api/payments, /api/payment-methods (liste globale) sont reserves a
  // l'Admin : Manager et Traveler prennent chacun une branche dediee qui ne les appelle
  // jamais - cf. respectivement user-service et payment-service SecurityConfig.
  protected readonly isManager = computed(() => this.authService.currentUser()?.role === 'TRAVEL_MANAGER');
  protected readonly isTraveler = computed(() => this.authService.currentUser()?.role === 'TRAVELER');

  protected readonly loading = signal(true);
  protected readonly stats = signal<Stats | null>(null);
  protected readonly managerStats = signal<ManagerStats | null>(null);
  protected readonly myTravels = signal<Travel[]>([]);
  protected readonly travelerStats = signal<TravelerStats | null>(null);
  protected readonly recommendations = signal<Travel[]>([]);
  protected readonly mySubscriptions = signal<Subscription[]>([]);

  ngOnInit(): void {
    if (this.isManager()) {
      this.loadManagerDashboard();
    } else if (this.isTraveler()) {
      this.loadTravelerDashboard();
    } else {
      this.loadAdminDashboard();
    }
  }

  private loadManagerDashboard(): void {
    const myUserId = this.authService.userId();
    forkJoin({
      stats: this.managerStatsService.myStats(),
      travels: this.travelsService.findAll(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ stats, travels }) => {
          this.managerStats.set(stats);
          this.myTravels.set(travels.filter((t) => t.managerId === myUserId));
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  // Recommandations personnalisees (feat/search-and-recommendations, Neo4j) + historique
  // d'abonnements + stats perso (feat/traveler-frontend) - voir docs/lets-travel_project.md,
  // section Traveler.
  private loadTravelerDashboard(): void {
    forkJoin({
      stats: this.travelerStatsService.myStats(),
      recommendations: this.travelsService.recommendations(),
      subscriptions: this.travelerStatsService.mySubscriptions(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ stats, recommendations, subscriptions }) => {
          this.travelerStats.set(stats);
          this.recommendations.set(recommendations);
          this.mySubscriptions.set(subscriptions);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  private loadAdminDashboard(): void {
    forkJoin({
      users: this.usersService.findAll(),
      travels: this.travelsService.findAll(),
      payments: this.paymentsService.findAll(),
      paymentMethods: this.paymentMethodsService.findAll(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ users, travels, payments, paymentMethods }) => {
          this.stats.set({
            users: users.length,
            travels: travels.length,
            payments: payments.length,
            paymentMethods: paymentMethods.length,
          });
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
