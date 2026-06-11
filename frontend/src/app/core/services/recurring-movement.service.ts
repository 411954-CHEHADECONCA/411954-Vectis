import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RecurringMovementRequest, RecurringMovementResponse } from '../models/recurring-movement.models';

@Injectable({ providedIn: 'root' })
export class RecurringMovementService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/recurring-movements`;

  getRecurringMovements(): Observable<RecurringMovementResponse[]> {
    return this.http.get<RecurringMovementResponse[]>(this.baseUrl);
  }

  createRecurringMovement(req: RecurringMovementRequest): Observable<RecurringMovementResponse> {
    return this.http.post<RecurringMovementResponse>(this.baseUrl, req);
  }

  updateRecurringMovement(id: string, req: RecurringMovementRequest): Observable<RecurringMovementResponse> {
    return this.http.put<RecurringMovementResponse>(`${this.baseUrl}/${id}`, req);
  }

  toggleActive(id: string): Observable<RecurringMovementResponse> {
    return this.http.patch<RecurringMovementResponse>(`${this.baseUrl}/${id}/toggle`, {});
  }

  deleteRecurringMovement(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
