import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ExchangeRateResponse, InflationResponse } from '../models/macro.models';

@Injectable({ providedIn: 'root' })
export class MacroService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  getLatestOficialRate(): Observable<ExchangeRateResponse> {
    return this.http.get<ExchangeRateResponse>(`${this.base}/exchange-rates/oficial/latest`);
  }

  getLatestMepRate(): Observable<ExchangeRateResponse> {
    return this.http.get<ExchangeRateResponse>(`${this.base}/exchange-rates/mep/latest`);
  }

  getLatestInflation(): Observable<InflationResponse> {
    return this.http.get<InflationResponse>(`${this.base}/inflation/latest`);
  }
}
