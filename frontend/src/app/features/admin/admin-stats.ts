import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AdminManagerRanking, AdminTravelRanking } from '../../core/models/admin-stats';

// feat/admin-dashboard-overview : classements globaux consommes par le dashboard Admin.
@Injectable({ providedIn: 'root' })
export class AdminStatsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/travels/admin';

  managerRankings(): Observable<AdminManagerRanking[]> {
    return this.http.get<AdminManagerRanking[]>(`${this.baseUrl}/manager-rankings`);
  }

  travelRankings(): Observable<AdminTravelRanking[]> {
    return this.http.get<AdminTravelRanking[]>(`${this.baseUrl}/travel-rankings`);
  }
}
