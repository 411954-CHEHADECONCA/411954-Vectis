import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { StorageService } from '../services/storage.service';
import { AuthService } from '../services/auth.service';
import { AuthResponse } from '../models/auth.models';

const MOCK_REFRESH_RESPONSE: AuthResponse = {
  accessToken: 'new-access-token',
  refreshToken: 'new-refresh-token',
  tokenType: 'Bearer',
  user: { id: '1', email: 'a@a.com', fullName: 'User A' },
};

/** Construye un JWT de juguete con el `exp` (en segundos) indicado, decodificable por atob. */
const makeJwt = (expSeconds: number): string =>
  `header.${btoa(JSON.stringify({ exp: expSeconds }))}.signature`;

const nowSeconds = () => Math.floor(Date.now() / 1000);

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let storageSpy: jasmine.SpyObj<StorageService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    storageSpy = jasmine.createSpyObj<StorageService>('StorageService', [
      'getAccessToken',
      'setAccessToken',
      'getRefreshToken',
      'setRefreshToken',
      'getUser',
      'setUser',
      'clear',
    ]);

    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', [
      'refreshToken',
      'logout',
      'isLoggedIn',
    ]);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: StorageService, useValue: storageSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ─── Adjuntar header ────────────────────────────────────────────────────────

  it('adjunta el header Authorization cuando hay un access token', () => {
    storageSpy.getAccessToken.and.returnValue('my-valid-token');

    http.get('/api/some-resource').subscribe();

    const req = httpMock.expectOne('/api/some-resource');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-valid-token');
    req.flush({});
  });

  it('no adjunta el header Authorization cuando no hay token', () => {
    storageSpy.getAccessToken.and.returnValue(null);

    http.get('/api/some-resource').subscribe();

    const req = httpMock.expectOne('/api/some-resource');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  // ─── Manejo de 401 — retry con refresh ─────────────────────────────────────

  it('reintenta el request original con el nuevo token al recibir 401', (done) => {
    storageSpy.getAccessToken.and.returnValue('old-token');
    authServiceSpy.refreshToken.and.returnValue(of(MOCK_REFRESH_RESPONSE));

    http.get('/api/protected').subscribe({
      next: (res: any) => {
        expect(res.data).toBe('ok');
        done();
      },
      error: done.fail,
    });

    // Primera respuesta → 401
    const firstReq = httpMock.expectOne('/api/protected');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer old-token');
    firstReq.flush({}, { status: 401, statusText: 'Unauthorized' });

    // Retry con el nuevo token
    const retryReq = httpMock.expectOne('/api/protected');
    expect(retryReq.request.headers.get('Authorization')).toBe(
      'Bearer new-access-token'
    );
    retryReq.flush({ data: 'ok' });
  });

  it('no reintenta cuando el 401 viene de un endpoint de autenticación', (done) => {
    storageSpy.getAccessToken.and.returnValue('token');

    http.post('/api/auth/login', {}).subscribe({
      next: () => done.fail('Expected error but received success'),
      error: (err) => {
        expect(err.status).toBe(401);
        expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
        done();
      },
    });

    const req = httpMock.expectOne('/api/auth/login');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  it('llama a logout() cuando el refresh falla y propaga el error', (done) => {
    storageSpy.getAccessToken.and.returnValue('expired-token');
    authServiceSpy.refreshToken.and.returnValue(
      throwError(() => new Error('Refresh failed'))
    );

    http.get('/api/protected').subscribe({
      next: () => done.fail('Expected error but received success'),
      error: () => {
        expect(authServiceSpy.logout).toHaveBeenCalled();
        done();
      },
    });

    const req = httpMock.expectOne('/api/protected');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
  });

  // ─── Refresh proactivo (token vencido, sin 401) ─────────────────────────────

  it('refresca proactivamente cuando el access token está vencido y envía el request con el token nuevo (sin 401)', (done) => {
    storageSpy.getAccessToken.and.returnValue(makeJwt(nowSeconds() - 60)); // vencido hace 1 min
    authServiceSpy.refreshToken.and.returnValue(of(MOCK_REFRESH_RESPONSE));

    http.get('/api/protected').subscribe({
      next: (res: any) => {
        expect(res.data).toBe('ok');
        done();
      },
      error: done.fail,
    });

    // No debe haber un primer request con 401: el único request ya lleva el token nuevo.
    const req = httpMock.expectOne('/api/protected');
    expect(req.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    expect(authServiceSpy.refreshToken).toHaveBeenCalledTimes(1);
    req.flush({ data: 'ok' });
  });

  it('NO refresca proactivamente cuando el access token sigue vigente', () => {
    storageSpy.getAccessToken.and.returnValue(makeJwt(nowSeconds() + 3600)); // vence en 1 h

    http.get('/api/protected').subscribe();

    const req = httpMock.expectOne('/api/protected');
    expect(req.request.headers.get('Authorization')).toContain('Bearer ');
    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
    req.flush({});
  });

  it('cierra sesión si el refresh proactivo falla', (done) => {
    storageSpy.getAccessToken.and.returnValue(makeJwt(nowSeconds() - 60));
    authServiceSpy.refreshToken.and.returnValue(throwError(() => new Error('Refresh failed')));

    http.get('/api/protected').subscribe({
      next: () => done.fail('Expected error but received success'),
      error: () => {
        expect(authServiceSpy.logout).toHaveBeenCalled();
        done();
      },
    });

    // No se emite ningún request HTTP porque el refresh proactivo falló antes de enviar.
  });

  // ─── Errores no-401 se propagan sin retry ───────────────────────────────────

  it('propaga errores 500 sin intentar refresh', (done) => {
    storageSpy.getAccessToken.and.returnValue('token');

    http.get('/api/resource').subscribe({
      next: () => done.fail('Expected error but received success'),
      error: (err) => {
        expect(err.status).toBe(500);
        expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
        done();
      },
    });

    const req = httpMock.expectOne('/api/resource');
    req.flush({}, { status: 500, statusText: 'Internal Server Error' });
  });
});
