import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminStatsService } from './admin-stats';

describe('AdminStatsService', () => {
  let service: AdminStatsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminStatsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the manager rankings', () => {
    service.managerRankings().subscribe();
    const req = httpMock.expectOne('/api/travels/admin/manager-rankings');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GETs the travel rankings', () => {
    service.travelRankings().subscribe();
    const req = httpMock.expectOne('/api/travels/admin/travel-rankings');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
