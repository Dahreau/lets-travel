import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, MeResponse } from '../models/auth';
import { isTokenExpired } from './jwt-util';

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
