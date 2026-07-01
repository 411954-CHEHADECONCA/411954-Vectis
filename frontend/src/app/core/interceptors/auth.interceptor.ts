import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { catchError, Observable, switchMap, throwError } from 'rxjs';
import { HttpEvent } from '@angular/common/http';
import { StorageService } from '../services/storage.service';
import { AuthService } from '../services/auth.service';

const isAuthEndpoint = (url: string): boolean =>
  url.includes('/auth/login') ||
  url.includes('/auth/register') ||
  url.includes('/auth/refresh');

const attachToken = (req: HttpRequest<unknown>, token: string): HttpRequest<unknown> =>
  req.clone({ headers: req.headers.set('Authorization', `Bearer ${token}`) });

/**
 * Margen para refrescar el token un poco antes de su expiración real, cubriendo latencia de red
 * y desfasajes de reloj entre cliente y servidor.
 */
const EXPIRY_SKEW_MS = 10_000;

/**
 * Decodifica el `exp` del JWT y decide si está vencido (o por vencer dentro del margen de skew).
 * Si el token no es un JWT decodificable, retorna `false`: no se fuerza un refresh proactivo y se
 * deja actuar el fallback reactivo ante un eventual 401.
 */
const isAccessTokenExpired = (token: string): boolean => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    if (typeof payload?.exp !== 'number') return false;
    return Date.now() >= payload.exp * 1000 - EXPIRY_SKEW_MS;
  } catch {
    return false;
  }
};

/**
 * Envía el request con el token indicado y mantiene el fallback reactivo: si el backend responde
 * 401 (p. ej. el token fue revocado antes de expirar), refresca y reintenta una vez.
 */
const sendWithReactiveRefresh = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  token: string | null,
  authService: AuthService,
): Observable<HttpEvent<unknown>> => {
  const outgoing = token ? attachToken(req, token) : req;

  return next(outgoing).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthEndpoint(req.url)) {
        return throwError(() => error);
      }

      return authService.refreshToken().pipe(
        switchMap((res) => next(attachToken(req, res.accessToken))),
        catchError((refreshError) => {
          authService.logout();
          return throwError(() => refreshError);
        })
      );
    })
  );
};

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const storage = inject(StorageService);
  const authService = inject(AuthService);

  const token = storage.getAccessToken();

  // Refresh proactivo: si el access token ya venció (o está por vencer) y no es un endpoint de auth,
  // se refresca ANTES de enviar el request. Esto evita el ciclo 401 → refresh → retry (que ensucia
  // la consola/Network con un error espurio) y hace la sesión transparente para el usuario.
  if (token && !isAuthEndpoint(req.url) && isAccessTokenExpired(token)) {
    return authService.refreshToken().pipe(
      switchMap((res) => sendWithReactiveRefresh(req, next, res.accessToken, authService)),
      catchError((refreshError) => {
        authService.logout();
        return throwError(() => refreshError);
      })
    );
  }

  return sendWithReactiveRefresh(req, next, token, authService);
};
