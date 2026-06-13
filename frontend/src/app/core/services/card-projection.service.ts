import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CardMatrix, CardOverview } from '../models/card-projection.models';

@Injectable({ providedIn: 'root' })
export class CardProjectionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/cards`;

  getOverview(): Observable<CardOverview> {
    return this.http.get<CardOverview>(`${this.baseUrl}/overview`);
  }

  getMatrix(months: number): Observable<CardMatrix> {
    return this.http.get<CardMatrix>(`${this.baseUrl}/matrix`, {
      params: new HttpParams().set('months', String(months)),
    });
  }
}
