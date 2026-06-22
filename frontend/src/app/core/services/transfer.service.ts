import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TransferRequest, TransferResponse } from '../models/transfer.models';

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly http = inject(HttpClient);

  create(req: TransferRequest): Observable<TransferResponse> {
    return this.http.post<TransferResponse>('/api/transfers', req);
  }

  delete(transferGroupId: string): Observable<void> {
    return this.http.delete<void>(`/api/transfers/${transferGroupId}`);
  }
}
