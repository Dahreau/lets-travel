import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TravelerStatsService } from './traveler-stats';

describe('TravelerStatsService', () => {
  let service: TravelerStatsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TravelerStatsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the connected traveler personal stats', () => {
    service.myStats().subscribe();
    const req = httpMock.expectOne('/api/travels/travelers/me/stats');
    expect(req.request.method).toBe('GET');
    req.flush({ participationCount: 0, feedbackCount: 0, reportCount: 0, cancellationCount: 0 });
  });

  it('GETs the connected traveler subscription history', () => {
    service.mySubscriptions().subscribe();
    const req = httpMock.expectOne('/api/travels/travelers/me/subscriptions');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GETs the connected traveler own feedback', () => {
    service.myFeedbacks().subscribe();
    const req = httpMock.expectOne('/api/travels/travelers/me/feedbacks');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('GETs the connected traveler own reports', () => {
    service.myReports().subscribe();
    const req = httpMock.expectOne('/api/travels/travelers/me/reports');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
