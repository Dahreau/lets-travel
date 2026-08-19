import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PaymentsService } from './payments';

describe('PaymentsService', () => {
  let service: PaymentsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaymentsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the list of payments', () => {
    service.findAll().subscribe();
    const req = httpMock.expectOne('/api/payments');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('POSTs a new payment', () => {
    service
      .create({ travelId: 't1', ownerId: 'u1', paymentMethodId: 'm1', amount: 100, currency: 'EUR' })
      .subscribe();
    const req = httpMock.expectOne('/api/payments');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('POSTs a refund for a payment', () => {
    service.refund('p1').subscribe();
    const req = httpMock.expectOne('/api/payments/p1/refund');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });
});
