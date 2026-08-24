import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SubscriptionsService } from './subscriptions';

describe('SubscriptionsService', () => {
  let service: SubscriptionsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SubscriptionsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the subscribers of a travel', () => {
    service.listSubscribers('t1').subscribe();
    const req = httpMock.expectOne('/api/travels/t1/subscriptions');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('DELETEs a subscription to unsubscribe a traveler', () => {
    service.unsubscribe('t1', 's1').subscribe();
    const req = httpMock.expectOne('/api/travels/t1/subscriptions/s1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
