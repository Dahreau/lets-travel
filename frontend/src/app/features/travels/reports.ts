import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ReportRequest } from '../../core/models/report';

// feat/traveler-frontend : signalement d'un manager ou d'un autre traveler, dans le contexte
// d'un travel (voir ReportController/ReportService - GET /api/reports reste Admin-only, pas
// expose ici).
@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  submit(travelId: string, request: ReportRequest): Observable<void> {
    return this.http.post<void>(`/api/travels/${travelId}/reports`, request);
  }
}
