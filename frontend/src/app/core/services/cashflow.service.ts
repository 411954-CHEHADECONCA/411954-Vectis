import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CashflowResponse } from '../models/cashflow.models';

@Injectable({ providedIn: 'root' })
export class CashflowService {
  private readonly http   = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/cashflow`;

  getCashflow(year: number, month: number): Observable<CashflowResponse> {
    const params = new HttpParams()
      .set('year',  year.toString())
      .set('month', month.toString());
    return this.http.get<CashflowResponse>(this.apiUrl, { params });
  }
}
