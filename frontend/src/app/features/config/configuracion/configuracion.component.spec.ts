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
import { RecurringMovementService } from '../../../core/services/recurring-movement.service';
import { MacroService } from '../../../core/services/macro.service';
import { InvestmentService } from '../../../core/services/investment.service';
import { CategoryResponse } from '../../../core/models/category.models';
import { AccountResponse } from '../../../core/models/account.models';
import { CardResponse } from '../../../core/models/card.models';
import { RecurringMovementResponse } from '../../../core/models/recurring-movement.models';
import { ExchangeRateResponse, InflationResponse } from '../../../core/models/macro.models';
import { MarketApiStatus } from '../../../core/models/investment.models';

const MOCK_RECURRING: RecurringMovementResponse = {
  id: 'rm-1', description: 'Netflix', amount: 15000, ccy: 'ARS', type: 'EXPENSE',
  categoryId: null, categoryName: null, categoryIcon: null, categoryColor: null,
  accountId: null, accountName: null, cardId: null, cardName: null,
  dayOfMonth: 10, active: true, createdAt: '2026-06-10T00:00:00Z',
};

const MOCK_CATS: CategoryResponse[] = [
  { id: '1', name: 'Sueldo',       icon: 'briefcase',  color: '#52eacd', type: 'INCOME',  isDefault: true,  estimatedAmount: null    },
  { id: '2', name: 'Supermercado', icon: 'utensils',   color: '#ffb4ab', type: 'EXPENSE', isDefault: true,  estimatedAmount: null    },
  { id: '3', name: 'Mi gasto',     icon: 'circle',     color: '#9ed1c5', type: 'EXPENSE', isDefault: false, estimatedAmount: 30000   },
];

const MOCK_ACCOUNT: AccountResponse = {
  id: 'acc-1', name: 'Brubank', kind: 'Banco', detail: '****1234',
  ccy: 'USD', balance: 5000, computedBalance: 5000, remunerada: false, tna: null,
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

const MOCK_CARD: CardResponse = {
  id: 'card-1', bank: 'Galicia', network: 'Visa', last4: '1234',
  ccy: 'ARS', creditLimit: 500000, closingDay: 15, dueDay: 5, accent: '#52eacd',
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

const MOCK_OFICIAL: ExchangeRateResponse = {
  rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000', rateDate: '2026-06-22', source: 'dolarapi.com',
};
const MOCK_MEP: ExchangeRateResponse = {
  rateType: 'MEP', buy: '1250.0000', sell: '1255.0000', rateDate: '2026-06-22', source: 'argentinadatos.com',
};
const MOCK_IPC: InflationResponse = {
  monthlyRate: '2.4000', periodDate: '2026-05-31', source: 'argentinadatos.com',
};
const MOCK_MARKET_STATUS: MarketApiStatus = {
  fciSnapshotsTotal: 74, fciLastSync: '2026-06-25',
  ppiConfigured: true,
};

describe('ConfiguracionComponent', () => {
  let fixture: ComponentFixture<ConfiguracionComponent>;
  let component: ConfiguracionComponent;
  let catServiceSpy: jasmine.SpyObj<CategoryService>;
  let accServiceSpy: jasmine.SpyObj<AccountService>;
  let cardServiceSpy: jasmine.SpyObj<CreditCardService>;
  let recurringServiceSpy: jasmine.SpyObj<RecurringMovementService>;
  let macroServiceSpy: jasmine.SpyObj<MacroService>;
  let investmentServiceSpy: jasmine.SpyObj<InvestmentService>;

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

    recurringServiceSpy = jasmine.createSpyObj<RecurringMovementService>('RecurringMovementService', [
      'getRecurringMovements', 'createRecurringMovement', 'updateRecurringMovement',
      'toggleActive', 'deleteRecurringMovement',
    ]);
    recurringServiceSpy.getRecurringMovements.and.returnValue(of([]));

    macroServiceSpy = jasmine.createSpyObj<MacroService>('MacroService', [
      'getLatestOficialRate', 'getLatestMepRate', 'getLatestInflation',
    ]);
    macroServiceSpy.getLatestOficialRate.and.returnValue(of(MOCK_OFICIAL));
    macroServiceSpy.getLatestMepRate.and.returnValue(of(MOCK_MEP));
    macroServiceSpy.getLatestInflation.and.returnValue(of(MOCK_IPC));

    investmentServiceSpy = jasmine.createSpyObj<InvestmentService>('InvestmentService', [
      'getMarketApiStatus',
    ]);
    investmentServiceSpy.getMarketApiStatus.and.returnValue(of(MOCK_MARKET_STATUS));

    await TestBed.configureTestingModule({
      imports: [ConfiguracionComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CategoryService,          useValue: catServiceSpy          },
        { provide: AccountService,           useValue: accServiceSpy          },
        { provide: CreditCardService,        useValue: cardServiceSpy         },
        { provide: RecurringMovementService, useValue: recurringServiceSpy    },
        { provide: MacroService,             useValue: macroServiceSpy        },
        { provide: InvestmentService,        useValue: investmentServiceSpy   },
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

  it('renders five tabs', () => {
    const tabs = fixture.nativeElement.querySelectorAll('.tab');
    expect(tabs.length).toBe(5);
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
      ccy: 'USD', balance: 5000,
      includeInCashflow: true,
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

  // ── Recurring movements ────────────────────────────────────────────────────
  it('loads recurring movements on init', () => {
    expect(recurringServiceSpy.getRecurringMovements).toHaveBeenCalledOnceWith();
    expect(component.recurringMovements().length).toBe(0);
  });

  it('tabDefs includes recurrentes tab with count', () => {
    component.recurringMovements.set([MOCK_RECURRING]);
    const defs = component.tabDefs();
    expect(defs.find(t => t.id === 'recurrentes')?.count).toBe(1);
  });

  it('openCreateRecurring resets form and opens modal', () => {
    component.openCreateRecurring();
    expect(component.modal()?.kind).toBe('recurring');
    expect(component.modal()?.mode).toBe('create');
    expect(component.recurringForm.controls.description.value).toBe('');
    expect(component.recurringForm.controls.dayOfMonth.value).toBe(1);
  });

  it('openEditRecurring populates form and sets edit mode', () => {
    component.openEditRecurring(MOCK_RECURRING);
    expect(component.modal()?.kind).toBe('recurring');
    expect(component.modal()?.mode).toBe('edit');
    expect(component.recurringForm.controls.description.value).toBe('Netflix');
    expect(component.recurringForm.controls.dayOfMonth.value).toBe(10);
  });

  it('submitRecurring does not call service when form is invalid', () => {
    component.openCreateRecurring();
    component.recurringForm.controls.description.setValue('');
    component.submitRecurring();
    expect(recurringServiceSpy.createRecurringMovement).not.toHaveBeenCalled();
  });

  it('recurringForm es inválido cuando categoryId es null', () => {
    component.openCreateRecurring();
    component.recurringForm.patchValue({ categoryId: null, paymentSource: 'acc:acc-1' });
    expect(component.recurringForm.controls.categoryId.invalid).toBeTrue();
  });

  it('recurringForm es inválido cuando paymentSource está vacío', () => {
    component.openCreateRecurring();
    component.recurringForm.patchValue({ categoryId: '2', paymentSource: '' });
    expect(component.recurringForm.controls.paymentSource.invalid).toBeTrue();
  });

  it('creates a recurring movement via service', () => {
    recurringServiceSpy.createRecurringMovement.and.returnValue(of(MOCK_RECURRING));

    component.openCreateRecurring();
    component.recurringForm.setValue({
      description: 'Netflix', amount: 15000, ccy: 'ARS', type: 'EXPENSE',
      categoryId: '2', paymentSource: 'acc:acc-1', dayOfMonth: 10,
    });
    component.submitRecurring();

    expect(recurringServiceSpy.createRecurringMovement).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ description: 'Netflix', dayOfMonth: 10 })
    );
    expect(component.recurringMovements().length).toBe(1);
    expect(component.modal()).toBeNull();
  });

  it('toggleRecurring calls service and updates list', () => {
    component.recurringMovements.set([MOCK_RECURRING]);
    const toggled = { ...MOCK_RECURRING, active: false };
    recurringServiceSpy.toggleActive.and.returnValue(of(toggled));

    component.toggleRecurring(MOCK_RECURRING);

    expect(recurringServiceSpy.toggleActive).toHaveBeenCalledOnceWith('rm-1');
    expect(component.recurringMovements()[0].active).toBeFalse();
  });

  it('deletes a recurring movement via confirmDelete', () => {
    component.recurringMovements.set([MOCK_RECURRING]);
    recurringServiceSpy.deleteRecurringMovement.and.returnValue(of(void 0));

    component.openDeleteRecurring(MOCK_RECURRING);
    component.confirmDelete();

    expect(recurringServiceSpy.deleteRecurringMovement).toHaveBeenCalledOnceWith('rm-1');
    expect(component.recurringMovements().length).toBe(0);
  });

  it('shows error when recurring movements fail to load', () => {
    recurringServiceSpy.getRecurringMovements.and.returnValue(throwError(() => new Error('network')));
    component.loadRecurringMovements();
    expect(component.recurringError()).toBe('No se pudieron cargar los movimientos recurrentes');
  });

  // ── API Control tab ────────────────────────────────────────────────────────

  describe('tab API Control', () => {
    beforeEach(() => {
      component.setTab('api');
      fixture.detectChanges();
    });

    it('se puede seleccionar el tab api', () => {
      expect(component.activeTab()).toBe('api');
    });

    it('tabDefs incluye api tab con count null', () => {
      const apiTab = component.tabDefs().find(t => t.id === 'api');
      expect(apiTab).toBeTruthy();
      expect(apiTab?.label).toBe('API Control');
      expect(apiTab?.count).toBeNull();
    });

    it('oficialRate se carga con el mock', () => {
      expect(component.oficialRate()).toEqual(MOCK_OFICIAL);
    });

    it('mepRate se carga con el mock', () => {
      expect(component.mepRate()).toEqual(MOCK_MEP);
    });

    it('inflation se carga con el mock', () => {
      expect(component.inflation()).toEqual(MOCK_IPC);
    });

    it('muestra cinco badges Activo cuando todos los datos estan disponibles', () => {
      const badges = fixture.nativeElement.querySelectorAll('.macro-badge--ok');
      expect(badges.length).toBe(5);
    });

    it('muestra el sell de la cotizacion OFICIAL en el DOM formateado', () => {
      const sells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.macro-card__sell');
      expect(sells[0].textContent).toContain(component.fmtRate(MOCK_OFICIAL.sell));
    });

    it('muestra el sell de la cotizacion MEP en el DOM formateado', () => {
      const sells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.macro-card__sell');
      expect(sells[1].textContent).toContain(component.fmtRate(MOCK_MEP.sell));
    });

    it('muestra el rate de IPC en el DOM formateado', () => {
      const sells: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.macro-card__sell');
      expect(sells[2].textContent).toContain(component.fmtRate(MOCK_IPC.monthlyRate));
    });

    it('muestra el source chip de cada cotizacion', () => {
      const sources: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.macro-card__source-chip');
      expect(sources[0].textContent).toContain('dolarapi.com');
      expect(sources[1].textContent).toContain('argentinadatos.com');
      expect(sources[2].textContent).toContain('argentinadatos.com');
    });

    it('no muestra ningun badge Sin datos cuando todos los datos estan disponibles', () => {
      const errorBadges = fixture.nativeElement.querySelectorAll('.macro-badge--error');
      expect(errorBadges.length).toBe(0);
    });
  });

  it('openEditRecurring sets paymentSource to card: prefix when rm has cardId', () => {
    const rmWithCard: RecurringMovementResponse = { ...MOCK_RECURRING, cardId: 'card-42', cardName: 'Galicia ····4821' };
    component.openEditRecurring(rmWithCard);
    expect(component.recurringForm.controls.paymentSource.value).toBe('card:card-42');
  });

  it('openEditRecurring sets paymentSource to acc: prefix when rm has accountId', () => {
    const rmWithAccount: RecurringMovementResponse = { ...MOCK_RECURRING, accountId: 'acc-1', accountName: 'Santander' };
    component.openEditRecurring(rmWithAccount);
    expect(component.recurringForm.controls.paymentSource.value).toBe('acc:acc-1');
  });

  it('submitRecurring extracts cardId from paymentSource and sends to service', () => {
    const rmWithCard: RecurringMovementResponse = { ...MOCK_RECURRING, cardId: 'card-42', cardName: 'Galicia ····4821' };
    recurringServiceSpy.createRecurringMovement.and.returnValue(of(rmWithCard));

    component.openCreateRecurring();
    component.recurringForm.setValue({
      description: 'Netflix', amount: 15000, ccy: 'ARS', type: 'EXPENSE',
      categoryId: '2', paymentSource: 'card:card-42', dayOfMonth: 10,
    });
    component.submitRecurring();

    expect(recurringServiceSpy.createRecurringMovement).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ cardId: 'card-42', accountId: null })
    );
  });

  it('submitRecurring extracts accountId from paymentSource and sends to service', () => {
    recurringServiceSpy.createRecurringMovement.and.returnValue(of(MOCK_RECURRING));

    component.openCreateRecurring();
    component.recurringForm.setValue({
      description: 'Netflix', amount: 15000, ccy: 'ARS', type: 'EXPENSE',
      categoryId: '2', paymentSource: 'acc:acc-1', dayOfMonth: 10,
    });
    component.submitRecurring();

    expect(recurringServiceSpy.createRecurringMovement).toHaveBeenCalledOnceWith(
      jasmine.objectContaining({ accountId: 'acc-1', cardId: null })
    );
  });
});

describe('ConfiguracionComponent — tab API Control sin datos', () => {
  let fixture: ComponentFixture<ConfiguracionComponent>;
  let component: ConfiguracionComponent;

  beforeEach(async () => {
    const errorMacroSpy = jasmine.createSpyObj<MacroService>('MacroService', [
      'getLatestOficialRate', 'getLatestMepRate', 'getLatestInflation',
    ]);
    errorMacroSpy.getLatestOficialRate.and.returnValue(throwError(() => new Error('no data')));
    errorMacroSpy.getLatestMepRate.and.returnValue(throwError(() => new Error('no data')));
    errorMacroSpy.getLatestInflation.and.returnValue(throwError(() => new Error('no data')));

    const errorInvestmentSpy = jasmine.createSpyObj<InvestmentService>('InvestmentService', [
      'getMarketApiStatus',
    ]);
    errorInvestmentSpy.getMarketApiStatus.and.returnValue(throwError(() => new Error('no data')));

    const catSpy = jasmine.createSpyObj<CategoryService>('CategoryService', ['getCategories']);
    catSpy.getCategories.and.returnValue(of([]));
    const accSpy = jasmine.createSpyObj<AccountService>('AccountService', ['getAccounts']);
    accSpy.getAccounts.and.returnValue(of([]));
    const cardSpy = jasmine.createSpyObj<CreditCardService>('CreditCardService', ['getCards']);
    cardSpy.getCards.and.returnValue(of([]));
    const recSpy = jasmine.createSpyObj<RecurringMovementService>('RecurringMovementService', ['getRecurringMovements']);
    recSpy.getRecurringMovements.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [ConfiguracionComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CategoryService,          useValue: catSpy               },
        { provide: AccountService,           useValue: accSpy               },
        { provide: CreditCardService,        useValue: cardSpy              },
        { provide: RecurringMovementService, useValue: recSpy               },
        { provide: MacroService,             useValue: errorMacroSpy        },
        { provide: InvestmentService,        useValue: errorInvestmentSpy   },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfiguracionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.setTab('api');
    fixture.detectChanges();
  });

  it('oficialRate es null cuando el servicio falla', () => {
    expect(component.oficialRate()).toBeNull();
  });

  it('muestra tres badges Sin datos (--error) para los datos macro cuando fallan', () => {
    const errorBadges = fixture.nativeElement.querySelectorAll('.macro-badge--error');
    expect(errorBadges.length).toBe(3);
  });

  it('no muestra ningun badge Activo cuando los servicios fallan', () => {
    const okBadges = fixture.nativeElement.querySelectorAll('.macro-badge--ok');
    expect(okBadges.length).toBe(0);
  });

  it('muestra cinco mensajes de sin datos o cargando cuando todos los servicios fallan', () => {
    const empties: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.macro-card__empty');
    expect(empties.length).toBe(5);
  });
});
