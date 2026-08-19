import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('is not authenticated with no token', () => {
    expect(service.isAuthenticated()).toBe(false);
  });

  it('stores the token on login and becomes authenticated', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    const token = fakeToken(futureExp);

    service.login({ username: 'admin', password: 'secret' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush({ token });

    expect(service.token).toBe(token);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('populates currentUser on me()', () => {
    service.me().subscribe();
    httpMock.expectOne('/api/auth/me').flush({ username: 'admin', role: 'ADMIN' });

    expect(service.currentUser()).toEqual({ username: 'admin', role: 'ADMIN' });
    expect(service.username()).toBe('admin');
  });

  it('clears session on logout', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600;
    service.login({ username: 'admin', password: 'secret' }).subscribe();
    httpMock.expectOne('/api/auth/login').flush({ token: fakeToken(futureExp) });

    service.logout();

    expect(service.token).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });
});

function fakeToken(exp: number): string {
  const header = btoa(JSON.stringify({ alg: 'HS256' }));
  const body = btoa(JSON.stringify({ sub: 'admin', exp }));
  return `${header}.${body}.sig`;
}
