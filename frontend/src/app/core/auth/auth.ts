import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, MeResponse } from '../models/auth';
import { decodeJwtPayload, isTokenExpired } from './jwt-util';

const TOKEN_KEY = 'travel-plan.admin.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly currentUserSignal = signal<MeResponse | null>(null);
  readonly currentUser = this.currentUserSignal.asReadonly();
  readonly username = computed(() => this.currentUserSignal()?.username ?? null);

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    const token = this.token;
    return !!token && !isTokenExpired(token);
  }

  // Le JWT porte deja un claim "userId" (voir auth-service JwtService), meme si MeResponse
  // ne l'expose pas : on le lit directement ici plutot que d'agrandir l'API pour si peu
  // (feat/manager-frontend en a besoin pour reconnaitre "mes" voyages/mon propre profil).
  // Null pour le compte ADMIN par defaut, qui n'a pas de fiche User liee.
  userId(): string | null {
    const token = this.token;
    if (!token) {
      return null;
    }
    const payload = decodeJwtPayload(token);
    const userId = payload?.['userId'];
    return typeof userId === 'string' ? userId : null;
  }

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/login', credentials)
      .pipe(tap((response) => localStorage.setItem(TOKEN_KEY, response.token)));
  }

  me(): Observable<MeResponse> {
    return this.http
      .get<MeResponse>('/api/auth/me')
      .pipe(tap((response) => this.currentUserSignal.set(response)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.currentUserSignal.set(null);
  }
}
