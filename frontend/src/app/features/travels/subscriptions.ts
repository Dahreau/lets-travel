import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Subscription } from '../../core/models/subscription';

@Injectable({ providedIn: 'root' })
export class SubscriptionsService {
  private readonly http = inject(HttpClient);

  listSubscribers(travelId: string): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`/api/travels/${travelId}/subscriptions`);
  }

  unsubscribe(travelId: string, subscriptionId: string): Observable<void> {
    return this.http.delete<void>(`/api/travels/${travelId}/subscriptions/${subscriptionId}`);
  }
}
