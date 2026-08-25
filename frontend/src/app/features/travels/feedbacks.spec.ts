import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FeedbacksService } from './feedbacks';

describe('FeedbacksService', () => {
  let service: FeedbacksService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FeedbacksService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the feedback for a travel', () => {
    service.listForTravel('t1').subscribe();
    const req = httpMock.expectOne('/api/travels/t1/feedbacks');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('POSTs a new feedback for a travel', () => {
    service.submit('t1', { rating: 5, comment: 'Great trip' }).subscribe();
    const req = httpMock.expectOne('/api/travels/t1/feedbacks');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ rating: 5, comment: 'Great trip' });
    req.flush({});
  });
});
