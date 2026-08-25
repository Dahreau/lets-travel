import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../../core/auth/auth';
import { Dashboard } from './dashboard';

const TRAVEL = (id: string, managerId: string) => ({
  id,
  title: `Trip ${id}`,
  managerId,
  startDate: '2026-01-01',
  endDate: '2026-01-05',
  durationDays: 5,
  status: 'PLANNED',
  price: 500,
  currency: 'EUR',
  destinations: [],
  transportations: [],
  createdAt: '',
  updatedAt: '',
});

describe('Dashboard (as ADMIN)', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Dashboard>>;
  let component: Dashboard;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'admin', role: 'ADMIN' });

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('aggregates counts from all four admin-only endpoints', () => {
    httpMock.expectOne('/api/users').flush([{}]);
    httpMock.expectOne('/api/travels').flush([{}, {}]);
    httpMock.expectOne('/api/payments').flush([{}, {}, {}]);
    httpMock.expectOne('/api/payment-methods').flush([]);

    expect(component['stats']()).toEqual({ users: 1, travels: 2, payments: 3, paymentMethods: 0 });
    expect(component['loading']()).toBe(false);
  });
});

describe('Dashboard (as TRAVEL_MANAGER)', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Dashboard>>;
  let component: Dashboard;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'manager', role: 'TRAVEL_MANAGER' });
    vi.spyOn(authService, 'userId').mockReturnValue('m1');

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('shows the manager stats and only the manager own travels, never calling the admin-only endpoints', () => {
    httpMock.expectOne('/api/travels/managers/me/stats').flush({ travelCount: 2, travelerCount: 3, estimatedRevenue: 900 });
    httpMock.expectOne('/api/travels').flush([TRAVEL('t1', 'm1'), TRAVEL('t2', 'other-manager')]);

    expect(component['managerStats']()).toEqual({ travelCount: 2, travelerCount: 3, estimatedRevenue: 900 });
    expect(component['myTravels']().map((t) => t.id)).toEqual(['t1']);

    httpMock.expectNone('/api/users');
    httpMock.expectNone('/api/payments');
    httpMock.expectNone('/api/payment-methods');
  });
});

describe('Dashboard (as TRAVELER)', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Dashboard>>;
  let component: Dashboard;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    const authService = TestBed.inject(AuthService);
    vi.spyOn(authService, 'currentUser').mockReturnValue({ username: 'traveler1', role: 'TRAVELER' });

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('shows personal stats, recommendations and subscription history, never calling the admin-only endpoints', () => {
    httpMock
      .expectOne('/api/travels/travelers/me/stats')
      .flush({ participationCount: 4, feedbackCount: 2, reportCount: 0, cancellationCount: 1 });
    httpMock.expectOne('/api/travels/recommendations').flush([TRAVEL('t1', 'm1')]);
    httpMock
      .expectOne('/api/travels/travelers/me/subscriptions')
      .flush([{ id: 's1', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '2026-01-01', cancelledAt: null }]);

    expect(component['travelerStats']()).toEqual({
      participationCount: 4,
      feedbackCount: 2,
      reportCount: 0,
      cancellationCount: 1,
    });
    expect(component['recommendations']().map((t) => t.id)).toEqual(['t1']);
    expect(component['mySubscriptions']().map((s) => s.id)).toEqual(['s1']);

    httpMock.expectNone('/api/users');
    httpMock.expectNone('/api/payments');
    httpMock.expectNone('/api/payment-methods');
  });
});
