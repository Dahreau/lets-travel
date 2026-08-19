import { SlicePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { Badge } from '../../../shared/ui/badge';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { PaymentMethod } from '../../../core/models/payment';
import { PaymentMethodsService } from '../payment-methods';

@Component({
  selector: 'app-payment-method-list',
  imports: [RouterLink, SlicePipe, Badge, ConfirmDialog, PageHeader, Spinner],
  templateUrl: './payment-method-list.html',
})
export class PaymentMethodList implements OnInit {
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly toastService = inject(ToastService);

  protected readonly methods = signal<PaymentMethod[]>([]);
  protected readonly loading = signal(true);
  protected readonly deleteTarget = signal<PaymentMethod | null>(null);
  protected readonly deleting = signal(false);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.paymentMethodsService
      .findAll()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (methods) => this.methods.set(methods),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) {
      return;
    }

    this.deleting.set(true);
    this.paymentMethodsService
      .delete(target.id)
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => {
          this.methods.update((methods) => methods.filter((m) => m.id !== target.id));
          this.toastService.success('Moyen de paiement supprimé');
          this.deleteTarget.set(null);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
