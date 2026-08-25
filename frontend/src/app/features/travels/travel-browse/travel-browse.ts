import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, finalize, of, switchMap } from 'rxjs';
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
  private readonly destroyRef = inject(DestroyRef);

  protected readonly searchForm = this.fb.nonNullable.group({ q: [''] });

  protected readonly loading = signal(true);
  protected readonly travels = signal<Travel[]>([]);
  protected readonly suggestions = signal<Travel[]>([]);
  protected readonly subscribingId = signal<string | null>(null);
  private readonly activeSubscriptionByTravelId = signal<Map<string, string>>(new Map());

  protected readonly isSubscribed = computed(() => {
    const map = this.activeSubscriptionByTravelId();
    return (travelId: string) => map.has(travelId);
  });

  ngOnInit(): void {
    this.loadSubscriptions();
    this.search();
    this.watchAutocomplete();
  }

  // feat/search-and-recommendations (backend) restait inutilisee cote UI - suggestions
  // live pendant la frappe, distinctes de la recherche "validee" par search() ci-dessous.
  private watchAutocomplete(): void {
    this.searchForm.controls.q.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((query) => (query.trim().length >= 2 ? this.travelsService.autocomplete(query.trim()) : of([]))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((suggestions) => this.suggestions.set(suggestions));
  }

  protected selectSuggestion(): void {
    this.suggestions.set([]);
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
