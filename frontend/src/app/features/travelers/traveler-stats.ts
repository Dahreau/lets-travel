import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Subscription } from '../../core/models/subscription';
import { TravelerStats } from '../../core/models/traveler-stats';

// feat/traveler-frontend : miroir cote Angular de TravelerStatsController (meme decoupage
// que ManagerStatsController/ManagerStatsService cote manager).
@Injectable({ providedIn: 'root' })
export class TravelerStatsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/travels/travelers';

  myStats(): Observable<TravelerStats> {
    return this.http.get<TravelerStats>(`${this.baseUrl}/me/stats`);
  }

  mySubscriptions(): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.baseUrl}/me/subscriptions`);
  }
}
