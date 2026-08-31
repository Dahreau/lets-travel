import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { ManagerTravelDetail } from './manager-travel-detail';

const TRAVEL = {
  id: 't1',
  title: 'Road trip',
  managerId: 'm1',
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
};

describe('ManagerTravelDetail', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<ManagerTravelDetail>>;
  let component: ManagerTravelDetail;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ManagerTravelDetail],
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
    fixture = TestBed.createComponent(ManagerTravelDetail);
    component = fixture.componentInstance;
  });

  afterEach(() => httpMock.verify());

  it('loads the travel, resolves subscriber profiles, and loads feedback', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/travels/t1').flush(TRAVEL);
    httpMock.expectOne('/api/travels/t1/subscriptions').flush([
      { id: 's1', travelId: 't1', travelerId: 'trav-1', status: 'ACTIVE', subscribedAt: '2026-01-01T00:00:00Z', cancelledAt: null },
    ]);
    httpMock.expectOne('/api/travels/t1/feedbacks').flush([
      { id: 'f1', travelId: 't1', travelerId: 'trav-1', rating: 4, comment: 'Nice', createdAt: '2026-01-06T00:00:00Z' },
    ]);
    httpMock
      .expectOne('/api/users/trav-1')
      .flush({ id: 'trav-1', firstName: 'Ada', lastName: 'T', email: 'ada@t.com', phone: null, role: 'TRAVELER', address: null, createdAt: '', updatedAt: '' });

    expect(component['travel']()?.title).toBe('Road trip');
    expect(component['subscribers']()).toHaveSize(1);
    expect(component['subscribers']()[0].traveler?.firstName).toBe('Ada');
    expect(component['feedbacks']()).toHaveSize(1);
    expect(component['loading']()).toBe(false);
  });

  it('does not hang when the travel has no subscribers', () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/travels/t1').flush(TRAVEL);
    httpMock.expectOne('/api/travels/t1/subscriptions').flush([]);
    httpMock.expectOne('/api/travels/t1/feedbacks').flush([]);

    expect(component['subscribers']()).toEqual([]);
    expect(component['loading']()).toBe(false);
  });

  it('marks a subscription as CANCELLED locally after a successful unsubscribe', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/t1').flush(TRAVEL);
    httpMock.expectOne('/api/travels/t1/subscriptions').flush([
      { id: 's1', travelId: 't1', travelerId: 'trav-1', status: 'ACTIVE', subscribedAt: '2026-01-01T00:00:00Z', cancelledAt: null },
    ]);
    httpMock.expectOne('/api/travels/t1/feedbacks').flush([]);
    httpMock
      .expectOne('/api/users/trav-1')
      .flush({ id: 'trav-1', firstName: 'Ada', lastName: 'T', email: 'ada@t.com', phone: null, role: 'TRAVELER', address: null, createdAt: '', updatedAt: '' });

    component['unsubscribeTarget'].set(component['subscribers']()[0].subscription);
    component['confirmUnsubscribe']();
    httpMock.expectOne('/api/travels/t1/subscriptions/s1').flush(null);

    expect(component['subscribers']()[0].subscription.status).toBe('CANCELLED');
  });
});
