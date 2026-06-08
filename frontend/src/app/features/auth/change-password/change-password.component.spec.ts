import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ChangePasswordComponent } from './change-password.component';
import { AuthService } from '../../../core/services/auth.service';

describe('ChangePasswordComponent', () => {
  let component: ChangePasswordComponent;
  let fixture: ComponentFixture<ChangePasswordComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', ['changePassword']);

    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChangePasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('form inválido con campos vacíos no llama al servicio', () => {
    component.onSubmit();
    expect(authServiceSpy.changePassword).not.toHaveBeenCalled();
  });

  it('contraseñas nuevas distintas muestran error passwordMismatch', () => {
    component.form.controls.currentPassword.setValue('oldPass123');
    component.form.controls.newPassword.setValue('newPass123');
    component.form.controls.confirmNewPassword.setValue('diferente456');
    component.form.controls.confirmNewPassword.markAsTouched();

    expect(component.form.hasError('passwordMismatch')).toBeTrue();
  });

  it('submit válido llama a changePassword() con currentPassword y newPassword', () => {
    authServiceSpy.changePassword.and.returnValue(of(undefined));

    component.form.controls.currentPassword.setValue('oldPass123');
    component.form.controls.newPassword.setValue('newPass123');
    component.form.controls.confirmNewPassword.setValue('newPass123');
    component.onSubmit();

    expect(authServiceSpy.changePassword).toHaveBeenCalledWith({
      currentPassword: 'oldPass123',
      newPassword: 'newPass123',
    });
  });

  it('éxito activa signal success y resetea el form', () => {
    authServiceSpy.changePassword.and.returnValue(of(undefined));

    component.form.controls.currentPassword.setValue('oldPass123');
    component.form.controls.newPassword.setValue('newPass123');
    component.form.controls.confirmNewPassword.setValue('newPass123');
    component.onSubmit();

    expect(component.success()).toBeTrue();
    expect(component.loading()).toBeFalse();
  });

  it('éxito muestra bloque de confirmación en el DOM', () => {
    authServiceSpy.changePassword.and.returnValue(of(undefined));

    component.form.controls.currentPassword.setValue('oldPass123');
    component.form.controls.newPassword.setValue('newPass123');
    component.form.controls.confirmNewPassword.setValue('newPass123');
    component.onSubmit();
    fixture.detectChanges();

    const successBlock = fixture.nativeElement.querySelector('.success-block');
    expect(successBlock).toBeTruthy();
  });

  it('error de API muestra errorMessage y desactiva loading', () => {
    authServiceSpy.changePassword.and.returnValue(
      throwError(() => ({ error: { message: 'Contraseña actual incorrecta' } }))
    );

    component.form.controls.currentPassword.setValue('wrongPass');
    component.form.controls.newPassword.setValue('newPass123');
    component.form.controls.confirmNewPassword.setValue('newPass123');
    component.onSubmit();

    expect(component.errorMessage()).toBe('Contraseña actual incorrecta');
    expect(component.loading()).toBeFalse();
  });

  it('botón deshabilitado durante loading()', () => {
    component.loading.set(true);
    fixture.detectChanges();

    const btn = fixture.nativeElement.querySelector('.submit-btn');
    expect(btn.disabled).toBeTrue();
  });
});
