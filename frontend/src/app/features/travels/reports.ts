import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ReportRequest, ReportResponse } from '../../core/models/report';

// feat/traveler-frontend : signalement d'un manager ou d'un autre traveler, dans le contexte
// d'un travel. listAll (feat/admin-dashboard-overview) est reserve a l'Admin cote backend.
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  submit(travelId: string, request: ReportRequest): Observable<void> {
    return this.http.post<void>(`/api/travels/${travelId}/reports`, request);
  }

  listAll(): Observable<ReportResponse[]> {
    return this.http.get<ReportResponse[]>('/api/reports');
  }
}
