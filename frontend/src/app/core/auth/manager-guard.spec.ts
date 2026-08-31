import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth';
import { managerGuard } from './manager-guard';

describe('managerGuard', () => {
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    authService = TestBed.inject(AuthService);
  });

  it('allows a TRAVEL_MANAGER through', () => {
    spyOn(authService, 'currentUser').and.returnValue({ username: 'manager', role: 'TRAVEL_MANAGER' });

    const result = TestBed.runInInjectionContext(() => managerGuard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('allows an ADMIN through', () => {
    spyOn(authService, 'currentUser').and.returnValue({ username: 'admin', role: 'ADMIN' });

    const result = TestBed.runInInjectionContext(() => managerGuard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('redirects a TRAVELER to /dashboard', () => {
    spyOn(authService, 'currentUser').and.returnValue({ username: 'traveler', role: 'TRAVELER' });

    const result = TestBed.runInInjectionContext(() => managerGuard({} as never, {} as never));

    const router = TestBed.inject(Router);
    expect(result?.toString()).toBe(router.createUrlTree(['/dashboard']).toString());
  });
});
