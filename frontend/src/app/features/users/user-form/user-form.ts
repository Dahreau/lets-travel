import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { UserRequest, UserRole } from '../../../core/models/user';
import { UsersService } from '../users';

@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, Spinner],
  templateUrl: './user-form.html',
})
export class UserForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly usersService = inject(UsersService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly roles: UserRole[] = ['TRAVELER', 'ADMIN'];
  protected readonly userId = signal<string | null>(null);
  protected readonly isEdit = computed(() => this.userId() !== null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly hasAddress = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    role: ['TRAVELER' as UserRole, Validators.required],
    address: this.fb.nonNullable.group({
      street: ['', Validators.required],
      city: ['', Validators.required],
      postalCode: ['', Validators.required],
      country: ['', Validators.required],
    }),
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      return;
    }

    this.userId.set(id);
    this.loading.set(true);
    this.usersService
      .findById(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (user) => {
          this.form.patchValue({
            firstName: user.firstName,
            lastName: user.lastName,
            email: user.email,
            phone: user.phone ?? '',
            role: user.role,
          });
          if (user.address) {
            this.hasAddress.set(true);
            this.form.controls.address.patchValue(user.address);
          }
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected toggleAddress(): void {
    this.hasAddress.update((value) => !value);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toastService.error('Certains champs sont manquants ou invalides.');
      return;
    }

    const raw = this.form.getRawValue();
    const request: UserRequest = {
      firstName: raw.firstName,
      lastName: raw.lastName,
      email: raw.email,
      phone: raw.phone || null,
      role: raw.role,
      address: this.hasAddress() ? raw.address : null,
    };

    this.saving.set(true);
    const id = this.userId();
    const request$ = id ? this.usersService.update(id, request) : this.usersService.create(request);

    request$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.toastService.success(id ? 'Utilisateur mis à jour' : 'Utilisateur créé');
        this.router.navigate(['/users']);
      },
      error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
    });
  }
}
