import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../../../core/auth/auth';
import { TravelDetail } from './travel-detail';

const TRAVEL = {
  id: 't1',
  title: 'Trip t1',
  managerId: 'm1',
  startDate: '2020-01-01',
  endDate: '2020-01-05',
  durationDays: 5,
  status: 'COMPLETED',
  price: 500,
  currency: 'EUR',
  destinations: [],
  transportations: [],
  createdAt: '',
  updatedAt: '',
};

describe('TravelDetail', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<TravelDetail>>;
  let component: TravelDetail;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TravelDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 't1' }) } },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelDetail);
    component = fixture.componentInstance;
  });

  afterEach(() => httpMock.verify());

  function flushInit(subscriptions: unknown[] = [], paymentMethods: unknown[] = []): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/t1').flush(TRAVEL);
    httpMock.expectOne('/api/travels/travelers/me/subscriptions').flush(subscriptions);
    httpMock.expectOne('/api/payment-methods').flush(paymentMethods);
  }

  it('marks the traveler as having participated when a past subscription exists', () => {
    flushInit([
      { id: 's1', travelId: 't1', travelerId: 'u1', status: 'CANCELLED', subscribedAt: '', cancelledAt: '' },
    ]);

    expect(component['hasParticipated']()).toBe(true);
    expect(component['activeSubscription']()).toBeNull();
    expect(component['paymentForm'].getRawValue().amount).toBe(500);
    expect(component['paymentForm'].getRawValue().currency).toBe('EUR');
  });

  it('subscribing posts and stores the new active subscription', () => {
    flushInit([]);

    component['subscribe']();
    const req = httpMock.expectOne('/api/travels/t1/subscriptions');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 's2', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '', cancelledAt: null });

    expect(component['activeSubscription']()?.id).toBe('s2');
    expect(component['hasParticipated']()).toBe(true);
  });

  it('unsubscribing deletes using the active subscription id and clears it', () => {
    flushInit([{ id: 's1', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '', cancelledAt: null }]);

    component['unsubscribe']();
    const req = httpMock.expectOne('/api/travels/t1/subscriptions/s1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(component['activeSubscription']()).toBeNull();
  });

  it('submits a payment for the connected traveler', () => {
    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'userId').mockReturnValue('u1');
    flushInit([], [{ id: 'pm1', ownerId: 'u1', provider: 'STRIPE', type: 'CARD', brand: 'Visa', last4: '4242', isDefault: true, createdAt: '' }]);

    component['paymentForm'].patchValue({ paymentMethodId: 'pm1' });
    component['submitPayment']();

    const req = httpMock.expectOne('/api/payments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      travelId: 't1',
      ownerId: 'u1',
      paymentMethodId: 'pm1',
      amount: 500,
      currency: 'EUR',
    });
    req.flush({});
  });

  it('isPastEndDate distinguishes past and future dates', () => {
    expect(component['isPastEndDate']('2020-01-01')).toBe(true);
    expect(component['isPastEndDate']('2999-01-01')).toBe(false);
  });
});
