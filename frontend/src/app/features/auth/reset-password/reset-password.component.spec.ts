import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ResetPasswordComponent } from './reset-password.component';
import { AuthService } from '../../../core/services/auth.service';

describe('ResetPasswordComponent', () => {
  let component: ResetPasswordComponent;
  let fixture: ComponentFixture<ResetPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  function createComponent(queryParams: Record<string, string> = {}): void {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { snapshot: { queryParams } },
    });
    fixture = TestBed.createComponent(ResetPasswordComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['resetPassword']);

    await TestBed.configureTestingModule({
      imports: [ResetPasswordComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();
  });

  it('se crea correctamente con token presente', () => {
    createComponent({ token: 'abc123' });
    expect(component).toBeTruthy();
  });

  it('sin query param token redirige a /forgot-password', () => {
    createComponent({});
    const navigateSpy = spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));
    component.ngOnInit();
    expect(navigateSpy).toHaveBeenCalledWith(['/forgot-password']);
  });

  it('contraseñas distintas muestran error passwordMismatch', () => {
    createComponent({ token: 'abc123' });

    component.form.controls.newPassword.setValue('password123');
    component.form.controls.confirmPassword.setValue('diferente456');
    component.form.controls.confirmPassword.markAsTouched();
    fixture.detectChanges();

    expect(component.form.hasError('passwordMismatch')).toBeTrue();
  });

  it('submit válido llama a resetPassword() con el token y la contraseña y navega a /login', () => {
    createComponent({ token: 'abc123' });
    authServiceSpy.resetPassword.and.returnValue(of(undefined));
    const navigateSpy = spyOn(router, 'navigate').and.returnValue(Promise.resolve(true));

    component.form.controls.newPassword.setValue('newPass123');
    component.form.controls.confirmPassword.setValue('newPass123');
    component.onSubmit();

    expect(authServiceSpy.resetPassword).toHaveBeenCalledWith('abc123', 'newPass123');
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });

  it('error de API muestra errorMessage y desactiva loading', () => {
    createComponent({ token: 'abc123' });
    authServiceSpy.resetPassword.and.returnValue(
      throwError(() => ({ error: { message: 'Token expirado' } }))
    );

    component.form.controls.newPassword.setValue('newPass123');
    component.form.controls.confirmPassword.setValue('newPass123');
    component.onSubmit();

    expect(component.errorMessage()).toBe('Token expirado');
    expect(component.loading()).toBeFalse();
  });

  it('botón deshabilitado durante loading()', () => {
    createComponent({ token: 'abc123' });
    component.loading.set(true);
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('.submit-btn');
    expect(btn.disabled).toBeTrue();
  });
});
