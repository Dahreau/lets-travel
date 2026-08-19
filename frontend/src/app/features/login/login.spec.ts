import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../../core/auth/auth';
import { Login } from './login';

@Component({ template: '' })
class DummyComponent {}

describe('Login', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Login>>;
  let component: Login;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'dashboard', component: DummyComponent }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('starts with an invalid form', () => {
    expect(component['form'].invalid).toBe(true);
  });

  it('does not call the auth service when submitting an empty form', () => {
    const authService = TestBed.inject(AuthService);
    const loginSpy = vi.spyOn(authService, 'login');

    component['submit']();

    expect(loginSpy).not.toHaveBeenCalled();
    expect(component['form'].touched).toBe(true);
  });

  it('logs in and fetches the current user when the form is valid', () => {
    component['form'].setValue({ username: 'admin', password: 'secret' });

    component['submit']();

    httpMock.expectOne('/api/auth/login').flush({ token: fakeToken() });
    httpMock.expectOne('/api/auth/me').flush({ username: 'admin', role: 'ADMIN' });

    expect(component['errorMessage']()).toBeNull();
  });
});

function fakeToken(): string {
  const exp = Math.floor(Date.now() / 1000) + 3600;
  const header = btoa(JSON.stringify({ alg: 'HS256' }));
  const body = btoa(JSON.stringify({ sub: 'admin', exp }));
  return `${header}.${body}.sig`;
}
