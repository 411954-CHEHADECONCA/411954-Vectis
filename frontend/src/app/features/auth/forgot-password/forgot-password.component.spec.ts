import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ForgotPasswordComponent } from './forgot-password.component';
import { AuthService } from '../../../core/services/auth.service';

describe('ForgotPasswordComponent', () => {
  let component: ForgotPasswordComponent;
  let fixture: ComponentFixture<ForgotPasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['forgotPassword']);

    await TestBed.configureTestingModule({
      imports: [ForgotPasswordComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('form inválido con email vacío no llama al servicio', () => {
    component.form.controls.email.setValue('');
    component.onSubmit();
    expect(authServiceSpy.forgotPassword).not.toHaveBeenCalled();
  });

  it('form inválido con email mal formateado no llama al servicio', () => {
    component.form.controls.email.setValue('no-es-un-email');
    component.onSubmit();
    expect(authServiceSpy.forgotPassword).not.toHaveBeenCalled();
  });

  it('form válido llama a forgotPassword() con el email correcto y activa submitted', () => {
    authServiceSpy.forgotPassword.and.returnValue(of(undefined));

    component.form.controls.email.setValue('user@vectis.com');
    component.onSubmit();

    expect(authServiceSpy.forgotPassword).toHaveBeenCalledWith('user@vectis.com');
    expect(component.submitted()).toBeTrue();
  });

  it('estado submitted oculta el form y muestra mensaje de éxito', () => {
    authServiceSpy.forgotPassword.and.returnValue(of(undefined));

    component.form.controls.email.setValue('user@vectis.com');
    component.onSubmit();
    fixture.detectChanges();

    const successBlock = fixture.nativeElement.querySelector('.success-block');
    const form = fixture.nativeElement.querySelector('form');
    expect(successBlock).toBeTruthy();
    expect(form).toBeNull();
  });

  it('error de API muestra errorMessage y desactiva loading', () => {
    authServiceSpy.forgotPassword.and.returnValue(
      throwError(() => ({ error: { message: 'Error del servidor' } }))
    );

    component.form.controls.email.setValue('user@vectis.com');
    component.onSubmit();

    expect(component.errorMessage()).toBe('Error del servidor');
    expect(component.loading()).toBeFalse();
  });

  it('loading() desactiva el botón durante la petición', () => {
    authServiceSpy.forgotPassword.and.returnValue(of(undefined));

    component.loading.set(true);
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('.submit-btn');
    expect(btn.disabled).toBeTrue();
  });
});
