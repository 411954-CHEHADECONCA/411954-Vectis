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
