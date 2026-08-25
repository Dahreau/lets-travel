import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { Travel } from '../../../core/models/travel';
import { ToastService } from '../../../core/notifications/toast';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { TravelerStatsService } from '../../travelers/traveler-stats';
import { SubscriptionsService } from '../subscriptions';
import { TravelsService } from '../travels';

// feat/traveler-frontend : composant DEDIE au parcours Traveler (recherche + abonnement),
// volontairement distinct de TravelList (table CRUD reservee Admin/Manager) - un Traveler ne
// doit ni editer ni supprimer un voyage.
@Component({
  selector: 'app-travel-browse',
  imports: [ReactiveFormsModule, RouterLink, PageHeader, Spinner],
  templateUrl: './travel-browse.html',
})
export class TravelBrowse implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly travelsService = inject(TravelsService);
  private readonly subscriptionsService = inject(SubscriptionsService);
  private readonly travelerStatsService = inject(TravelerStatsService);
  private readonly toastService = inject(ToastService);

  protected readonly searchForm = this.fb.nonNullable.group({ q: [''] });

  protected readonly loading = signal(true);
  protected readonly travels = signal<Travel[]>([]);
  protected readonly subscribingId = signal<string | null>(null);
  private readonly activeSubscriptionByTravelId = signal<Map<string, string>>(new Map());

  protected readonly isSubscribed = computed(() => {
    const map = this.activeSubscriptionByTravelId();
    return (travelId: string) => map.has(travelId);
  });

  ngOnInit(): void {
    this.loadSubscriptions();
    this.search();
  }

  private loadSubscriptions(): void {
    this.travelerStatsService.mySubscriptions().subscribe({
      next: (subscriptions) => {
        const map = new Map<string, string>();
        for (const subscription of subscriptions) {
          if (subscription.status === 'ACTIVE') {
            map.set(subscription.travelId, subscription.id);
          }
        }
        this.activeSubscriptionByTravelId.set(map);
      },
      error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
    });
  }

  protected search(): void {
    const query = this.searchForm.getRawValue().q.trim();
    const request$ = query ? this.travelsService.search(query) : this.travelsService.findAll();

    this.loading.set(true);
    request$.pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (travels) => this.travels.set(travels),
      error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
    });
  }

  protected subscribe(travel: Travel): void {
    this.subscribingId.set(travel.id);
    this.subscriptionsService
      .subscribe(travel.id)
      .pipe(finalize(() => this.subscribingId.set(null)))
      .subscribe({
        next: (subscription) => {
          this.activeSubscriptionByTravelId.update((map) => new Map(map).set(travel.id, subscription.id));
          this.toastService.success(`Abonné à "${travel.title}"`);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
