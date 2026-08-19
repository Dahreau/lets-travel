import { Component, OnInit, inject, signal } from '@angular/core';
import { finalize, forkJoin } from 'rxjs';
import { extractErrorMessage } from '../../core/http/api-error';
import { ToastService } from '../../core/notifications/toast';
import { PageHeader } from '../../shared/ui/page-header';
import { Spinner } from '../../shared/ui/spinner';
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
  imports: [PageHeader, Spinner],
  templateUrl: './dashboard.html',
})
export class Dashboard implements OnInit {
  private readonly usersService = inject(UsersService);
  private readonly travelsService = inject(TravelsService);
  private readonly paymentsService = inject(PaymentsService);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly toastService = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly stats = signal<Stats | null>(null);

  ngOnInit(): void {
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
