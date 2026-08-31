import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, MeResponse, RegisterRequest } from '../models/auth';
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

  // Claim "userId" du JWT, non expose par MeResponse - lu ici directement. Null pour ADMIN
  // (pas de fiche User liee).
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

  // feat/traveler-frontend : 2e etape de l'inscription publique - meme reponse que login(),
  // connecte immediatement l'inscrit (voir AuthController.register cote backend).
  register(request: RegisterRequest): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/register', request)
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
