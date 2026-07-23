import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, RouterLink } from '@angular/router';
import { By } from '@angular/platform-browser';
import { LegalLayoutComponent } from './legal-layout.component';

@Component({
  standalone: true,
  imports: [LegalLayoutComponent],
  template: `
    <app-legal-layout eyebrow="Legal" title="Título de prueba">
      <p class="host-body">Contenido proyectado del cuerpo.</p>
      <span legalFooter>Nota de pie <a routerLink="/contacto">Contacto</a></span>
    </app-legal-layout>
  `,
})
class HostComponent {}

describe('LegalLayoutComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renderiza el eyebrow y el title recibidos como inputs', () => {
    const eyebrow = fixture.debugElement.query(By.css('.eyebrow'));
    const title = fixture.debugElement.query(By.css('h1'));

    expect(eyebrow.nativeElement.textContent.trim()).toBe('Legal');
    expect(title.nativeElement.textContent.trim()).toBe('Título de prueba');
  });

  it('muestra un botón "Volver" con routerLink a /login', () => {
    const backLink = fixture.debugElement.query(By.css('.back-link'));

    expect(backLink).toBeTruthy();
    expect(backLink.nativeElement.textContent.trim()).toBe('Volver');
    expect(backLink.injector.get(RouterLink).href).toBe('/login');
  });

  it('el brand row también enlaza a /login', () => {
    const brandRow = fixture.debugElement.query(By.css('.brand-row'));
    expect(brandRow).toBeTruthy();
    expect(brandRow.nativeElement.textContent).toContain('Vectis');
  });

  it('proyecta el contenido por defecto (body)', () => {
    const body = fixture.debugElement.query(By.css('.host-body'));
    expect(body).toBeTruthy();
    expect(body.nativeElement.textContent).toContain('Contenido proyectado del cuerpo.');
  });

  it('proyecta el contenido del footer dentro de .legal-footer-note', () => {
    const footer = fixture.debugElement.query(By.css('.legal-footer-note'));
    expect(footer).toBeTruthy();
    expect(footer.nativeElement.textContent).toContain('Nota de pie');
    expect(footer.nativeElement.textContent).toContain('Contacto');
  });
});
