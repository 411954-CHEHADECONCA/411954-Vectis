import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ConfiguracionComponent, AccountLocal, CardLocal } from './configuracion.component';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse } from '../../../core/models/category.models';

const MOCK_CATS: CategoryResponse[] = [
  { id: '1', name: 'Sueldo',       icon: 'briefcase',  color: '#52eacd', type: 'INCOME',  isDefault: true  },
  { id: '2', name: 'Supermercado', icon: 'utensils',   color: '#ffb4ab', type: 'EXPENSE', isDefault: true  },
  { id: '3', name: 'Mi gasto',     icon: 'circle',     color: '#9ed1c5', type: 'EXPENSE', isDefault: false },
];

describe('ConfiguracionComponent', () => {
  let fixture: ComponentFixture<ConfiguracionComponent>;
  let component: ConfiguracionComponent;
  let catServiceSpy: jasmine.SpyObj<CategoryService>;

  beforeEach(async () => {
    catServiceSpy = jasmine.createSpyObj<CategoryService>('CategoryService', [
      'getCategories', 'createCategory', 'updateCategory', 'deleteCategory',
    ]);
    catServiceSpy.getCategories.and.returnValue(of([...MOCK_CATS]));

    await TestBed.configureTestingModule({
      imports: [ConfiguracionComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CategoryService, useValue: catServiceSpy },
      ],
    }).compileComponents();

    fixture  = TestBed.createComponent(ConfiguracionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ── Initialisation ─────────────────────────────────────────────────────────
  it('renders the page title', () => {
    const h1: HTMLElement = fixture.nativeElement.querySelector('.page-head__title');
    expect(h1.textContent).toContain('Configuración');
  });

  it('loads categories on init', () => {
    expect(catServiceSpy.getCategories).toHaveBeenCalledOnceWith();
    expect(component.categories().length).toBe(3);
  });

  it('renders three tabs', () => {
    const tabs = fixture.nativeElement.querySelectorAll('.tab');
    expect(tabs.length).toBe(3);
  });

  // ── Tab switching ──────────────────────────────────────────────────────────
  it('starts on cuentas tab', () => {
    expect(component.activeTab()).toBe('cuentas');
  });

  it('switches tab on click', () => {
    component.setTab('tarjetas');
    expect(component.activeTab()).toBe('tarjetas');
    component.setTab('categorias');
    expect(component.activeTab()).toBe('categorias');
  });

  // ── Computed splits ────────────────────────────────────────────────────────
  it('splits categories into ingresos and egresos', () => {
    expect(component.ingresos().length).toBe(1);
    expect(component.egresos().length).toBe(2);
  });

  it('tabDefs reflect current counts', () => {
    const defs = component.tabDefs();
    expect(defs.find(t => t.id === 'categorias')?.count).toBe(3);
    expect(defs.find(t => t.id === 'cuentas')?.count).toBe(0);
  });

  // ── Account CRUD (local state) ─────────────────────────────────────────────
  it('adds an account to local state on submit', () => {
    component.openCreateAccount();
    component.accountForm.setValue({ name: 'Brubank', kind: 'Banco', detail: '****1234', ccy: 'USD', balance: 5000 });
    component.submitAccount();
    expect(component.accounts().length).toBe(1);
    expect(component.accounts()[0].name).toBe('Brubank');
    expect(component.modal()).toBeNull();
  });

  it('edits an existing account', () => {
    component.openCreateAccount();
    component.accountForm.setValue({ name: 'Old name', kind: 'Banco', detail: '', ccy: 'ARS', balance: 0 });
    component.submitAccount();

    const acc = component.accounts()[0];
    component.openEditAccount(acc);
    component.accountForm.controls.name.setValue('New name');
    component.submitAccount();

    expect(component.accounts()[0].name).toBe('New name');
  });

  it('deletes an account after confirmation', () => {
    component.openCreateAccount();
    component.accountForm.setValue({ name: 'To delete', kind: 'Banco', detail: '', ccy: 'ARS', balance: 0 });
    component.submitAccount();

    const acc = component.accounts()[0];
    component.openDeleteAccount(acc);
    component.confirmDeleteAccount();

    expect(component.accounts().length).toBe(0);
  });

  // ── Card CRUD (local state) ────────────────────────────────────────────────
  it('adds a card to local state on submit', () => {
    component.openCreateCard();
    component.cardForm.setValue({ bank: 'Galicia', network: 'Visa', last4: '4821', ccy: 'ARS', limit: 1500000, closing: '24 jun', due: '02 jul', accent: '#52eacd' });
    component.submitCard();
    expect(component.cards().length).toBe(1);
    expect(component.cards()[0].bank).toBe('Galicia');
  });

  it('deletes a card after confirmation', () => {
    component.openCreateCard();
    component.cardForm.setValue({ bank: 'X', network: 'Visa', last4: '0000', ccy: 'ARS', limit: 0, closing: '', due: '', accent: '#52eacd' });
    component.submitCard();

    const card = component.cards()[0];
    component.openDeleteCard(card);
    component.confirmDeleteCard();

    expect(component.cards().length).toBe(0);
  });

  // ── Category CRUD (backend) ────────────────────────────────────────────────
  it('creates a category via service', () => {
    const newCat: CategoryResponse = { id: '99', name: 'Nueva', icon: 'circle', color: '#52eacd', type: 'EXPENSE', isDefault: false };
    catServiceSpy.createCategory.and.returnValue(of(newCat));

    component.openCreateCategory();
    component.categoryForm.setValue({ name: 'Nueva', type: 'EXPENSE', icon: 'circle', color: '#52eacd' });
    component.submitCategory();

    expect(catServiceSpy.createCategory).toHaveBeenCalledOnceWith({ name: 'Nueva', type: 'EXPENSE', icon: 'circle', color: '#52eacd' });
    expect(component.categories().length).toBe(4);
    expect(component.modal()).toBeNull();
  });

  it('updates a category via service', () => {
    const updated: CategoryResponse = { ...MOCK_CATS[2], name: 'Renamed' };
    catServiceSpy.updateCategory.and.returnValue(of(updated));

    component.openEditCategory(MOCK_CATS[2]);
    component.categoryForm.controls.name.setValue('Renamed');
    component.submitCategory();

    expect(catServiceSpy.updateCategory).toHaveBeenCalledOnceWith('3', jasmine.objectContaining({ name: 'Renamed' }));
    expect(component.categories().find(c => c.id === '3')?.name).toBe('Renamed');
  });

  it('deletes a category via service', () => {
    catServiceSpy.deleteCategory.and.returnValue(of(void 0));

    component.openDeleteCategory(MOCK_CATS[2]);
    component.confirmDeleteCategory();

    expect(catServiceSpy.deleteCategory).toHaveBeenCalledOnceWith('3');
    expect(component.categories().find(c => c.id === '3')).toBeUndefined();
  });

  it('shows error message when category service fails on load', () => {
    catServiceSpy.getCategories.and.returnValue(throwError(() => new Error('network')));
    component.loadCategories();
    expect(component.catError()).toBe('No se pudieron cargar las categorías');
  });

  // ── Modal state ────────────────────────────────────────────────────────────
  it('opens and closes modal correctly', () => {
    component.openCreateAccount();
    expect(component.modal()?.kind).toBe('account');
    expect(component.modal()?.mode).toBe('create');

    component.closeModal();
    expect(component.modal()).toBeNull();
  });

  // ── Preview category ───────────────────────────────────────────────────────
  it('updates preview category reactively', () => {
    component.openCreateCategory();
    component.categoryForm.setValue({ name: 'Viajes', type: 'EXPENSE', icon: 'car', color: '#e8c37a' });
    expect(component.previewCategory().name).toBe('Viajes');
    expect(component.previewCategory().icon).toBe('car');
    expect(component.previewCategory().color).toBe('#e8c37a');
  });

  // ── fmtAmount ──────────────────────────────────────────────────────────────
  it('formats ARS amounts with $ prefix', () => {
    expect(component.fmtAmount(1200000, 'ARS')).toContain('$');
  });

  it('formats USD amounts with US$ prefix', () => {
    expect(component.fmtAmount(5000, 'USD')).toContain('US$');
  });
});
