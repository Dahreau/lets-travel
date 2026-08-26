import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Register } from './register';

@Component({ template: '' })
class DummyComponent {}

describe('Register', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Register>>;
  let component: Register;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'dashboard', component: DummyComponent }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('starts with an invalid form', () => {
    expect(component['form'].invalid).toBe(true);
  });

  it('does not call the registration endpoints when submitting an empty form, and shows an error', () => {
    component['submit']();

    httpMock.expectNone('/api/users/register');
    expect(component['form'].touched).toBe(true);
    expect(component['errorMessage']()).not.toBeNull();
  });

  it('is valid without an address, and requires the address fields once "adresse renseignée" is toggled on', () => {
    component['form'].patchValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      username: 'ada',
      password: 'secretpw',
    });
    expect(component['form'].valid).toBe(true);

    component['toggleAddress']();
    expect(component['form'].valid).toBe(false);

    component['form'].controls.address.patchValue({
      street: '1 rue de la Paix',
      city: 'Paris',
      postalCode: '75000',
      country: 'FR',
    });
    expect(component['form'].valid).toBe(true);
  });

  it('registers the profile then the account, and clears the error on success', () => {
    component['form'].setValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      phone: '',
      username: 'ada',
      password: 'secretpw',
      address: { street: '', city: '', postalCode: '', country: '' },
    });

    component['submit']();

    const registerUserReq = httpMock.expectOne('/api/users/register');
    expect(registerUserReq.request.method).toBe('POST');
    expect(registerUserReq.request.body.address).toBeNull();
    registerUserReq.flush({ id: 'u1' });

    const registerAccountReq = httpMock.expectOne('/api/auth/register');
    expect(registerAccountReq.request.body).toEqual({ username: 'ada', password: 'secretpw', userId: 'u1' });
    registerAccountReq.flush({ token: fakeToken() });

    httpMock.expectOne('/api/auth/me').flush({ username: 'ada', role: 'TRAVELER' });

    expect(component['errorMessage']()).toBeNull();
  });
});

function fakeToken(): string {
  const exp = Math.floor(Date.now() / 1000) + 3600;
  const header = btoa(JSON.stringify({ alg: 'HS256' }));
  const body = btoa(JSON.stringify({ sub: 'ada', exp }));
  return `${header}.${body}.sig`;
}
