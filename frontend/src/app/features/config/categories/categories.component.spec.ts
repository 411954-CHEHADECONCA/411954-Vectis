import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { CategoriesComponent } from './categories.component';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse } from '../../../core/models/category.models';

const MOCK_CATEGORIES: CategoryResponse[] = [
  { id: 'cat-1', name: 'Alimentos',  icon: 'utensils',   color: '#10B981', type: 'EXPENSE', isDefault: true  },
  { id: 'cat-2', name: 'Sueldo',     icon: 'briefcase',  color: '#10B981', type: 'INCOME',  isDefault: true  },
  { id: 'cat-3', name: 'Mi ahorro',  icon: 'circle',     color: '#3B82F6', type: 'BOTH',    isDefault: false },
];

describe('CategoriesComponent', () => {
  let fixture: ComponentFixture<CategoriesComponent>;
  let component: CategoriesComponent;
  let serviceSpy: jasmine.SpyObj<CategoryService>;

  beforeEach(async () => {
    serviceSpy = jasmine.createSpyObj('CategoryService', [
      'getCategories',
      'createCategory',
      'updateCategory',
      'deleteCategory',
    ]);
    serviceSpy.getCategories.and.returnValue(of(MOCK_CATEGORIES));

    await TestBed.configureTestingModule({
      imports: [CategoriesComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: CategoryService, useValue: serviceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CategoriesComponent);
    component = fixture.componentInstance;
  });

  it('se crea correctamente', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('carga categorías al iniciar', () => {
    fixture.detectChanges();
    expect(serviceSpy.getCategories).toHaveBeenCalled();
    expect(component.categories().length).toBe(3);
  });

  it('muestra error cuando falla la carga', () => {
    serviceSpy.getCategories.and.returnValue(throwError(() => new Error('network')));
    fixture.detectChanges();
    expect(component.error()).toBeTruthy();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No se pudieron cargar');
  });

  it('agrupa categorías por tipo', () => {
    fixture.detectChanges();
    const groups = component.groups();
    const types = groups.map(g => g.type);
    expect(types).toContain('EXPENSE');
    expect(types).toContain('INCOME');
    expect(types).toContain('BOTH');
  });

  it('abre el modal de creación al pulsar "Nueva categoría"', () => {
    fixture.detectChanges();
    component.openCreate();
    fixture.detectChanges();
    expect(component.modalOpen()).toBeTrue();
    expect(component.editingId()).toBeNull();
  });

  it('abre el modal de edición con los datos de la categoría', () => {
    fixture.detectChanges();
    const cat = MOCK_CATEGORIES[2];
    component.openEdit(cat);
    fixture.detectChanges();
    expect(component.modalOpen()).toBeTrue();
    expect(component.editingId()).toBe(cat.id);
    expect(component.form.controls.name.value).toBe(cat.name);
    expect(component.form.controls.color.value).toBe(cat.color);
    expect(component.form.controls.icon.value).toBe(cat.icon);
    expect(component.form.controls.type.value).toBe(cat.type);
  });

  it('cierra el modal correctamente', () => {
    fixture.detectChanges();
    component.openCreate();
    component.closeModal();
    expect(component.modalOpen()).toBeFalse();
    expect(component.editingId()).toBeNull();
  });

  it('llama a createCategory al enviar un nuevo formulario', fakeAsync(() => {
    const newCat: CategoryResponse = { id: 'cat-4', name: 'Gym', icon: 'dumbbell', color: '#EF4444', type: 'EXPENSE', isDefault: false };
    serviceSpy.createCategory.and.returnValue(of(newCat));
    fixture.detectChanges();

    component.openCreate();
    component.form.setValue({ name: 'Gym', type: 'EXPENSE', icon: 'dumbbell', color: '#EF4444' });
    component.submit();
    tick();
    fixture.detectChanges();

    expect(serviceSpy.createCategory).toHaveBeenCalledWith({ name: 'Gym', type: 'EXPENSE', icon: 'dumbbell', color: '#EF4444' });
    expect(component.categories().some(c => c.id === 'cat-4')).toBeTrue();
    expect(component.modalOpen()).toBeFalse();
  }));

  it('llama a updateCategory al editar una categoría existente', fakeAsync(() => {
    const cat = MOCK_CATEGORIES[2];
    const updated: CategoryResponse = { ...cat, name: 'Ahorro actualizado' };
    serviceSpy.updateCategory.and.returnValue(of(updated));
    fixture.detectChanges();

    component.openEdit(cat);
    component.form.controls.name.setValue('Ahorro actualizado');
    component.submit();
    tick();
    fixture.detectChanges();

    expect(serviceSpy.updateCategory).toHaveBeenCalledWith(cat.id, jasmine.objectContaining({ name: 'Ahorro actualizado' }));
    expect(component.categories().find(c => c.id === cat.id)?.name).toBe('Ahorro actualizado');
  }));

  it('no hace submit si el formulario es inválido', () => {
    fixture.detectChanges();
    component.openCreate();
    component.form.controls.name.setValue('');
    component.submit();
    expect(serviceSpy.createCategory).not.toHaveBeenCalled();
  });

  it('establece deleteTarget al pedir confirmación de borrado', () => {
    fixture.detectChanges();
    const cat = MOCK_CATEGORIES[2];
    component.requestDelete(cat);
    expect(component.deleteTarget()).toEqual(cat);
  });

  it('cancela el borrado correctamente', () => {
    fixture.detectChanges();
    component.requestDelete(MOCK_CATEGORIES[2]);
    component.cancelDelete();
    expect(component.deleteTarget()).toBeNull();
  });

  it('llama a deleteCategory y elimina de la lista', fakeAsync(() => {
    serviceSpy.deleteCategory.and.returnValue(of(undefined));
    fixture.detectChanges();
    const cat = MOCK_CATEGORIES[2];
    component.requestDelete(cat);
    component.confirmDelete();
    tick();
    fixture.detectChanges();

    expect(serviceSpy.deleteCategory).toHaveBeenCalledWith(cat.id);
    expect(component.categories().some(c => c.id === cat.id)).toBeFalse();
    expect(component.deleteTarget()).toBeNull();
  }));

  it('previewCategory refleja los valores actuales del formulario', () => {
    fixture.detectChanges();
    component.openCreate();
    component.form.setValue({ name: 'Salidas', type: 'EXPENSE', icon: 'music', color: '#EC4899' });
    fixture.detectChanges();

    const preview = component.previewCategory();
    expect(preview.name).toBe('Salidas');
    expect(preview.icon).toBe('music');
    expect(preview.color).toBe('#EC4899');
  });
});
