import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { AddressFields } from '../../../shared/ui/address-fields';
import { NameContactFields } from '../../../shared/ui/name-contact-fields';
import { UserRequest, UserRole } from '../../../core/models/user';
import { UsersService } from '../users';

@Component({
  selector: 'app-user-form',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, Spinner, NameContactFields, AddressFields],
  templateUrl: './user-form.html',
})
export class UserForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly usersService = inject(UsersService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  // TRAVEL_MANAGER ajouté par feat/manager-frontend : c'était le seul moyen de créer un
  // compte manager depuis l'UI (pas d'endpoint de "promotion" séparé, cf. UserService).
  protected readonly roles: UserRole[] = ['TRAVELER', 'TRAVEL_MANAGER', 'ADMIN'];
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
    // Pas de Validators.required ici : verifie manuellement dans submit() uniquement
    // quand wantsAccount() est coche.
    username: [''],
    password: [''],
  });

  // Cree un compte de connexion avec le profil - jamais propose pour ADMIN ni en
  // edition (voir UserService.create cote backend).
  protected readonly wantsAccount = signal(false);
  private readonly selectedRole = toSignal(this.form.controls.role.valueChanges, { initialValue: 'TRAVELER' as UserRole });
  protected readonly canCreateAccount = computed(() => !this.isEdit() && this.selectedRole() !== 'ADMIN');

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

  protected toggleWantsAccount(): void {
    this.wantsAccount.update((value) => !value);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.toastService.error('Certains champs sont manquants ou invalides.');
      return;
    }

    const raw = this.form.getRawValue();

    if (this.canCreateAccount() && this.wantsAccount() && (!raw.username.trim() || !raw.password.trim())) {
      this.toastService.error("Nom d'utilisateur et mot de passe sont requis pour créer un compte de connexion.");
      return;
    }

    const request: UserRequest = {
      firstName: raw.firstName,
      lastName: raw.lastName,
      email: raw.email,
      phone: raw.phone || null,
      role: raw.role,
      address: this.hasAddress() ? raw.address : null,
      ...(this.canCreateAccount() && this.wantsAccount()
        ? { username: raw.username.trim(), password: raw.password }
        : {}),
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
