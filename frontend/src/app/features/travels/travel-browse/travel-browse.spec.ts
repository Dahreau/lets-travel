import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Travel } from '../../../core/models/travel';
import { TravelBrowse } from './travel-browse';

const TRAVEL = (id: string): Travel => ({
  id,
  title: `Trip ${id}`,
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
});

describe('TravelBrowse', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<TravelBrowse>>;
  let component: TravelBrowse;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TravelBrowse],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TravelBrowse);
    component = fixture.componentInstance;
  });

  afterEach(() => httpMock.verify());

  it('loads all travels and marks the ones the traveler is already subscribed to', () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/travels/travelers/me/subscriptions')
      .flush([{ id: 's1', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '', cancelledAt: null }]);
    httpMock.expectOne('/api/travels').flush([TRAVEL('t1'), TRAVEL('t2')]);

    expect(component['travels']().map((t) => t.id)).toEqual(['t1', 't2']);
    expect(component['isSubscribed']()('t1')).toBe(true);
    expect(component['isSubscribed']()('t2')).toBe(false);
  });

  it('subscribing to a travel marks it as subscribed', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/travelers/me/subscriptions').flush([]);
    httpMock.expectOne('/api/travels').flush([TRAVEL('t1')]);

    component['subscribe'](TRAVEL('t1'));

    const req = httpMock.expectOne('/api/travels/t1/subscriptions');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 's2', travelId: 't1', travelerId: 'u1', status: 'ACTIVE', subscribedAt: '', cancelledAt: null });

    expect(component['isSubscribed']()('t1')).toBe(true);
  });

  it('searching queries the search endpoint with the typed query', () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/travelers/me/subscriptions').flush([]);
    httpMock.expectOne('/api/travels').flush([]);

    component['searchForm'].setValue({ q: 'paris' });
    component['search']();

    const req = httpMock.expectOne((r) => r.url === '/api/travels/search');
    expect(req.request.params.get('q')).toBe('paris');
    req.flush([]);
  });

  it('autocompletes suggestions while typing, debounced', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/travelers/me/subscriptions').flush([]);
    httpMock.expectOne('/api/travels').flush([]);

    component['searchForm'].controls.q.setValue('par');
    await new Promise((resolve) => setTimeout(resolve, 300));

    const req = httpMock.expectOne((r) => r.url === '/api/travels/autocomplete');
    expect(req.request.params.get('q')).toBe('par');
    req.flush([TRAVEL('t1')]);

    expect(component['suggestions']().map((t) => t.id)).toEqual(['t1']);
  });

  it('does not autocomplete for queries shorter than 2 characters', async () => {
    fixture.detectChanges();
    httpMock.expectOne('/api/travels/travelers/me/subscriptions').flush([]);
    httpMock.expectOne('/api/travels').flush([]);

    component['searchForm'].controls.q.setValue('p');
    await new Promise((resolve) => setTimeout(resolve, 300));

    httpMock.expectNone((r) => r.url === '/api/travels/autocomplete');
    expect(component['suggestions']()).toHaveSize(0); // More specific assertion to ensure no suggestions are populated
    expect(component['searchForm'].controls.q.value).toBe('p'); // Assertion to ensure the query value is correctly set
  });
});
