import { DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ManagerPublicStats } from '../../../core/models/manager-stats';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { ManagerStatsService } from '../manager-stats';

@Component({
  selector: 'app-manager-public',
  imports: [RouterLink, DecimalPipe, PageHeader, Spinner],
  templateUrl: './manager-public.html',
})
export class ManagerPublic implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly managerStatsService = inject(ManagerStatsService);
  private readonly toastService = inject(ToastService);

  protected readonly managerId = this.route.snapshot.paramMap.get('managerId') ?? '';
  protected readonly loading = signal(true);
  protected readonly stats = signal<ManagerPublicStats | null>(null);

  ngOnInit(): void {
    this.managerStatsService
      .publicStats(this.managerId)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (stats) => this.stats.set(stats),
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
