import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CardRequest, CardResponse } from '../models/card.models';
import { CardPaymentHistory, CardPaymentRequest, CardPaymentResponse } from '../models/card-payment.models';

@Injectable({ providedIn: 'root' })
export class CreditCardService {
  private readonly http    = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/cards`;

  getCards(): Observable<CardResponse[]> {
    return this.http.get<CardResponse[]>(this.baseUrl);
  }

  createCard(req: CardRequest): Observable<CardResponse> {
    return this.http.post<CardResponse>(this.baseUrl, req);
  }

  updateCard(id: string, req: CardRequest): Observable<CardResponse> {
    return this.http.put<CardResponse>(`${this.baseUrl}/${id}`, req);
  }

  deleteCard(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  payCard(cardId: string, req: CardPaymentRequest): Observable<CardPaymentResponse> {
    return this.http.post<CardPaymentResponse>(`${this.baseUrl}/${cardId}/payments`, req);
  }

  getCardPayments(): Observable<CardPaymentHistory[]> {
    return this.http.get<CardPaymentHistory[]>(`${this.baseUrl}/payments`);
  }
}
