import { SlicePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { Badge } from '../../../shared/ui/badge';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { Payment } from '../../../core/models/payment';
import { PaymentsService } from '../payments';

@Component({
  selector: 'app-payment-list',
  imports: [RouterLink, SlicePipe, Badge, PageHeader, Spinner],
  templateUrl: './payment-list.html',
})
export class PaymentList implements OnInit {
  private readonly paymentsService = inject(PaymentsService);
  private readonly toastService = inject(ToastService);

  protected readonly payments = signal<Payment[]>([]);
  protected readonly loading = signal(true);
  protected readonly refundingId = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.paymentsService
      .findAll()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (payments) => this.payments.set(payments),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected refund(payment: Payment): void {
    this.refundingId.set(payment.id);
    this.paymentsService
      .refund(payment.id)
      .pipe(finalize(() => this.refundingId.set(null)))
      .subscribe({
        next: (updated) => {
          this.payments.update((payments) => payments.map((p) => (p.id === updated.id ? updated : p)));
          this.toastService.success('Paiement remboursé');
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
