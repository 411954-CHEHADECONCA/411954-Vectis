import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RegisterComponent } from './register.component';
import { AuthService } from '../../../core/services/auth.service';
import { AuthResponse } from '../../../core/models/auth.models';

const MOCK_AUTH_RESPONSE: AuthResponse = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  user: { id: '1', email: 'new@vectis.com', fullName: 'New User' },
};

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let navigateSpy: jasmine.Spy;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['register']);

    await TestBed.configureTestingModule({
      imports: [RegisterComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
    navigateSpy = spyOn(TestBed.inject(Router), 'navigate').and.returnValue(
      Promise.resolve(true)
    );
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(component).toBeTruthy();
  });

  // ─── Validación del form ─────────────────────────────────────────────────────

  it('form inválido cuando todos los campos están vacíos', () => {
    expect(component.form.invalid).toBeTrue();
  });

  it('error passwordMismatch cuando las contraseñas no coinciden', () => {
    component.form.setValue({
      fullName: 'Test User',
      email: 'test@vectis.com',
      password: 'password123',
      confirmPassword: 'different',
    });
    expect(component.form.hasError('passwordMismatch')).toBeTrue();
  });

  it('no hay error passwordMismatch cuando las contraseñas coinciden', () => {
    component.form.setValue({
      fullName: 'Test User',
      email: 'test@vectis.com',
      password: 'password123',
      confirmPassword: 'password123',
    });
    expect(component.form.hasError('passwordMismatch')).toBeFalse();
  });

  it('form inválido con nombre demasiado corto (1 carácter)', () => {
    component.form.setValue({
      fullName: 'A',
      email: 'test@vectis.com',
      password: 'password123',
      confirmPassword: 'password123',
    });
    expect(component.form.controls.fullName.invalid).toBeTrue();
  });

  // ─── Submit ──────────────────────────────────────────────────────────────────

  it('onSubmit() no llama al servicio si el form es inválido', () => {
    component.onSubmit();
    expect(authServiceSpy.register).not.toHaveBeenCalled();
  });

  it('onSubmit() llama a authService.register() con los datos correctos y navega a /dashboard', () => {
    authServiceSpy.register.and.returnValue(of(MOCK_AUTH_RESPONSE));

    component.form.setValue({
      fullName: 'Test User',
      email: 'test@vectis.com',
      password: 'password123',
      confirmPassword: 'password123',
    });

    component.onSubmit();

    expect(authServiceSpy.register).toHaveBeenCalledOnceWith({
      fullName: 'Test User',
      email: 'test@vectis.com',
      password: 'password123',
    });
    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
  });

  it('error de API establece errorMessage y desactiva loading', () => {
    const apiError = { error: { message: 'Email ya registrado' } };
    authServiceSpy.register.and.returnValue(throwError(() => apiError));

    component.form.setValue({
      fullName: 'Test User',
      email: 'dup@vectis.com',
      password: 'password123',
      confirmPassword: 'password123',
    });

    component.onSubmit();

    expect(component.errorMessage()).toBe('Email ya registrado');
    expect(component.loading()).toBeFalse();
  });

  it('error sin mensaje usa el fallback genérico', () => {
    authServiceSpy.register.and.returnValue(throwError(() => new Error('Network error')));

    component.form.setValue({
      fullName: 'Test User',
      email: 'test@vectis.com',
      password: 'password123',
      confirmPassword: 'password123',
    });

    component.onSubmit();

    expect(component.errorMessage()).toBe('Error al registrarse. Intentá de nuevo.');
  });

  it('loading() es false tras el error del servicio', () => {
    authServiceSpy.register.and.returnValue(throwError(() => ({})));

    component.form.setValue({
      fullName: 'Test User',
      email: 'test@vectis.com',
      password: 'password123',
      confirmPassword: 'password123',
    });

    component.onSubmit();

    expect(component.loading()).toBeFalse();
  });
});
