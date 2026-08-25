import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { PaymentMethod, PaymentRequest } from '../../../core/models/payment';
import { Subscription } from '../../../core/models/subscription';
import { Travel } from '../../../core/models/travel';
import { AuthService } from '../../../core/auth/auth';
import { ToastService } from '../../../core/notifications/toast';
import { Badge } from '../../../shared/ui/badge';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { PaymentMethodsService } from '../../payments/payment-methods';
import { PaymentsService } from '../../payments/payments';
import { TravelerStatsService } from '../../travelers/traveler-stats';
import { FeedbacksService } from '../feedbacks';
import { ReportsService } from '../reports';
import { SubscriptionsService } from '../subscriptions';
import { TravelsService } from '../travels';

// feat/traveler-frontend : page detail Traveler d'un voyage - abonnement/desabonnement,
// avis, signalement, paiement. Distincte de TravelForm (edition Admin/Manager).
@Component({
  selector: 'app-travel-detail',
  imports: [ReactiveFormsModule, RouterLink, Badge, PageHeader, Spinner],
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

  protected readonly feedbackSubmitting = signal(false);
  protected readonly feedbackSubmitted = signal(false);
  protected readonly feedbackForm = this.fb.nonNullable.group({
    rating: [5, [Validators.required, Validators.min(1), Validators.max(5)]],
    comment: ['', Validators.maxLength(2000)],
  });

  protected readonly reportSubmitting = signal(false);
  protected readonly reportSubmitted = signal(false);
  protected readonly reportForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(2000)]],
  });

  protected readonly paymentSubmitting = signal(false);
  protected readonly paymentForm = this.fb.nonNullable.group({
    paymentMethodId: ['', Validators.required],
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    currency: ['EUR', Validators.required],
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

          if (travel.price !== null) {
            this.paymentForm.patchValue({ amount: travel.price, currency: travel.currency ?? 'EUR' });
          }
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
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

  // Seul le manager du voyage est signalable depuis cette page : signaler "un autre traveler"
  // exigerait de connaitre son id, que rien n'expose a un simple Traveler (la liste des
  // abonnes d'un voyage est reservee au manager/admin, cf. SecurityConfig) - voir
  // docs/nouveautes-vs-travel-plan.md pour le detail de cette limitation assumee.
  protected submitReport(): void {
    const travel = this.travel();
    if (this.reportForm.invalid || !travel) {
      this.reportForm.markAllAsTouched();
      return;
    }

    this.reportSubmitting.set(true);
    this.reportsService
      .submit(this.travelId, {
        reportedType: 'MANAGER',
        reportedId: travel.managerId,
        reason: this.reportForm.getRawValue().reason,
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
      return;
    }

    const raw = this.paymentForm.getRawValue();
    const request: PaymentRequest = {
      travelId: this.travelId,
      ownerId: myUserId,
      paymentMethodId: raw.paymentMethodId,
      amount: raw.amount ?? 0,
      currency: raw.currency,
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
