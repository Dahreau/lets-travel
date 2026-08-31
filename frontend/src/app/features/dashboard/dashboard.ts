import { DecimalPipe, SlicePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth';
import { extractErrorMessage } from '../../core/http/api-error';
import { AdminManagerRanking, AdminMonthlyRevenue, AdminTravelRanking } from '../../core/models/admin-stats';
import { Feedback } from '../../core/models/feedback';
import { ManagerStats, ManagerTravelStats } from '../../core/models/manager-stats';
import { PaymentMethod } from '../../core/models/payment';
import { ReportResponse } from '../../core/models/report';
import { Subscription } from '../../core/models/subscription';
import { Travel } from '../../core/models/travel';
import { TravelerStats } from '../../core/models/traveler-stats';
import { ToastService } from '../../core/notifications/toast';
import { Badge } from '../../shared/ui/badge';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
import { AdminStatsService } from '../admin/admin-stats';
import { ManagerStatsService } from '../manager/manager-stats';
import { PaymentMethodsService } from '../payments/payment-methods';
import { PaymentsService } from '../payments/payments';
import { ReportsService } from '../travels/reports';
import { TravelerStatsService } from '../travelers/traveler-stats';
import { TravelsService } from '../travels/travels';
import { User } from '../../core/models/user';
import { UsersService } from '../users/users';

interface Stats {
  users: number;
  travels: number;
  payments: number;
  paymentMethods: number;
}

@Component({
  selector: 'app-dashboard',
  imports: [PageHeader, Spinner, Badge, RouterLink, SlicePipe, DecimalPipe],
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
  private readonly adminStatsService = inject(AdminStatsService);
  private readonly reportsService = inject(ReportsService);
  private readonly toastService = inject(ToastService);

  // GET /api/users, /api/payments, /api/payment-methods (liste globale) = Admin-only : Manager
  // et Traveler prennent chacun leur propre branche qui ne les appelle jamais.
  protected readonly isManager = computed(() => this.authService.currentUser()?.role === 'TRAVEL_MANAGER');
  protected readonly isTraveler = computed(() => this.authService.currentUser()?.role === 'TRAVELER');

  protected readonly loading = signal(true);
  protected readonly stats = signal<Stats | null>(null);
  protected readonly managerRankings = signal<AdminManagerRanking[]>([]);
  protected readonly travelRankings = signal<AdminTravelRanking[]>([]);
  protected readonly monthlyRevenue = signal<AdminMonthlyRevenue[]>([]);
  protected readonly reports = signal<ReportResponse[]>([]);
  protected readonly reportedNames = signal<Map<string, string>>(new Map());
  protected readonly reporterNames = signal<Map<string, string>>(new Map());
  protected readonly managerStats = signal<ManagerStats | null>(null);
  protected readonly myTravels = signal<Travel[]>([]);
  protected readonly travelStatsById = computed(() => {
    const map = new Map<string, ManagerTravelStats>();
    this.managerStats()?.travels.forEach((t) => map.set(t.travelId, t));
    return map;
  });
  protected readonly travelerStats = signal<TravelerStats | null>(null);
  protected readonly recommendations = signal<Travel[]>([]);
  protected readonly mySubscriptions = signal<Subscription[]>([]);
  protected readonly myFeedbacks = signal<Feedback[]>([]);
  protected readonly myReports = signal<ReportResponse[]>([]);
  protected readonly myPaymentMethods = signal<PaymentMethod[]>([]);

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

  // Stats/recommandations/abonnements existants + moyens de paiement (feat/admin-dashboard-overview,
  // deja scopes au caller cote backend) - voir docs/lets-travel_project.md, section Traveler.
  private loadTravelerDashboard(): void {
    forkJoin({
      stats: this.travelerStatsService.myStats(),
      recommendations: this.travelsService.recommendations(),
      subscriptions: this.travelerStatsService.mySubscriptions(),
      paymentMethods: this.paymentMethodsService.findAll(),
      feedbacks: this.travelerStatsService.myFeedbacks(),
      reports: this.travelerStatsService.myReports(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ stats, recommendations, subscriptions, paymentMethods, feedbacks, reports }) => {
          this.travelerStats.set(stats);
          this.recommendations.set(recommendations);
          this.mySubscriptions.set(subscriptions);
          this.myPaymentMethods.set(paymentMethods);
          this.myFeedbacks.set(feedbacks);
          this.myReports.set(reports);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  // Vue d'ensemble Admin : compteurs globaux + classements (AdminStatsService) + moderation
  // des signalements (ReportController, deja expose, Admin uniquement).
  private loadAdminDashboard(): void {
    forkJoin({
      users: this.usersService.findAll(),
      travels: this.travelsService.findAll(),
      payments: this.paymentsService.findAll(),
      paymentMethods: this.paymentMethodsService.findAll(),
      managerRankings: this.adminStatsService.managerRankings(),
      travelRankings: this.adminStatsService.travelRankings(),
      monthlyRevenue: this.adminStatsService.monthlyRevenue(),
      reports: this.reportsService.listAll(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ users, travels, payments, paymentMethods, managerRankings, travelRankings, monthlyRevenue, reports }) => {
          this.stats.set({
            users: users.length,
            travels: travels.length,
            payments: payments.length,
            paymentMethods: paymentMethods.length,
          });
          this.managerRankings.set(managerRankings);
          this.travelRankings.set(travelRankings);
          this.monthlyRevenue.set(monthlyRevenue);
          this.reports.set(reports);
          this.resolveUserNames(reports);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  // Meme pattern que TravelDetail.loadCoTravelers : reporterId/reportedId (tous deux des
  // User) restent des UUID nus cote travel-service (pas de FK cross-service possible).
  private resolveUserNames(reports: ReportResponse[]): void {
    const ids = [...new Set([...reports.map((r) => r.reportedId), ...reports.map((r) => r.reporterId)])];
    if (ids.length === 0) {
      return;
    }

    forkJoin(ids.map((id) => this.usersService.findById(id).pipe(catchError(() => of(null))))).subscribe(
      (users) => {
        const map = new Map<string, string>();
        users.forEach((user: User | null, i) => {
          if (user) {
            map.set(ids[i], `${user.firstName} ${user.lastName}`);
          }
        });
        this.reportedNames.set(map);
        this.reporterNames.set(map);
      },
    );
  }
}
