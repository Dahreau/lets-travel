import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth/auth';
import { extractErrorMessage } from '../../core/http/api-error';
import { ManagerStats } from '../../core/models/manager-stats';
import { Travel } from '../../core/models/travel';
import { ToastService } from '../../core/notifications/toast';
import { Badge } from '../../shared/ui/badge';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { ManagerStatsService } from '../manager/manager-stats';
import { PaymentMethodsService } from '../payments/payment-methods';
import { PaymentsService } from '../payments/payments';
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
  imports: [PageHeader, Spinner, Badge, RouterLink],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly usersService = inject(UsersService);
  private readonly travelsService = inject(TravelsService);
  private readonly paymentsService = inject(PaymentsService);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly managerStatsService = inject(ManagerStatsService);
  private readonly toastService = inject(ToastService);

  // GET /api/users, /api/payments, /api/payment-methods sont réservés à l'Admin : un Travel
  // Manager qui atterrit ici (route /dashboard partagée) prend une branche dédiée, qui ne les
  // appelle jamais - cf. respectivement user-service et payment-service SecurityConfig.
  protected readonly isManager = computed(() => this.authService.currentUser()?.role === 'TRAVEL_MANAGER');

  protected readonly loading = signal(true);
  protected readonly stats = signal<Stats | null>(null);
  protected readonly managerStats = signal<ManagerStats | null>(null);
  protected readonly myTravels = signal<Travel[]>([]);

  ngOnInit(): void {
    if (this.isManager()) {
      this.loadManagerDashboard();
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
