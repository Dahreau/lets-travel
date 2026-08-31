import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { PaymentMethod, PaymentRequest } from '../../../core/models/payment';
import { Subscription } from '../../../core/models/subscription';
import { Travel } from '../../../core/models/travel';
import { User } from '../../../core/models/user';
import { AuthService } from '../../../core/auth/auth';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { TravelDestinationsList } from '../../../shared/ui/travel-destinations-list';
import { TravelSummaryCard } from '../../../shared/ui/travel-summary-card';
import { PaymentMethodsService } from '../../payments/payment-methods';
import { PaymentsService } from '../../payments/payments';
import { TravelerStatsService } from '../../travelers/traveler-stats';
import { UsersService } from '../../users/users';
import { FeedbacksService } from '../feedbacks';
import { ReportsService } from '../reports';
import { SubscriptionsService } from '../subscriptions';
import { TravelsService } from '../travels';

const MANAGER_TARGET = 'MANAGER';

// feat/traveler-frontend : page detail Traveler d'un voyage - abonnement/desabonnement,
// avis, signalement, paiement. Distincte de TravelForm (edition Admin/Manager).
@Component({
  selector: 'app-travel-detail',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    PageHeader,
    Spinner,
    TravelSummaryCard,
    TravelDestinationsList,
  ],
  templateUrl: './travel-detail.html',
})
export class TravelDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly travelsService = inject(TravelsService);
  private readonly subscriptionsService = inject(SubscriptionsService);
  private readonly travelerStatsService = inject(TravelerStatsService);
  private readonly feedbacksService = inject(FeedbacksService);
  private readonly reportsService = inject(ReportsService);
  private readonly paymentsService = inject(PaymentsService);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly usersService = inject(UsersService);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);

  protected readonly travelId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly loading = signal(true);
  protected readonly travel = signal<Travel | null>(null);
  protected readonly paymentMethods = signal<PaymentMethod[]>([]);

  protected readonly activeSubscription = signal<Subscription | null>(null);
  protected readonly hasParticipated = signal(false);
  protected readonly subscribing = signal(false);
  protected readonly unsubscribing = signal(false);
  protected readonly coTravelers = signal<User[]>([]);

  protected readonly feedbackSubmitting = signal(false);
  protected readonly feedbackSubmitted = signal(false);
  protected readonly feedbackForm = this.fb.nonNullable.group({
    rating: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
    comment: ['', Validators.maxLength(2000)],
  });

  protected readonly reportSubmitting = signal(false);
  protected readonly reportSubmitted = signal(false);
  protected readonly reportForm = this.fb.nonNullable.group({
    target: [MANAGER_TARGET, Validators.required],
    reason: ['', [Validators.required, Validators.maxLength(2000)]],
  });

  protected readonly paymentSubmitting = signal(false);
  // amount/currency ne sont plus dans le formulaire : le montant reel vient de t.price/t.currency
  // (affiche en lecture seule), le backend l'ignorerait de toute facon (voir PaymentRequest).
  protected readonly paymentForm = this.fb.nonNullable.group({
    paymentMethodId: ['', Validators.required],
  });

  ngOnInit(): void {
    forkJoin({
      travel: this.travelsService.findById(this.travelId),
      subscriptions: this.travelerStatsService.mySubscriptions(),
      paymentMethods: this.paymentMethodsService.findAll(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ travel, subscriptions, paymentMethods }) => {
          this.travel.set(travel);
          this.paymentMethods.set(paymentMethods);

          const mine = subscriptions.filter((s) => s.travelId === this.travelId);
          this.hasParticipated.set(mine.length > 0);
          this.activeSubscription.set(mine.find((s) => s.status === 'ACTIVE') ?? null);

          if (this.hasParticipated()) {
            this.loadCoTravelers();
          }
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  // Charge apres coup, seulement si le traveler a participe : sinon SubscriptionService.coTravelerIds
  // renvoie 403 (voir SecurityConfig, meme regle que le report lui-meme).
  private loadCoTravelers(): void {
    this.subscriptionsService
      .coTravelers(this.travelId)
      .pipe(
        switchMap((ids) =>
          ids.length === 0
            ? of([])
            : forkJoin(ids.map((id) => this.usersService.findById(id).pipe(catchError(() => of(null))))),
        ),
      )
      .subscribe((users) => this.coTravelers.set(users.filter((u): u is User => u !== null)));
  }

  protected subscribe(): void {
    this.subscribing.set(true);
    this.subscriptionsService
      .subscribe(this.travelId)
      .pipe(finalize(() => this.subscribing.set(false)))
      .subscribe({
        next: (subscription) => {
          this.activeSubscription.set(subscription);
          this.hasParticipated.set(true);
          this.toastService.success('Abonnement confirmé');
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected unsubscribe(): void {
    const subscription = this.activeSubscription();
    if (!subscription) {
      return;
    }

    this.unsubscribing.set(true);
    this.subscriptionsService
      .unsubscribe(this.travelId, subscription.id)
      .pipe(finalize(() => this.unsubscribing.set(false)))
      .subscribe({
        next: () => {
          this.activeSubscription.set(null);
          this.toastService.success('Abonnement annulé');
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected submitFeedback(): void {
    if (this.feedbackForm.invalid) {
      this.feedbackForm.markAllAsTouched();
      this.toastService.error('Note obligatoire entre 1 et 5.');
      return;
    }

    const raw = this.feedbackForm.getRawValue();
    this.feedbackSubmitting.set(true);
    this.feedbacksService
      .submit(this.travelId, { rating: raw.rating, comment: raw.comment })
      .pipe(finalize(() => this.feedbackSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.feedbackSubmitted.set(true);
          this.toastService.success('Merci pour votre avis');
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected submitReport(): void {
    const travel = this.travel();
    if (this.reportForm.invalid || !travel) {
      this.reportForm.markAllAsTouched();
      this.toastService.error('Le motif du signalement est obligatoire.');
      return;
    }

    const raw = this.reportForm.getRawValue();
    const isManagerTarget = raw.target === MANAGER_TARGET;

    this.reportSubmitting.set(true);
    this.reportsService
      .submit(this.travelId, {
        reportedType: isManagerTarget ? 'MANAGER' : 'TRAVELER',
        reportedId: isManagerTarget ? travel.managerId : raw.target,
        reason: raw.reason,
      })
      .pipe(finalize(() => this.reportSubmitting.set(false)))
      .subscribe({
        next: () => {
          this.reportSubmitted.set(true);
          this.toastService.success('Signalement envoyé');
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected submitPayment(): void {
    const travel = this.travel();
    const myUserId = this.authService.userId();
    if (this.paymentForm.invalid || !travel || !myUserId) {
      this.paymentForm.markAllAsTouched();
      this.toastService.error('Sélectionnez un moyen de paiement.');
      return;
    }

    const raw = this.paymentForm.getRawValue();
    const request: PaymentRequest = {
      travelId: this.travelId,
      ownerId: myUserId,
      paymentMethodId: raw.paymentMethodId,
    };

    this.paymentSubmitting.set(true);
    this.paymentsService
      .create(request)
      .pipe(finalize(() => this.paymentSubmitting.set(false)))
      .subscribe({
        next: () => this.toastService.success('Paiement effectué'),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected isPastEndDate(endDate: string): boolean {
    return new Date(endDate).getTime() < Date.now();
  }
}
