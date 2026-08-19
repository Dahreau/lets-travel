import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PaymentMethodsService } from './payment-methods';

describe('PaymentMethodsService', () => {
  let service: PaymentMethodsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PaymentMethodsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the list of payment methods', () => {
    service.findAll().subscribe();
    const req = httpMock.expectOne('/api/payment-methods');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('DELETEs a payment method', () => {
    service.delete('m1').subscribe();
    const req = httpMock.expectOne('/api/payment-methods/m1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
