import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ManagerStatsService } from './manager-stats';

describe('ManagerStatsService', () => {
  let service: ManagerStatsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ManagerStatsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the connected manager own stats', () => {
    service.myStats().subscribe();
    const req = httpMock.expectOne('/api/travels/managers/me/stats');
    expect(req.request.method).toBe('GET');
    req.flush({ travelCount: 2, travelerCount: 5, estimatedRevenue: 1000 });
  });

  it('GETs the public stats of a manager', () => {
    service.publicStats('m1').subscribe();
    const req = httpMock.expectOne('/api/travels/managers/m1/public-stats');
    expect(req.request.method).toBe('GET');
    req.flush({ travelCount: 2, averageRating: 4.5, reportCount: 0 });
  });
});
