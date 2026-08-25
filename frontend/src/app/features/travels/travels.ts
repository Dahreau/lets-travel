import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Travel, TravelRequest } from '../../core/models/travel';

@Injectable({ providedIn: 'root' })
export class TravelsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/travels';

  findAll(): Observable<Travel[]> {
    return this.http.get<Travel[]>(this.baseUrl);
  }

  findById(id: string): Observable<Travel> {
    return this.http.get<Travel>(`${this.baseUrl}/${id}`);
  }

  // feat/traveler-frontend : recherche Elasticsearch (feat/search-and-recommendations), pas
  // encore consommee cote UI jusqu'ici - voir TravelController.search/autocomplete/recommendations.
  search(query: string): Observable<Travel[]> {
    return this.http.get<Travel[]>(`${this.baseUrl}/search`, { params: { q: query } });
  }

  autocomplete(query: string): Observable<Travel[]> {
    return this.http.get<Travel[]>(`${this.baseUrl}/autocomplete`, { params: { q: query } });
  }

  recommendations(): Observable<Travel[]> {
    return this.http.get<Travel[]>(`${this.baseUrl}/recommendations`);
  }

  create(request: TravelRequest): Observable<Travel> {
    return this.http.post<Travel>(this.baseUrl, request);
  }

  update(id: string, request: TravelRequest): Observable<Travel> {
    return this.http.put<Travel>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
