import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
    spyOn(authService, 'currentUser').and.returnValue({ username: 'admin', role: 'ADMIN' });

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('aggregates counts and loads the manager/travel rankings and moderation queue', () => {
    httpMock.expectOne('/api/users').flush([{}]);
    httpMock.expectOne('/api/travels').flush([{}, {}]);
    httpMock.expectOne('/api/payments').flush([{}, {}, {}]);
    httpMock.expectOne('/api/payment-methods').flush([]);
    httpMock.expectOne('/api/travels/admin/manager-rankings').flush([
      { managerId: 'm1', travelCount: 2, travelerCount: 5, estimatedRevenue: 450, averageRating: 4.2, reportCount: 0, performanceScore: 46.5 },
    ]);
    httpMock.expectOne('/api/travels/admin/travel-rankings').flush([
      { travelId: 't1', title: 'Trip', managerId: 'm1', activeSubscriberCount: 3, revenue: 300, averageRating: 4.5 },
    ]);
    httpMock.expectOne('/api/travels/admin/monthly-revenue').flush([{ month: '2026-08', revenue: 500 }]);
    httpMock
      .expectOne('/api/reports')
      .flush([{ id: 'r1', travelId: 't1', reporterId: 'u1', reportedType: 'MANAGER', reportedId: 'm1', reason: 'No show', createdAt: '2026-01-01' }]);
    httpMock.expectOne('/api/users/m1').flush({
      id: 'm1',
      firstName: 'Jane',
      lastName: 'Doe',
      email: '',
      phone: null,
      role: 'TRAVEL_MANAGER',
      address: null,
      createdAt: '',
      updatedAt: '',
    });
    httpMock.expectOne('/api/users/u1').flush({
      id: 'u1',
      firstName: 'John',
      lastName: 'Smith',
      email: '',
      phone: null,
      role: 'TRAVELER',
      address: null,
      createdAt: '',
      updatedAt: '',
    });

    expect(component['stats']()).toEqual({ users: 1, travels: 2, payments: 3, paymentMethods: 0 });
    expect(component['managerRankings']().map((m) => m.managerId)).toEqual(['m1']);
    expect(component['travelRankings']().map((t) => t.travelId)).toEqual(['t1']);
    expect(component['monthlyRevenue']().map((m) => m.month)).toEqual(['2026-08']);
    expect(component['reports']().map((r) => r.id)).toEqual(['r1']);
    expect(component['reportedNames']().get('m1')).toBe('Jane Doe');
    expect(component['reporterNames']().get('u1')).toBe('John Smith');
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
    spyOn(authService, 'currentUser').and.returnValue({ username: 'manager', role: 'TRAVEL_MANAGER' });
    spyOn(authService, 'userId').and.returnValue('m1');

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('shows the manager stats and only the manager own travels, never calling the admin-only endpoints', () => {
    httpMock
      .expectOne('/api/travels/managers/me/stats')
      .flush({ travelCount: 2, travelerCount: 3, estimatedRevenue: 900, travels: [] });
    httpMock.expectOne('/api/travels').flush([TRAVEL('t1', 'm1'), TRAVEL('t2', 'other-manager')]);

    expect(component['managerStats']()).toEqual({
      travelCount: 2,
      travelerCount: 3,
      estimatedRevenue: 900,
      travels: [],
    });
    expect(component['myTravels']().map((t) => t.id)).toEqual(['t1']);

    httpMock.expectNone('/api/users');
    httpMock.expectNone('/api/payments');
    httpMock.expectNone('/api/payment-methods');
    httpMock.expectNone('/api/travels/admin/manager-rankings');
    httpMock.expectNone('/api/travels/admin/travel-rankings');
    httpMock.expectNone('/api/travels/admin/monthly-revenue');
    httpMock.expectNone('/api/reports');
  });

  it('exposes per-travel subscriber count and average rating keyed by travel id', () => {
    httpMock.expectOne('/api/travels/managers/me/stats').flush({
      travelCount: 1,
      travelerCount: 3,
      estimatedRevenue: 300,
      travels: [{ travelId: 't1', title: 'Trip', subscriberCount: 3, averageRating: 4.5, feedbackCount: 2 }],
    });
    httpMock.expectOne('/api/travels').flush([TRAVEL('t1', 'm1')]);

    expect(component['travelStatsById']().get('t1')?.subscriberCount).toBe(3);
    expect(component['travelStatsById']().get('t1')?.averageRating).toBe(4.5);
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
    spyOn(authService, 'currentUser').and.returnValue({ username: 'traveler1', role: 'TRAVELER' });

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Dashboard);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('shows personal stats, recommendations, subscription history and payment methods, never calling the admin/manager-only endpoints', () => {
    httpMock
      .expectOne('/api/travels/travelers/me/stats')
      .flush({ participationCount: 4, feedbackCount: 2, reportCount: 0, cancellationCount: 1 });
    httpMock.expectOne('/api/travels/recommendations').flush([TRAVEL('t1', 'm1')]);
    httpMock
      .expectOne('/api/travels/travelers/me/subscriptions')
      .flush([{ id: 's1', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '2026-01-01', cancelledAt: null }]);
    // GET /api/payment-methods deja scope au caller cote backend (PaymentMethodService.findAll) :
    // un Traveler ne recoit ici que SES propres moyens de paiement, pas la liste globale.
    httpMock.expectOne('/api/payment-methods').flush([
      {
        id: 'pm1',
        ownerId: 'u1',
        provider: 'STRIPE',
        type: 'CARD',
        brand: 'visa',
        last4: '4242',
        isDefault: true,
        createdAt: '2026-01-01',
      },
    ]);
    httpMock
      .expectOne('/api/travels/travelers/me/feedbacks')
      .flush([{ id: 'f1', travelId: 't1', travelerId: 'u1', rating: 5, comment: 'Super', createdAt: '2026-01-01' }]);
    httpMock
      .expectOne('/api/travels/travelers/me/reports')
      .flush([
        {
          id: 'r1',
          travelId: 't1',
          reporterId: 'u1',
          reportedType: 'TRAVELER',
          reportedId: 'u2',
          reason: 'Comportement inapproprié',
          createdAt: '2026-01-01',
        },
      ]);

    expect(component['travelerStats']()).toEqual({
      participationCount: 4,
      feedbackCount: 2,
      reportCount: 0,
      cancellationCount: 1,
    });
    expect(component['recommendations']().map((t) => t.id)).toEqual(['t1']);
    expect(component['mySubscriptions']().map((s) => s.id)).toEqual(['s1']);
    expect(component['myPaymentMethods']().map((m) => m.id)).toEqual(['pm1']);
    expect(component['myFeedbacks']().map((f) => f.id)).toEqual(['f1']);
    expect(component['myReports']().map((r) => r.id)).toEqual(['r1']);

    httpMock.expectNone('/api/users');
    httpMock.expectNone('/api/payments');
    httpMock.expectNone('/api/travels/admin/manager-rankings');
    httpMock.expectNone('/api/travels/admin/travel-rankings');
    httpMock.expectNone('/api/travels/admin/monthly-revenue');
    httpMock.expectNone('/api/reports');
  });
});
