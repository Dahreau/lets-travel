import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Payment, PaymentRequest } from '../../core/models/payment';

@Injectable({ providedIn: 'root' })
export class PaymentsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/payments';

  findAll(): Observable<Payment[]> {
    return this.http.get<Payment[]>(this.baseUrl);
  }

  findById(id: string): Observable<Payment> {
    return this.http.get<Payment>(`${this.baseUrl}/${id}`);
  }

  create(request: PaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(this.baseUrl, request);
  }

  refund(id: string): Observable<Payment> {
    return this.http.post<Payment>(`${this.baseUrl}/${id}/refund`, {});
  }
}
