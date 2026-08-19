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
import { Travel } from '../../../core/models/travel';
import { TravelsService } from '../travels';

@Component({
  selector: 'app-travel-list',
  imports: [RouterLink, SlicePipe, Badge, ConfirmDialog, PageHeader, Spinner],
  templateUrl: './travel-list.html',
})
export class TravelList implements OnInit {
  private readonly travelsService = inject(TravelsService);
  private readonly toastService = inject(ToastService);

  protected readonly travels = signal<Travel[]>([]);
  protected readonly loading = signal(true);
  protected readonly deleteTarget = signal<Travel | null>(null);
  protected readonly deleting = signal(false);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.travelsService
      .findAll()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (travels) => this.travels.set(travels),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) {
      return;
    }

    this.deleting.set(true);
    this.travelsService
      .delete(target.id)
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => {
          this.travels.update((travels) => travels.filter((t) => t.id !== target.id));
          this.toastService.success(`Voyage "${target.title}" supprimé`);
          this.deleteTarget.set(null);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
