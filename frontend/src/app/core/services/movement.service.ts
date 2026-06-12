import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/pagination.models';
import {
  MovementFilters,
  MovementRequest,
  MovementResponse,
  MovementSummary,
} from '../models/movement.models';

@Injectable({ providedIn: 'root' })
export class MovementService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/movements`;

  search(filters: MovementFilters): Observable<PageResponse<MovementResponse>> {
    return this.http.get<PageResponse<MovementResponse>>(this.baseUrl, {
      params: this.buildParams(filters, true),
    });
  }

  summary(filters: MovementFilters): Observable<MovementSummary> {
    return this.http.get<MovementSummary>(`${this.baseUrl}/summary`, {
      params: this.buildParams(filters, false),
    });
  }

  create(req: MovementRequest): Observable<MovementResponse[]> {
    return this.http.post<MovementResponse[]>(this.baseUrl, req);
  }

  update(id: string, req: MovementRequest): Observable<MovementResponse> {
    return this.http.put<MovementResponse>(`${this.baseUrl}/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  private buildParams(filters: MovementFilters, withPaging: boolean): HttpParams {
    let params = new HttpParams().set('from', filters.from).set('to', filters.to);
    if (filters.type)       params = params.set('type', filters.type);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.q)          params = params.set('q', filters.q);
    if (withPaging) {
      params = params
        .set('page', String(filters.page ?? 0))
        .set('size', String(filters.size ?? 20));
    }
    return params;
  }
}
