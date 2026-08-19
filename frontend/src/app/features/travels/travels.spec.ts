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
        ownerId: 'u1',
        startDate: '2026-01-01',
        endDate: '2026-01-05',
        status: 'PLANNED',
        destinations: [],
        transportations: [],
      })
      .subscribe();
    const req = httpMock.expectOne('/api/travels');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('DELETEs a travel', () => {
    service.delete('t1').subscribe();
    const req = httpMock.expectOne('/api/travels/t1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
