import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategoryBadgeComponent } from './category-badge.component';
import { CategoryResponse } from '../../../core/models/category.models';
import { By } from '@angular/platform-browser';

const MOCK_CATEGORY: CategoryResponse = {
  id: 'cat-1',
  name: 'Alimentos',
  icon: 'utensils',
  color: '#10B981',
  type: 'EXPENSE',
  isDefault: true,
  estimatedAmount: null,
};

describe('CategoryBadgeComponent', () => {
  let fixture: ComponentFixture<CategoryBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CategoryBadgeComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CategoryBadgeComponent);
    fixture.componentRef.setInput('category', MOCK_CATEGORY);
  });

  it('se crea correctamente', () => {
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renderiza el nombre de la categoría', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Alimentos');
  });

  it('aplica el color del badge como color del texto en el span contenedor', () => {
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.badge') as HTMLElement;
    expect(badge.style.color).toBeTruthy();
  });

  it('aplica background-color derivado del hex de la categoría', () => {
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.badge') as HTMLElement;
    expect(badge.style.backgroundColor).toContain('rgba');
  });

  it('aplica border-color derivado del hex de la categoría', () => {
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.badge') as HTMLElement;
    expect(badge.style.borderColor).toContain('rgba');
  });

  it('renderiza el ícono SVG correspondiente al icono de la categoría', () => {
    fixture.detectChanges();
    const svg = fixture.debugElement.query(By.css('svg'));
    expect(svg).toBeTruthy();
  });

  it('muestra el badge con un ícono por defecto cuando el icon es desconocido', async () => {
    const unknownCategory: CategoryResponse = { ...MOCK_CATEGORY, icon: 'unknown-icon' };
    fixture.componentRef.setInput('category', unknownCategory);
    fixture.detectChanges();
    const svg = fixture.debugElement.query(By.css('svg'));
    expect(svg).toBeTruthy();
  });

  it('actualiza el nombre cuando cambia el input de categoría', () => {
    fixture.detectChanges();
    const updated: CategoryResponse = { ...MOCK_CATEGORY, name: 'Supermercado' };
    fixture.componentRef.setInput('category', updated);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Supermercado');
  });
});
