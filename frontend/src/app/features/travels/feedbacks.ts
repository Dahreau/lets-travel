import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Feedback, FeedbackRequest } from '../../core/models/feedback';

@Injectable({ providedIn: 'root' })
export class FeedbacksService {
  private readonly http = inject(HttpClient);

  listForTravel(travelId: string): Observable<Feedback[]> {
    return this.http.get<Feedback[]>(`/api/travels/${travelId}/feedbacks`);
  }

  // feat/traveler-frontend : reserve au traveler ayant ete abonne au travel, apres sa fin
  // (verifie cote FeedbackService, pas ici).
  submit(travelId: string, request: FeedbackRequest): Observable<Feedback> {
    return this.http.post<Feedback>(`/api/travels/${travelId}/feedbacks`, request);
  }
}
