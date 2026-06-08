import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StorageService } from './storage.service';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  RegisterRequest,
  UserInfo,
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storage = inject(StorageService);
  private readonly router = inject(Router);

  private readonly baseUrl = `${environment.apiUrl}/auth`;

  private readonly _currentUser$ = new BehaviorSubject<UserInfo | null>(
    this.storage.getUser<UserInfo>()
  );

  readonly currentUser$ = this._currentUser$.asObservable();

  isLoggedIn(): boolean {
    return !!this.storage.getAccessToken();
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/register`, request)
      .pipe(tap((res) => this.storeSession(res)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/login`, request)
      .pipe(tap((res) => this.storeSession(res)));
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.storage.getRefreshToken();
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/refresh`, { refreshToken })
      .pipe(
        tap((res) => {
          this.storage.setAccessToken(res.accessToken);
          this.storage.setRefreshToken(res.refreshToken);
        })
      );
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/reset-password`, { token, newPassword });
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.patch<void>(
      `${environment.apiUrl}/users/me/password`,
      request
    );
  }

  logout(): void {
    const refreshToken = this.storage.getRefreshToken();
    if (refreshToken) {
      this.http
        .post(`${this.baseUrl}/logout`, { refreshToken })
        .subscribe({ error: () => {} });
    }
    this.clearSession();
    this.router.navigate(['/login']);
  }

  private storeSession(res: AuthResponse): void {
    this.storage.setAccessToken(res.accessToken);
    this.storage.setRefreshToken(res.refreshToken);
    this.storage.setUser(res.user);
    this._currentUser$.next(res.user);
  }

  private clearSession(): void {
    this.storage.clear();
    this._currentUser$.next(null);
  }
}
