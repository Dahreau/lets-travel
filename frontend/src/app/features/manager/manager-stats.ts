import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ManagerPublicStats, ManagerStats } from '../../core/models/manager-stats';

@Injectable({ providedIn: 'root' })
export class ManagerStatsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/travels/managers';

  myStats(): Observable<ManagerStats> {
    return this.http.get<ManagerStats>(`${this.baseUrl}/me/stats`);
  }

  publicStats(managerId: string): Observable<ManagerPublicStats> {
    return this.http.get<ManagerPublicStats>(`${this.baseUrl}/${managerId}/public-stats`);
  }
}
