import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ReportsService } from './reports';

describe('ReportsService', () => {
  let service: ReportsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReportsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('POSTs a report for a travel', () => {
    service.submit('t1', { reportedType: 'MANAGER', reportedId: 'm1', reason: 'No show' }).subscribe();
    const req = httpMock.expectOne('/api/travels/t1/reports');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reportedType: 'MANAGER', reportedId: 'm1', reason: 'No show' });
    req.flush(null);
  });
});
