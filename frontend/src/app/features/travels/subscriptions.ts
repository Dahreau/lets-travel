import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Subscription } from '../../core/models/subscription';

@Injectable({ providedIn: 'root' })
export class SubscriptionsService {
  private readonly http = inject(HttpClient);

  // feat/traveler-frontend : le Traveler s'abonne lui-meme, cf. SubscriptionController.subscribe
  // (TRAVELER minimum, travelerId derive du JWT cote serveur, jamais du corps de la requete).
  subscribe(travelId: string): Observable<Subscription> {
    return this.http.post<Subscription>(`/api/travels/${travelId}/subscriptions`, {});
  }

  listSubscribers(travelId: string): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`/api/travels/${travelId}/subscriptions`);
  }

  unsubscribe(travelId: string, subscriptionId: string): Observable<void> {
    return this.http.delete<void>(`/api/travels/${travelId}/subscriptions/${subscriptionId}`);
  }
}
