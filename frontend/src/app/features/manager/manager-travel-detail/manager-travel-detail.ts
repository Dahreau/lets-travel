import { SlicePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, of, switchMap } from 'rxjs';
import { extractErrorMessage } from '../../../core/http/api-error';
import { ToastService } from '../../../core/notifications/toast';
import { Badge } from '../../../shared/ui/badge';
import { ConfirmDialog } from '../../../shared/ui/confirm-dialog';
import { PageHeader } from '../../../shared/ui/page-header';
import { Spinner } from '../../../shared/ui/spinner';
import { Feedback } from '../../../core/models/feedback';
import { Subscription } from '../../../core/models/subscription';
import { Travel } from '../../../core/models/travel';
import { User } from '../../../core/models/user';
import { FeedbacksService } from '../../travels/feedbacks';
import { SubscriptionsService } from '../../travels/subscriptions';
import { TravelsService } from '../../travels/travels';
import { UsersService } from '../../users/users';

interface SubscriberRow {
  subscription: Subscription;
  // null si le profil traveler n'a pas pu être résolu (ex. compte supprimé depuis) : on affiche
  // quand même la ligne, avec l'id brut, plutôt que de faire échouer toute la page pour ça.
  traveler: User | null;
}

@Component({
  selector: 'app-manager-travel-detail',
  imports: [RouterLink, SlicePipe, Badge, ConfirmDialog, PageHeader, Spinner],
  templateUrl: './manager-travel-detail.html',
})
export class ManagerTravelDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly travelsService = inject(TravelsService);
  private readonly subscriptionsService = inject(SubscriptionsService);
  private readonly feedbacksService = inject(FeedbacksService);
  private readonly usersService = inject(UsersService);
  private readonly toastService = inject(ToastService);

  protected readonly travelId = this.route.snapshot.paramMap.get('id') ?? '';
  protected readonly loading = signal(true);
  protected readonly travel = signal<Travel | null>(null);
  protected readonly subscribers = signal<SubscriberRow[]>([]);
  protected readonly feedbacks = signal<Feedback[]>([]);
  protected readonly unsubscribeTarget = signal<Subscription | null>(null);
  protected readonly unsubscribing = signal(false);

  ngOnInit(): void {
    this.loading.set(true);
    forkJoin({
      travel: this.travelsService.findById(this.travelId),
      subscriptions: this.subscriptionsService.listSubscribers(this.travelId),
      feedbacks: this.feedbacksService.listForTravel(this.travelId),
    })
      .pipe(
        switchMap(({ travel, subscriptions, feedbacks }) =>
          this.resolveTravelers(subscriptions).pipe(
            map((subscribers) => ({ travel, subscribers, feedbacks })),
          ),
        ),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: ({ travel, subscribers, feedbacks }) => {
          this.travel.set(travel);
          this.subscribers.set(subscribers);
          this.feedbacks.set(feedbacks);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }

  // forkJoin d'un tableau vide ne notifie jamais (complète sans valeur) : le garde-fou
  // ci-dessous évite qu'un voyage sans abonné bloque tout l'écran indéfiniment.
  private resolveTravelers(subscriptions: Subscription[]) {
    if (subscriptions.length === 0) {
      return of<SubscriberRow[]>([]);
    }
    return forkJoin(
      subscriptions.map((subscription) =>
        this.usersService.findById(subscription.travelerId).pipe(catchError(() => of(null))),
      ),
    ).pipe(
      map((travelers) => subscriptions.map((subscription, i) => ({ subscription, traveler: travelers[i] }))),
    );
  }

  protected confirmUnsubscribe(): void {
    const target = this.unsubscribeTarget();
    if (!target) {
      return;
    }

    this.unsubscribing.set(true);
    this.subscriptionsService
      .unsubscribe(this.travelId, target.id)
      .pipe(finalize(() => this.unsubscribing.set(false)))
      .subscribe({
        next: () => {
          this.subscribers.update((rows) =>
            rows.map((row) =>
              row.subscription.id === target.id
                ? { ...row, subscription: { ...row.subscription, status: 'CANCELLED' as const } }
                : row,
            ),
          );
          this.toastService.success('Voyageur désabonné');
          this.unsubscribeTarget.set(null);
        },
        error: (error: unknown) => this.toastService.error(extractErrorMessage(error)),
      });
  }
}
