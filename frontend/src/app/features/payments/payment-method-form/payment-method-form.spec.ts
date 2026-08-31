import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../../core/auth/auth';
import { PaymentMethodForm } from './payment-method-form';

// feat/traveler-frontend : couvre le fix "GET /api/users est Admin-only" - un Traveler ne
// doit jamais declencher cet appel (voir project_lets-travel.md).
describe('PaymentMethodForm (as TRAVELER)', () => {
  let httpMock: HttpTestingController;

  async function setup(paramId: string | null): Promise<ReturnType<typeof TestBed.createComponent<PaymentMethodForm>>> {
    await TestBed.configureTestingModule({
      imports: [PaymentMethodForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap(paramId ? { id: paramId } : {}) } },
        },
      ],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    spyOn(authService, 'currentUser').and.returnValue({ username: 'traveler1', role: 'TRAVELER' });
    spyOn(authService, 'userId').and.returnValue('u1');

    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PaymentMethodForm);
    return fixture;
  }

  afterEach(() => httpMock.verify());

  it('sets ownerId to the connected traveler and never calls GET /api/users when creating', async () => {
    const fixture = await setup(null);
    fixture.detectChanges();

    expect(fixture.componentInstance['form'].controls.ownerId.value).toBe('u1');
    expect(fixture.componentInstance['loading']()).toBe(false);
    httpMock.expectNone('/api/users');
  });

  it('fetches the method directly when editing, without calling GET /api/users', async () => {
    const fixture = await setup('pm1');
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/payment-methods/pm1');
    req.flush({
      id: 'pm1',
      ownerId: 'u1',
      provider: 'STRIPE',
      type: 'CARD',
      brand: 'Visa',
      last4: '4242',
      isDefault: true,
      createdAt: '',
    });

    expect(fixture.componentInstance['form'].controls.ownerId.value).toBe('u1');
    httpMock.expectNone('/api/users');
  });
});

describe('PaymentMethodForm (as ADMIN)', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentMethodForm],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({}) } } },
      ],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    spyOn(authService, 'currentUser').and.returnValue({ username: 'admin', role: 'ADMIN' });

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('still fetches the user list to populate the owner picker when creating', () => {
    const fixture = TestBed.createComponent(PaymentMethodForm);
    fixture.detectChanges();

    const req = httpMock.expectOne('/api/users');
    req.flush([{ id: 'u2', firstName: 'Ada', lastName: 'Lovelace' }]);

    expect(fixture.componentInstance['users']()).toHaveSize(1);
  });
});
