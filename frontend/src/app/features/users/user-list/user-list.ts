import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { Badge } from '../../../shared/ui/badge';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { User } from '../../../core/models/user';
import { UsersService } from '../users';

@Component({
  selector: 'app-user-list',
  imports: [RouterLink, Badge, ConfirmDialog, PageHeader, Spinner],
  templateUrl: './user-list.html',
})
export class UserList implements OnInit {
  private readonly usersService = inject(UsersService);
  private readonly toastService = inject(ToastService);

  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly deleteTarget = signal<User | null>(null);
  protected readonly deleting = signal(false);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.usersService
      .findAll()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (users) => this.users.set(users),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) {
      return;
    }

    this.deleting.set(true);
    this.usersService
      .delete(target.id)
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => {
          this.users.update((users) => users.filter((u) => u.id !== target.id));
          this.toastService.success(`Utilisateur ${target.firstName} ${target.lastName} supprimé`);
          this.deleteTarget.set(null);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
