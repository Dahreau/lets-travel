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

const USER = (id: string) => ({
  id,
  firstName: 'Co',
  lastName: id,
  email: '',
  phone: null,
  role: 'TRAVELER',
  address: null,
  createdAt: '',
  updatedAt: '',
});

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

  // co-travelers n'est chargé que si le traveler a participé (SubscriptionService.coTravelerIds,
  // 403 sinon) : coTravelerIds ne s'applique donc qu'aux appels flushInit avec une subscription.
  function flushInit(subscriptions: unknown[] = [], paymentMethods: unknown[] = [], coTravelerIds: string[] = []): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/t1').flush(TRAVEL);
    httpMock.expectOne('/api/travels/travelers/me/subscriptions').flush(subscriptions);
    httpMock.expectOne('/api/payment-methods').flush(paymentMethods);

    if ((subscriptions as { travelId: string }[]).some((s) => s.travelId === 't1')) {
      httpMock.expectOne('/api/travels/t1/subscriptions/co-travelers').flush(coTravelerIds);
      coTravelerIds.forEach((id) => httpMock.expectOne(`/api/users/${id}`).flush(USER(id)));
    }
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

  it('loads and resolves co-travelers once the traveler has participated', () => {
    flushInit(
      [{ id: 's1', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '', cancelledAt: null }],
      [],
      ['u2'],
    );

    expect(component['coTravelers']().map((u) => u.id)).toEqual(['u2']);
  });

  it('submits a report against the manager by default', () => {
    flushInit([{ id: 's1', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '', cancelledAt: null }]);

    component['reportForm'].patchValue({ reason: 'No show' });
    component['submitReport']();

    const req = httpMock.expectOne('/api/travels/t1/reports');
    expect(req.request.body).toEqual({ reportedType: 'MANAGER', reportedId: 'm1', reason: 'No show' });
    req.flush(null);
  });

  it('submits a report against a selected co-traveler', () => {
    flushInit(
      [{ id: 's1', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '', cancelledAt: null }],
      [],
      ['u2'],
    );

    component['reportForm'].patchValue({ target: 'u2', reason: 'Rude behaviour' });
    component['submitReport']();

    const req = httpMock.expectOne('/api/travels/t1/reports');
    expect(req.request.body).toEqual({ reportedType: 'TRAVELER', reportedId: 'u2', reason: 'Rude behaviour' });
    req.flush(null);
  });
});
