import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PaymentMethod, PaymentMethodRequest } from '../../core/models/payment';

@Injectable({ providedIn: 'root' })
export class PaymentMethodsService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/payment-methods';

  findAll(): Observable<PaymentMethod[]> {
    return this.http.get<PaymentMethod[]>(this.baseUrl);
  }

  findById(id: string): Observable<PaymentMethod> {
    return this.http.get<PaymentMethod>(`${this.baseUrl}/${id}`);
  }

  create(request: PaymentMethodRequest): Observable<PaymentMethod> {
    return this.http.post<PaymentMethod>(this.baseUrl, request);
  }

  update(id: string, request: PaymentMethodRequest): Observable<PaymentMethod> {
    return this.http.put<PaymentMethod>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
