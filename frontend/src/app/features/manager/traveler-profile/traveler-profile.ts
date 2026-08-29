import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { User } from '../../../core/models/user';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { UsersService } from '../../users/users';

// fix/audit-gaps : "view profiles" des abonnes depuis la liste d'un voyage (enonce, role
// Travel Manager) - lecture seule, reutilise GET /api/users/{id} deja restreint par #38
// (un manager ne voit que le profil d'un de SES abonnes).
@Component({
  selector: 'app-traveler-profile',
  imports: [RouterLink, PageHeader, Spinner],
  templateUrl: './traveler-profile.html',
})
export class TravelerProfile implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly usersService = inject(UsersService);
  private readonly toastService = inject(ToastService);

  protected readonly travelerId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly loading = signal(true);
  protected readonly traveler = signal<User | null>(null);

  ngOnInit(): void {
    this.usersService
      .findById(this.travelerId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (user) => this.traveler.set(user),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
