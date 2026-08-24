import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from './auth';
import { authGuard } from './auth-guard';

describe('authGuard', () => {
  let authService: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('redirects to /login with a returnUrl when not authenticated', () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/users' } as never),
    );

    const router = TestBed.inject(Router);
    const expectedTree = router.createUrlTree(['/login'], { queryParams: { returnUrl: '/users' } });
    expect(result?.toString()).toBe(expectedTree.toString());
  });

  it('allows navigation synchronously when authenticated and currentUser is already loaded', () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(true);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'admin', role: 'ADMIN' });

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/users' } as never),
    );

    expect(result).toBe(true);
  });

  // currentUser() reste à null après un rechargement de page tant que /me n'a pas été rappelé
  // (voir commentaire dans auth-guard.ts) : le guard doit le repeupler lui-même avant de laisser
  // passer la navigation, sinon le filtrage par rôle (nav manager, managerGuard) casserait.
  it('calls /me and allows navigation when authenticated but currentUser is not yet loaded', () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(true);
    let resolved: unknown;

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/dashboard' } as never),
    );
    (result as Observable<unknown>).subscribe((value) => (resolved = value));
    httpMock.expectOne('/api/auth/me').flush({ username: 'manager', role: 'TRAVEL_MANAGER' });

    expect(resolved).toBe(true);
    expect(authService.currentUser()).toEqual({ username: 'manager', role: 'TRAVEL_MANAGER' });
  });

  it('redirects to /login when authenticated but /me fails', () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(true);
    const router = TestBed.inject(Router);
    let resolved: unknown;

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/dashboard' } as never),
    );
    (result as Observable<unknown>).subscribe((value) => (resolved = value));
    httpMock.expectOne('/api/auth/me').flush('boom', { status: 500, statusText: 'Server Error' });

    const expectedTree = router.createUrlTree(['/login'], { queryParams: { returnUrl: '/dashboard' } });
    expect((resolved as { toString(): string }).toString()).toBe(expectedTree.toString());
  });
});
