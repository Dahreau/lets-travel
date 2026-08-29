import { SlicePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/auth/auth';
import { extractErrorMessage } from '../../../core/http/api-error';
import { User } from '../../../core/models/user';
import { ToastService } from '../../../core/notifications/toast';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { UsersService } from '../../users/users';

// fix/audit-gaps (troubleshooting.md #41) : page self-service RGPD, ouverte a tout role
// authentifie (voir app.routes.ts - pas de guard supplementaire au-dela du authGuard deja sur
// le Shell parent). Couvre le droit d'acces/portabilite (export JSON de GET /api/users/me) et
// le droit a l'effacement (DELETE /api/users/me) - meme pattern ConfirmDialog que UserList
// pour la suppression, memes signaux loading/deleting.
@Component({
  selector: 'app-my-data',
  imports: [RouterLink, SlicePipe, ConfirmDialog, PageHeader, Spinner],
  templateUrl: './my-data.html',
})
export class MyData implements OnInit {
  private readonly usersService = inject(UsersService);
  private readonly authService = inject(AuthService);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly profile = signal<User | null>(null);
  protected readonly loading = signal(true);
  protected readonly deleting = signal(false);
  protected readonly confirmingDelete = signal(false);

  ngOnInit(): void {
    this.usersService
      .me()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (profile) => this.profile.set(profile),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  // Droit d'acces/portabilite (enonce RGPD) : telecharge exactement ce que renvoie
  // GET /api/users/me, sans reformatage - c'est deja la representation complete du profil
  // (adresse, consentement, dates), pas la peine d'inventer un autre format d'export.
  protected exportData(): void {
    const profile = this.profile();
    if (!profile) {
      return;
    }
    const blob = new Blob([JSON.stringify(profile, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `mes-donnees-${profile.id}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  protected confirmDelete(): void {
    this.deleting.set(true);
    this.usersService
      .deleteMe()
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => {
          // Compte de connexion deja supprime cote auth-service a ce stade (voir
          // UserService.deleteMe cote backend) - le token local n'a plus aucune valeur,
          // logout() + redirection /login evitent un appel API voue a un 401.
          this.authService.logout();
          this.router.navigateByUrl('/login');
        },
        error: (error: unknown) => {
          this.confirmingDelete.set(false);
          this.toastService.error(extractErrorMessage(error));
        },
      });
  }
}
