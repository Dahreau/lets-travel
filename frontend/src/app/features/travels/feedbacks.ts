import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Feedback } from '../../core/models/feedback';

@Injectable({ providedIn: 'root' })
export class FeedbacksService {
  private readonly http = inject(HttpClient);

  listForTravel(travelId: string): Observable<Feedback[]> {
    return this.http.get<Feedback[]>(`/api/travels/${travelId}/feedbacks`);
  }
}
