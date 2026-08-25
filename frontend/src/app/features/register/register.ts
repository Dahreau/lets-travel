import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth';
import { extractErrorMessage } from '../../core/http/api-error';
import { UserRegistrationRequest } from '../../core/models/user';
import { UsersService } from '../users/users';

// Inscription publique Traveler en 2 appels (deja existants cote backend, feat/traveler-experience) :
// POST /api/users/register cree le profil, puis POST /api/auth/register cree le compte avec le
// userId renvoye et connecte immediatement - meme enchainement que Login, avec un appel de plus.
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly usersService = inject(UsersService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly hasAddress = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    username: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(6)]],
    address: this.fb.nonNullable.group({
      street: ['', Validators.required],
      city: ['', Validators.required],
      postalCode: ['', Validators.required],
      country: ['', Validators.required],
    }),
  });

  // Groupe desactive par defaut : les champs required de l'adresse ne doivent
  // bloquer le formulaire que si le traveler choisit de la renseigner.
  ngOnInit(): void {
    this.form.controls.address.disable();
  }

  protected toggleAddress(): void {
    const next = !this.hasAddress();
    this.hasAddress.set(next);
    this.form.controls.address[next ? 'enable' : 'disable']();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const profile: UserRegistrationRequest = {
      firstName: raw.firstName,
      lastName: raw.lastName,
      email: raw.email,
      phone: raw.phone || null,
      address: this.hasAddress() ? raw.address : null,
    };

    this.loading.set(true);
    this.errorMessage.set(null);

    this.usersService
      .register(profile)
      .pipe(
        switchMap((user) =>
          this.authService.register({ username: raw.username, password: raw.password, userId: user.id }),
        ),
        switchMap(() => this.authService.me()),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: () => this.router.navigateByUrl('/dashboard'),
        error: (error: unknown) => {
          this.errorMessage.set(extractErrorMessage(error, "L'inscription a échoué"));
        },
      });
  }
}
