import { Injectable } from '@angular/core';

const KEYS = {
  accessToken: 'vectis_access_token',
  refreshToken: 'vectis_refresh_token',
  user: 'vectis_user',
} as const;

@Injectable({ providedIn: 'root' })
export class StorageService {

  getAccessToken(): string | null {
    return localStorage.getItem(KEYS.accessToken);
  }

  setAccessToken(token: string): void {
    localStorage.setItem(KEYS.accessToken, token);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(KEYS.refreshToken);
  }

  setRefreshToken(token: string): void {
    localStorage.setItem(KEYS.refreshToken, token);
  }

  getUser<T>(): T | null {
    const raw = localStorage.getItem(KEYS.user);
    return raw ? (JSON.parse(raw) as T) : null;
  }

  setUser(user: unknown): void {
    localStorage.setItem(KEYS.user, JSON.stringify(user));
  }

  clear(): void {
    localStorage.removeItem(KEYS.accessToken);
    localStorage.removeItem(KEYS.refreshToken);
    localStorage.removeItem(KEYS.user);
  }
}
