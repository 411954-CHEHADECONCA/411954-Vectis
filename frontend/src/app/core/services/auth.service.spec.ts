import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { StorageService } from './storage.service';
import { AuthResponse, UserInfo } from '../models/auth.models';

const MOCK_USER: UserInfo = {
  id: 'user-123',
  email: 'test@vectis.com',
  fullName: 'Test User',
};

const MOCK_RESPONSE: AuthResponse = {
  accessToken: 'access-token-abc',
  refreshToken: 'refresh-token-xyz',
  tokenType: 'Bearer',
  user: MOCK_USER,
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let storageSpy: jasmine.SpyObj<StorageService>;
  let routerSpy: jasmine.SpyObj<Router>;

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
    storageSpy.getUser.and.returnValue(null);
    storageSpy.getAccessToken.and.returnValue(null);

    routerSpy = jasmine.createSpyObj<Router>('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: StorageService, useValue: storageSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ─── Estado inicial ─────────────────────────────────────────────────────────

  it('se crea correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('isLoggedIn() retorna false cuando no hay access token', () => {
    storageSpy.getAccessToken.and.returnValue(null);
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('isLoggedIn() retorna true cuando existe un access token', () => {
    storageSpy.getAccessToken.and.returnValue('some-token');
    expect(service.isLoggedIn()).toBeTrue();
  });

  it('currentUser$ emite null cuando no hay usuario almacenado', (done) => {
    service.currentUser$.subscribe((user) => {
      expect(user).toBeNull();
      done();
    });
  });

  // ─── login() ────────────────────────────────────────────────────────────────

  it('login() almacena access token y refresh token en storage', () => {
    service.login({ email: 'test@vectis.com', password: 'password123' }).subscribe();

    const req = httpMock.expectOne((r) => r.url.includes('/auth/login'));
    expect(req.request.method).toBe('POST');
    req.flush(MOCK_RESPONSE);

    expect(storageSpy.setAccessToken).toHaveBeenCalledWith('access-token-abc');
    expect(storageSpy.setRefreshToken).toHaveBeenCalledWith('refresh-token-xyz');
  });

  it('login() actualiza currentUser$ con los datos del usuario', (done) => {
    service.login({ email: 'test@vectis.com', password: 'password123' }).subscribe();

    const req = httpMock.expectOne((r) => r.url.includes('/auth/login'));
    req.flush(MOCK_RESPONSE);

    // BehaviorSubject emite el valor actual inmediatamente al suscribirse
    service.currentUser$.subscribe((user) => {
      expect(user?.email).toBe('test@vectis.com');
      done();
    });
  });

  it('login() hace POST al endpoint correcto con las credenciales', () => {
    const credentials = { email: 'test@vectis.com', password: 'password123' };
    service.login(credentials).subscribe();

    const req = httpMock.expectOne((r) => r.url.includes('/auth/login'));
    expect(req.request.body).toEqual(credentials);
    req.flush(MOCK_RESPONSE);
  });

  // ─── register() ─────────────────────────────────────────────────────────────

  it('register() almacena tokens y persiste el usuario', () => {
    service
      .register({
        email: 'new@vectis.com',
        password: 'password123',
        fullName: 'New User',
      })
      .subscribe();

    const req = httpMock.expectOne((r) => r.url.includes('/auth/register'));
    expect(req.request.method).toBe('POST');
    req.flush(MOCK_RESPONSE);

    expect(storageSpy.setAccessToken).toHaveBeenCalledWith('access-token-abc');
    expect(storageSpy.setUser).toHaveBeenCalledWith(MOCK_USER);
  });

  // ─── refreshToken() ──────────────────────────────────────────────────────────

  it('refreshToken() envía el refresh token almacenado y actualiza el access token', () => {
    storageSpy.getRefreshToken.and.returnValue('old-refresh-token');

    service.refreshToken().subscribe();

    const req = httpMock.expectOne((r) => r.url.includes('/auth/refresh'));
    expect(req.request.body).toEqual({ refreshToken: 'old-refresh-token' });
    req.flush(MOCK_RESPONSE);

    expect(storageSpy.setAccessToken).toHaveBeenCalledWith('access-token-abc');
    expect(storageSpy.setRefreshToken).toHaveBeenCalledWith('refresh-token-xyz');
  });

  // ─── logout() ───────────────────────────────────────────────────────────────

  it('logout() limpia el storage y navega a /login', () => {
    storageSpy.getRefreshToken.and.returnValue('my-refresh-token');

    service.logout();

    const req = httpMock.expectOne((r) => r.url.includes('/auth/logout'));
    req.flush(null);

    expect(storageSpy.clear).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('logout() limpia el storage aunque no haya refresh token', () => {
    storageSpy.getRefreshToken.and.returnValue(null);

    service.logout();

    httpMock.expectNone((r) => r.url.includes('/auth/logout'));

    expect(storageSpy.clear).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});
