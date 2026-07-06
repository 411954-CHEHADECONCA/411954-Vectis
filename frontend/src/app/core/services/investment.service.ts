import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import {
  FciFundOption,
  InstrumentOption,
  InvestmentCollectPreviewResponse,
  InvestmentCollectRequest,
  InvestmentCollectResponse,
  InvestmentMovementRequest,
  InvestmentMovementUpdateRequest,
  InvestmentRequest,
  InvestmentResponse,
  InvestmentValuationRequest,
  MarketApiStatus,
} from '../models/investment.models';

@Injectable({ providedIn: 'root' })
export class InvestmentService {
  private readonly http    = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/investments`;

  getInvestments(): Observable<InvestmentResponse[]> {
    return this.http.get<InvestmentResponse[]>(this.baseUrl);
  }

  createInvestment(req: InvestmentRequest): Observable<InvestmentResponse> {
    return this.http.post<InvestmentResponse>(this.baseUrl, req);
  }

  updateInvestment(id: string, req: InvestmentRequest): Observable<InvestmentResponse> {
    return this.http.put<InvestmentResponse>(`${this.baseUrl}/${id}`, req);
  }

  deleteInvestment(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  collectInvestment(id: string, req: InvestmentCollectRequest): Observable<InvestmentCollectResponse> {
    return this.http.post<InvestmentCollectResponse>(`${this.baseUrl}/${id}/collect`, req);
  }

  /** Preview de capital/rendimiento a una fecha de cobro dada, sin persistir nada. */
  previewCollect(id: string, date: string): Observable<InvestmentCollectPreviewResponse> {
    return this.http.get<InvestmentCollectPreviewResponse>(
      `${this.baseUrl}/${id}/collect-preview`,
      { params: { date } },
    );
  }

  addMovement(investmentId: string, req: InvestmentMovementRequest): Observable<InvestmentResponse> {
    return this.http.post<InvestmentResponse>(`${this.baseUrl}/${investmentId}/movements`, req);
  }

  updateMovement(investmentId: string, movId: string, req: InvestmentMovementUpdateRequest): Observable<InvestmentResponse> {
    return this.http.put<InvestmentResponse>(`${this.baseUrl}/${investmentId}/movements/${movId}`, req);
  }

  deleteMovement(investmentId: string, movId: string): Observable<InvestmentResponse> {
    return this.http.delete<InvestmentResponse>(`${this.baseUrl}/${investmentId}/movements/${movId}`);
  }

  addValuation(investmentId: string, req: InvestmentValuationRequest): Observable<InvestmentResponse> {
    return this.http.post<InvestmentResponse>(`${this.baseUrl}/${investmentId}/valuations`, req);
  }

  updateValuation(investmentId: string, valId: string, req: InvestmentValuationRequest): Observable<InvestmentResponse> {
    return this.http.put<InvestmentResponse>(`${this.baseUrl}/${investmentId}/valuations/${valId}`, req);
  }

  deleteValuation(investmentId: string, valId: string): Observable<InvestmentResponse> {
    return this.http.delete<InvestmentResponse>(`${this.baseUrl}/${investmentId}/valuations/${valId}`);
  }

  getFciFunds(categoria: string): Observable<FciFundOption[]> {
    return this.http.get<FciFundOption[]>(`${this.baseUrl}/market/fci-funds`, { params: { categoria } });
  }

  getInstruments(tipo?: string): Observable<InstrumentOption[]> {
    const params: Record<string, string> = tipo ? { tipo } : {};
    return this.http.get<InstrumentOption[]>(`${this.baseUrl}/market/instruments`, { params });
  }

  getMarketApiStatus(): Observable<MarketApiStatus> {
    return this.http.get<MarketApiStatus>(`${this.baseUrl}/market/status`);
  }

  getFciVcp(fondo: string, fecha: string): Observable<{ fondo: string; vcp: number; fecha: string } | null> {
    return this.http
      .get<{ fondo: string; vcp: number; fecha: string }>(
        `${this.baseUrl}/market/fci-vcp`,
        { params: { fondo, fecha } },
      )
      .pipe(catchError(() => of(null)));
  }

  getInstrumentPrice(
    ticker: string, type: string, fecha: string,
  ): Observable<{ pricePerUnit: number; fecha: string } | null> {
    return this.http
      .get<{ pricePerUnit: number; fecha: string }>(
        `${this.baseUrl}/market/instrument-price`,
        { params: { ticker, type, fecha } },
      )
      .pipe(catchError(() => of(null)));
  }
}
