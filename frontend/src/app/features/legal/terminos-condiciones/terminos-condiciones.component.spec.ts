import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { TerminosCondicionesComponent } from './terminos-condiciones.component';

describe('TerminosCondicionesComponent', () => {
  let fixture: ComponentFixture<TerminosCondicionesComponent>;
  let component: TerminosCondicionesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TerminosCondicionesComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(TerminosCondicionesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('se crea correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('muestra el título "Términos y Condiciones"', () => {
    const h1 = fixture.debugElement.query(By.css('h1'));
    expect(h1.nativeElement.textContent).toContain('Términos y Condiciones');
  });

  it('muestra la fecha de última actualización', () => {
    const meta = fixture.debugElement.query(By.css('.page-meta'));
    expect(meta.nativeElement.textContent).toContain('12 de julio de 2026');
  });

  it('el índice lista todas las secciones definidas', () => {
    const indexLinks = fixture.debugElement.queryAll(By.css('.legal-index a'));
    expect(indexLinks.length).toBe(component.sections.length);

    component.sections.forEach((section, i) => {
      expect(indexLinks[i].nativeElement.textContent.trim()).toBe(section.title);
      // RouterLink + fragment resuelve el href contra la ruta activa, así que se
      // valida el fragmento y no la base (que en test es '/' y en la app '/terminos').
      // Un href crudo '#id' se resolvería contra <base href="/"> y sacaría al
      // usuario de la página, redirigiéndolo al login.
      expect(indexLinks[i].nativeElement.getAttribute('href')).toMatch(
        new RegExp(`#${section.id}$`),
      );
    });
  });

  it('renderiza cada sección con su id como anchor', () => {
    component.sections.forEach((section) => {
      const el = fixture.debugElement.query(By.css(`#${section.id}`));
      expect(el).withContext(`section #${section.id} debe existir`).toBeTruthy();
    });
  });

  it('renderiza todos los párrafos de cada sección', () => {
    const totalParagraphs = component.sections.reduce(
      (acc, s) => acc + s.paragraphs.length,
      0
    );
    const renderedParagraphs = fixture.debugElement.queryAll(By.css('.legal-section p'));
    expect(renderedParagraphs.length).toBe(totalParagraphs);
  });

  it('el pie incluye un enlace mailto a chehadesantiago@gmail.com', () => {
    const mailLink = fixture.debugElement.query(
      By.css('.legal-footer-note a[href="mailto:chehadesantiago@gmail.com"]')
    );
    expect(mailLink).toBeTruthy();
  });

  it('el pie incluye un enlace a la sección Contáctanos (/contacto)', () => {
    const contactLink = fixture.debugElement.query(
      By.css('.legal-footer-note a[routerLink="/contacto"]')
    );
    expect(contactLink).toBeTruthy();
    expect(contactLink.nativeElement.textContent.trim()).toBe('Contáctanos');
  });
});
