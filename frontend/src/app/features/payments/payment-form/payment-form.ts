import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { PaymentMethod, PaymentRequest } from '../../../core/models/payment';
import { Travel } from '../../../core/models/travel';
import { User } from '../../../core/models/user';
import { TravelsService } from '../../travels/travels';
import { UsersService } from '../../users/users';
import { PaymentMethodsService } from '../payment-methods';
import { PaymentsService } from '../payments';

@Component({
  selector: 'app-payment-form',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, Spinner],
  templateUrl: './payment-form.html',
})
export class PaymentForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly paymentsService = inject(PaymentsService);
  private readonly travelsService = inject(TravelsService);
  private readonly usersService = inject(UsersService);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly travels = signal<Travel[]>([]);
  protected readonly users = signal<User[]>([]);
  protected readonly paymentMethods = signal<PaymentMethod[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    travelId: ['', Validators.required],
    ownerId: ['', Validators.required],
    paymentMethodId: ['', Validators.required],
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    currency: ['EUR', Validators.required],
  });

  ngOnInit(): void {
    forkJoin({
      travels: this.travelsService.findAll(),
      users: this.usersService.findAll(),
      paymentMethods: this.paymentMethodsService.findAll(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ travels, users, paymentMethods }) => {
          this.travels.set(travels);
          this.users.set(users);
          this.paymentMethods.set(paymentMethods);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toastService.error('Certains champs sont manquants ou invalides.');
      return;
    }

    const raw = this.form.getRawValue();
    const request: PaymentRequest = {
      travelId: raw.travelId,
      ownerId: raw.ownerId,
      paymentMethodId: raw.paymentMethodId,
      amount: raw.amount ?? 0,
      currency: raw.currency,
    };

    this.saving.set(true);
    this.paymentsService
      .create(request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.toastService.success('Paiement créé');
          this.router.navigate(['/payments']);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
