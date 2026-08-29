import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable } from 'rxjs';
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
    spyOn(authService, 'isAuthenticated').and.returnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/users' } as never),
    );

    const router = TestBed.inject(Router);
    const expectedTree = router.createUrlTree(['/login'], { queryParams: { returnUrl: '/users' } });
    expect(result?.toString()).toBe(expectedTree.toString());
  });

  it('allows navigation synchronously when authenticated and currentUser is already loaded', () => {
    spyOn(authService, 'isAuthenticated').and.returnValue(true);
    spyOn(authService, 'currentUser').and.returnValue({ username: 'admin', role: 'ADMIN' });

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/users' } as never),
    );

    expect(result).toBe(true);
  });

  // currentUser() reste a null apres reload tant que /me n'a pas ete rappele (voir auth-guard.ts).
  it('calls /me and allows navigation when authenticated but currentUser is not yet loaded', () => {
    spyOn(authService, 'isAuthenticated').and.returnValue(true);
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
    spyOn(authService, 'isAuthenticated').and.returnValue(true);
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
