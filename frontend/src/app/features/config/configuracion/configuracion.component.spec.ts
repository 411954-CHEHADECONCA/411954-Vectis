import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ConfiguracionComponent } from './configuracion.component';
import { CategoryService } from '../../../core/services/category.service';
import { AccountService } from '../../../core/services/account.service';
import { CreditCardService } from '../../../core/services/credit-card.service';
import { CategoryResponse } from '../../../core/models/category.models';
import { AccountResponse } from '../../../core/models/account.models';
import { CardResponse } from '../../../core/models/card.models';

const MOCK_CATS: CategoryResponse[] = [
  { id: '1', name: 'Sueldo',       icon: 'briefcase',  color: '#52eacd', type: 'INCOME',  isDefault: true,  estimatedAmount: null    },
  { id: '2', name: 'Supermercado', icon: 'utensils',   color: '#ffb4ab', type: 'EXPENSE', isDefault: true,  estimatedAmount: null    },
  { id: '3', name: 'Mi gasto',     icon: 'circle',     color: '#9ed1c5', type: 'EXPENSE', isDefault: false, estimatedAmount: 30000   },
];

const MOCK_ACCOUNT: AccountResponse = {
  id: 'acc-1', name: 'Brubank', kind: 'Banco', detail: '****1234',
  ccy: 'USD', balance: 5000, remunerada: false, tna: null,
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

const MOCK_CARD: CardResponse = {
  id: 'card-1', bank: 'Galicia', network: 'Visa', last4: '1234',
  ccy: 'ARS', creditLimit: 500000, closingDay: 15, dueDay: 5, accent: '#52eacd',
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

describe('ConfiguracionComponent', () => {
  let fixture: ComponentFixture<ConfiguracionComponent>;
  let component: ConfiguracionComponent;
  let catServiceSpy: jasmine.SpyObj<CategoryService>;
  let accServiceSpy: jasmine.SpyObj<AccountService>;
  let cardServiceSpy: jasmine.SpyObj<CreditCardService>;

  beforeEach(async () => {
    catServiceSpy = jasmine.createSpyObj<CategoryService>('CategoryService', [
      'getCategories', 'createCategory', 'updateCategory', 'deleteCategory',
    ]);
    catServiceSpy.getCategories.and.returnValue(of([...MOCK_CATS]));

    accServiceSpy = jasmine.createSpyObj<AccountService>('AccountService', [
      'getAccounts', 'createAccount', 'updateAccount', 'deleteAccount',
    ]);
    accServiceSpy.getAccounts.and.returnValue(of([]));

    cardServiceSpy = jasmine.createSpyObj<CreditCardService>('CreditCardService', [
      'getCards', 'createCard', 'updateCard', 'deleteCard',
    ]);
    cardServiceSpy.getCards.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ConfiguracionComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CategoryService,  useValue: catServiceSpy  },
        { provide: AccountService,   useValue: accServiceSpy  },
        { provide: CreditCardService, useValue: cardServiceSpy },
      ],
    }).compileComponents();

    fixture   = TestBed.createComponent(ConfiguracionComponent);
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

  it('loads accounts on init', () => {
    expect(accServiceSpy.getAccounts).toHaveBeenCalledOnceWith();
  });

  it('loads cards on init', () => {
    expect(cardServiceSpy.getCards).toHaveBeenCalledOnceWith();
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

  // ── Account CRUD (backend) ─────────────────────────────────────────────────
  it('creates an account via service', () => {
    accServiceSpy.createAccount.and.returnValue(of(MOCK_ACCOUNT));

    component.openCreateAccount();
    component.accountForm.setValue({
      name: 'Brubank', kind: 'Banco', detail: '****1234',
      ccy: 'USD', balance: 5000, remunerada: false, tna: null,
    });
    component.submitAccount();

    expect(accServiceSpy.createAccount).toHaveBeenCalledOnceWith(jasmine.objectContaining({ name: 'Brubank' }));
    expect(component.accounts().length).toBe(1);
    expect(component.modal()).toBeNull();
  });

  it('updates an account via service', () => {
    component.accounts.set([MOCK_ACCOUNT]);
    const updated = { ...MOCK_ACCOUNT, name: 'New name' };
    accServiceSpy.updateAccount.and.returnValue(of(updated));

    component.openEditAccount(MOCK_ACCOUNT);
    component.accountForm.controls.name.setValue('New name');
    component.submitAccount();

    expect(accServiceSpy.updateAccount).toHaveBeenCalledOnceWith('acc-1', jasmine.objectContaining({ name: 'New name' }));
    expect(component.accounts()[0].name).toBe('New name');
  });

  it('deletes an account via service', () => {
    component.accounts.set([MOCK_ACCOUNT]);
    accServiceSpy.deleteAccount.and.returnValue(of(void 0));

    component.openDeleteAccount(MOCK_ACCOUNT);
    component.confirmDeleteAccount();

    expect(accServiceSpy.deleteAccount).toHaveBeenCalledOnceWith('acc-1');
    expect(component.accounts().length).toBe(0);
  });

  // ── Card CRUD (backend) ───────────────────────────────────────────────────
  it('creates a card via service', () => {
    cardServiceSpy.createCard.and.returnValue(of(MOCK_CARD));

    component.openCreateCard();
    component.cardForm.setValue({
      bank: 'Galicia', network: 'Visa', last4: '1234',
      ccy: 'ARS', creditLimit: 500000, closingDay: 15, dueDay: 5, accent: '#52eacd',
    });
    component.submitCard();

    expect(cardServiceSpy.createCard).toHaveBeenCalledOnceWith(jasmine.objectContaining({ bank: 'Galicia' }));
    expect(component.cards().length).toBe(1);
    expect(component.modal()).toBeNull();
  });

  it('updates a card via service', () => {
    component.cards.set([MOCK_CARD]);
    const updated = { ...MOCK_CARD, bank: 'Santander' };
    cardServiceSpy.updateCard.and.returnValue(of(updated));

    component.openEditCard(MOCK_CARD);
    component.cardForm.controls.bank.setValue('Santander');
    component.submitCard();

    expect(cardServiceSpy.updateCard).toHaveBeenCalledOnceWith('card-1', jasmine.objectContaining({ bank: 'Santander' }));
    expect(component.cards()[0].bank).toBe('Santander');
  });

  it('deletes a card via service', () => {
    component.cards.set([MOCK_CARD]);
    cardServiceSpy.deleteCard.and.returnValue(of(void 0));

    component.openDeleteCard(MOCK_CARD);
    component.confirmDeleteCard();

    expect(cardServiceSpy.deleteCard).toHaveBeenCalledOnceWith('card-1');
    expect(component.cards().length).toBe(0);
  });

  it('shows error message when card service fails on load', () => {
    cardServiceSpy.getCards.and.returnValue(throwError(() => new Error('network')));
    component.loadCards();
    expect(component.cardsError()).toBe('No se pudieron cargar las tarjetas');
  });

  // ── Category CRUD (backend) ────────────────────────────────────────────────
  it('openCreateCategory INCOME pre-selects tipo ingreso', () => {
    component.openCreateCategory('INCOME');
    expect(component.categoryForm.controls.type.value).toBe('INCOME');
  });

  it('openCreateCategory EXPENSE pre-selects tipo egreso', () => {
    component.openCreateCategory('EXPENSE');
    expect(component.categoryForm.controls.type.value).toBe('EXPENSE');
  });

  it('creates a category via service', () => {
    const newCat: CategoryResponse = { id: '99', name: 'Nueva', icon: 'circle', color: '#52eacd', type: 'EXPENSE', isDefault: false, estimatedAmount: null };
    catServiceSpy.createCategory.and.returnValue(of(newCat));

    component.openCreateCategory();
    component.categoryForm.setValue({ name: 'Nueva', type: 'EXPENSE', icon: 'circle', color: '#52eacd', estimatedAmount: null });
    component.submitCategory();

    expect(catServiceSpy.createCategory).toHaveBeenCalledOnceWith({ name: 'Nueva', type: 'EXPENSE', icon: 'circle', color: '#52eacd', estimatedAmount: null });
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

  it('openEditCategory loads estimatedAmount into form', () => {
    component.openEditCategory(MOCK_CATS[2]);
    expect(component.categoryForm.controls.estimatedAmount.value).toBe(30000);
  });

  it('shows error message when category service fails on load', () => {
    catServiceSpy.getCategories.and.returnValue(throwError(() => new Error('network')));
    component.loadCategories();
    expect(component.catError()).toBe('No se pudieron cargar las categorías');
  });

  it('shows error message when account service fails on load', () => {
    accServiceSpy.getAccounts.and.returnValue(throwError(() => new Error('network')));
    component.loadAccounts();
    expect(component.accError()).toBe('No se pudieron cargar las cuentas');
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
    component.categoryForm.setValue({ name: 'Viajes', type: 'EXPENSE', icon: 'car', color: '#e8c37a', estimatedAmount: null });
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
