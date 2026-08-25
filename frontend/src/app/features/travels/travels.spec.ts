import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TravelsService } from './travels';

describe('TravelsService', () => {
  let service: TravelsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TravelsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the list of travels', () => {
    service.findAll().subscribe();
    const req = httpMock.expectOne('/api/travels');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('POSTs a new travel', () => {
    service
      .create({
        title: 'Trip',
        managerId: 'u1',
        startDate: '2026-01-01',
        endDate: '2026-01-05',
        status: 'PLANNED',
        price: 500,
        currency: 'EUR',
        destinations: [],
        transportations: [],
      })
      .subscribe();
    const req = httpMock.expectOne('/api/travels');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.managerId).toBe('u1');
    req.flush({});
  });

  it('DELETEs a travel', () => {
    service.delete('t1').subscribe();
    const req = httpMock.expectOne('/api/travels/t1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('GETs search results with the query as a param', () => {
    service.search('paris').subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/travels/search');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('q')).toBe('paris');
    req.flush([]);
  });

  it('GETs autocomplete suggestions with the query as a param', () => {
    service.autocomplete('par').subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/travels/autocomplete');
    expect(req.request.params.get('q')).toBe('par');
    req.flush([]);
  });

  it('GETs personalized recommendations', () => {
    service.recommendations().subscribe();
    const req = httpMock.expectOne('/api/travels/recommendations');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
