import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AccountRequest, AccountResponse } from '../models/account.models';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http    = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/accounts`;

  getAccounts(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>(this.baseUrl);
  }

  createAccount(req: AccountRequest): Observable<AccountResponse> {
    return this.http.post<AccountResponse>(this.baseUrl, req);
  }

  updateAccount(id: string, req: AccountRequest): Observable<AccountResponse> {
    return this.http.put<AccountResponse>(`${this.baseUrl}/${id}`, req);
  }

  deleteAccount(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
