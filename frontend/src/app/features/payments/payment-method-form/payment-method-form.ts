import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AuthService } from '../../../core/auth/auth';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { METHOD_TYPES, PROVIDER_TYPES, PaymentMethod, PaymentMethodRequest } from '../../../core/models/payment';
import { User } from '../../../core/models/user';
import { UsersService } from '../../users/users';
import { PaymentMethodsService } from '../payment-methods';

@Component({
  selector: 'app-payment-method-form',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, Spinner],
  templateUrl: './payment-method-form.html',
})
export class PaymentMethodForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly paymentMethodsService = inject(PaymentMethodsService);
  private readonly usersService = inject(UsersService);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly providers = PROVIDER_TYPES;
  protected readonly methodTypes = METHOD_TYPES;
  protected readonly methodId = signal<string | null>(null);
  protected readonly isEdit = computed(() => this.methodId() !== null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly users = signal<User[]>([]);
  // GET /api/users est reserve a l'Admin : un Traveler qui gere son propre moyen de paiement
  // n'a jamais a choisir un owner arbitraire (feat/traveler-frontend - sinon 403 garanti,
  // meme piege que PaymentForm/ownerId, voir docs/nouveautes-vs-travel-plan.md).
  protected readonly isTraveler = computed(() => this.authService.currentUser()?.role === 'TRAVELER');

  protected readonly form = this.fb.nonNullable.group({
    ownerId: ['', Validators.required],
    provider: ['STRIPE', Validators.required],
    type: ['CARD', Validators.required],
    providerToken: ['', Validators.required],
    brand: [''],
    last4: [''],
    isDefault: [false],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');

    if (this.isTraveler()) {
      this.form.controls.ownerId.setValue(this.authService.userId() ?? '');
      this.loadForTraveler(id);
      return;
    }

    this.loadForAdminOrManager(id);
  }

  private loadForTraveler(id: string | null): void {
    if (!id) {
      this.loading.set(false);
      return;
    }

    this.methodId.set(id);
    this.paymentMethodsService
      .findById(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (method) => this.patchMethod(method),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  private loadForAdminOrManager(id: string | null): void {
    const users$ = this.usersService.findAll();
    if (!id) {
      users$.pipe(finalize(() => this.loading.set(false))).subscribe({
        next: (users) => this.users.set(users),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
      return;
    }

    this.methodId.set(id);
    forkJoin({ users: users$, method: this.paymentMethodsService.findById(id) })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ users, method }) => {
          this.users.set(users);
          this.patchMethod(method);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  private patchMethod(method: PaymentMethod): void {
    this.form.patchValue({
      ownerId: method.ownerId,
      provider: method.provider,
      type: method.type,
      providerToken: '••••••••',
      brand: method.brand ?? '',
      last4: method.last4 ?? '',
      isDefault: method.isDefault,
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toastService.error('Certains champs sont manquants ou invalides.');
      return;
    }

    const raw = this.form.getRawValue();
    const request: PaymentMethodRequest = {
      ownerId: raw.ownerId,
      provider: raw.provider as PaymentMethodRequest['provider'],
      type: raw.type as PaymentMethodRequest['type'],
      providerToken: raw.providerToken,
      brand: raw.brand || null,
      last4: raw.last4 || null,
      isDefault: raw.isDefault,
    };

    this.saving.set(true);
    const id = this.methodId();
    const request$ = id
      ? this.paymentMethodsService.update(id, request)
      : this.paymentMethodsService.create(request);

    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.toastService.success(id ? 'Moyen de paiement mis à jour' : 'Moyen de paiement créé');
        this.router.navigate(['/payment-methods']);
      },
      error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
    });
  }
}
