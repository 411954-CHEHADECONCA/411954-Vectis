import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['isLoggedIn']);
    routerSpy = jasmine.createSpyObj<Router>('Router', ['createUrlTree', 'navigate']);
    routerSpy.createUrlTree.and.callFake((commands) => {
      const tree = new UrlTree();
      (tree as any)._commands = commands;
      return tree;
    });

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('retorna true cuando el usuario está autenticado', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any)
    );

    expect(result).toBeTrue();
    expect(routerSpy.createUrlTree).not.toHaveBeenCalled();
  });

  it('retorna UrlTree hacia /login cuando el usuario no está autenticado', () => {
    authServiceSpy.isLoggedIn.and.returnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any)
    );

    expect(result).toBeInstanceOf(UrlTree);
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/login']);
  });

  it('llama a isLoggedIn() exactamente una vez por activación', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);

    TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

    expect(authServiceSpy.isLoggedIn).toHaveBeenCalledTimes(1);
  });
});
