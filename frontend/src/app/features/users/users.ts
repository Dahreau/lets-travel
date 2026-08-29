import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RegistrationResponse, User, UserRegistrationRequest, UserRequest } from '../../core/models/user';

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/users';

  findAll(): Observable<User[]> {
    return this.http.get<User[]>(this.baseUrl);
  }

  findById(id: string): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/${id}`);
  }

  create(request: UserRequest): Observable<User> {
    return this.http.post<User>(this.baseUrl, request);
  }

  register(request: UserRegistrationRequest): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${this.baseUrl}/register`, request);
  }

  update(id: string, request: UserRequest): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  // fix/audit-gaps (troubleshooting.md #41) : droit d'acces/portabilite RGPD - l'appelant
  // recupere SON PROPRE profil (GET /api/users/me cote backend), sans jamais fournir d'id.
  me(): Observable<User> {
    return this.http.get<User>(`${this.baseUrl}/me`);
  }

  // voir troubleshooting.md #41 - droit a l'effacement RGPD, self-service (cf. UserService.deleteMe).
  deleteMe(): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/me`);
  }
}
