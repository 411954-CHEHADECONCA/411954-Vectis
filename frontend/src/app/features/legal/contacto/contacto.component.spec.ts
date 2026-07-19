import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { ContactoComponent } from './contacto.component';

describe('ContactoComponent', () => {
  let fixture: ComponentFixture<ContactoComponent>;
  let component: ContactoComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContactoComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ContactoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('muestra el título "Contáctanos"', () => {
    const h1 = fixture.debugElement.query(By.css('h1'));
    expect(h1.nativeElement.textContent).toContain('Contáctanos');
  });

  it('renderiza un ítem de acordeón por cada FAQ', () => {
    const items = fixture.debugElement.queryAll(By.css('.faq-item'));
    expect(items.length).toBe(component.faqs.length);
  });

  it('todas las preguntas empiezan cerradas', () => {
    component.faqs.forEach((faq) => expect(component.isOpen(faq.id)).toBeFalse());
    const panels = fixture.debugElement.queryAll(By.css('.faq-panel'));
    expect(panels.length).toBe(0);
  });

  it('toggle() abre una pregunta y aria-expanded pasa a true', () => {
    const firstFaq = component.faqs[0];
    const trigger = fixture.debugElement.query(
      By.css(`#faq-trigger-${firstFaq.id}`)
    );

    trigger.nativeElement.click();
    fixture.detectChanges();

    expect(component.isOpen(firstFaq.id)).toBeTrue();
    expect(trigger.nativeElement.getAttribute('aria-expanded')).toBe('true');

    const panel = fixture.debugElement.query(By.css(`#faq-panel-${firstFaq.id}`));
    expect(panel).toBeTruthy();
    expect(panel.nativeElement.textContent).toContain(firstFaq.answer);
  });

  it('toggle() vuelve a cerrar la pregunta abierta', () => {
    const firstFaq = component.faqs[0];

    component.toggle(firstFaq.id);
    expect(component.isOpen(firstFaq.id)).toBeTrue();

    component.toggle(firstFaq.id);
    expect(component.isOpen(firstFaq.id)).toBeFalse();
  });

  it('abrir una pregunta no cierra las demás (múltiples ítems abiertos)', () => {
    const [first, second] = component.faqs;

    component.toggle(first.id);
    component.toggle(second.id);

    expect(component.isOpen(first.id)).toBeTrue();
    expect(component.isOpen(second.id)).toBeTrue();
  });

  it('muestra el enlace mailto con el correo de contacto', () => {
    const mailLink = fixture.debugElement.query(By.css('.contact-email'));
    expect(mailLink.nativeElement.getAttribute('href')).toBe(
      `mailto:${component.contactEmail}`
    );
  });

  it('incluye un enlace a /terminos', () => {
    const terminosLink = fixture.debugElement.query(
      By.css('.legal-footer-note a[href="/terminos"]')
    );
    expect(terminosLink).toBeTruthy();
  });
});
