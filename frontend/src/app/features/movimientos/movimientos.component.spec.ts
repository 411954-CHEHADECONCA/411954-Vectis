import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { MovimientosComponent } from './movimientos.component';
import { MovementService } from '../../core/services/movement.service';
import { CategoryService } from '../../core/services/category.service';
import { AccountService } from '../../core/services/account.service';
import { CreditCardService } from '../../core/services/credit-card.service';
import { CardProjectionService } from '../../core/services/card-projection.service';
import { TransferService } from '../../core/services/transfer.service';
import { MovementResponse } from '../../core/models/movement.models';
import { PageResponse } from '../../core/models/pagination.models';
import { CategoryResponse } from '../../core/models/category.models';
import { AccountResponse } from '../../core/models/account.models';
import { CardResponse } from '../../core/models/card.models';
import { CardFace, CardOverview } from '../../core/models/card-projection.models';

const MOCK_CATS: CategoryResponse[] = [
  { id: '1', name: 'Sueldo',       icon: 'briefcase', color: '#52eacd', type: 'INCOME',  isDefault: true,  estimatedAmount: null },
  { id: '2', name: 'Supermercado', icon: 'utensils',  color: '#ffb4ab', type: 'EXPENSE', isDefault: true,  estimatedAmount: null },
];

const MOCK_ACCOUNT: AccountResponse = {
  id: 'acc-1', name: 'Galicia', kind: 'Banco', detail: null,
  ccy: 'ARS', balance: 0, computedBalance: 0, remunerada: false, tna: null,
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

const MOCK_CARD: CardResponse = {
  id: 'card-1', bank: 'Galicia', network: 'Visa', last4: '4821',
  ccy: 'ARS', creditLimit: 500000, closingDay: 5, dueDay: 15, accent: '#52eacd',
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

const MOCK_CARD_FACE: CardFace = {
  id: 'card-1', bank: 'Galicia', network: 'Visa', last4: '4821',
  ccy: 'ARS', accent: '#52eacd', creditLimit: 500000, consumido: 50000,
  nextClosingDate: '2026-07-05', nextDueDate: '2026-07-15',
};

const MOCK_OVERVIEW: CardOverview = {
  cards: [MOCK_CARD_FACE], totalDue: 50000,
  nextDueCardName: 'Galicia ····4821', nextDueDate: '2026-07-15', nextDueAmount: 50000,
  cuotasActivas: [], vencimientos: [],
};

const MOCK_MOVEMENT: MovementResponse = {
  id: 'mv-1', type: 'EXPENSE', description: 'Coto', amount: 86400, ccy: 'ARS',
  categoryId: '2', categoryName: 'Supermercado', categoryIcon: 'utensils', categoryColor: '#ffb4ab',
  accountId: 'acc-1', accountName: 'Galicia', cardId: null, cardName: null,
  transactionDate: '2026-06-09', dueDate: '2026-06-09',
  installment: false, installmentNumber: null, totalInstallments: null,
  installmentGroupId: null, isProjected: false, paid: false, createdAt: '2026-06-09T00:00:00Z',
  transferGroupId: null,
  exchangeRateAtTime: null,
};

const MOCK_INSTALLMENT: MovementResponse = {
  ...MOCK_MOVEMENT, id: 'mv-2', description: 'Notebook — cuota 3/6', cardId: 'card-1', cardName: 'Galicia ····4821',
  accountId: null, accountName: null, installment: true, installmentNumber: 3, totalInstallments: 6,
  installmentGroupId: 'grp-1',
};

function pageOf(items: MovementResponse[]): PageResponse<MovementResponse> {
  return { content: items, page: 0, size: 20, totalElements: items.length, totalPages: 1, hasNext: false };
}

describe('MovimientosComponent', () => {
  let fixture: ComponentFixture<MovimientosComponent>;
  let component: MovimientosComponent;
  let movServiceSpy: jasmine.SpyObj<MovementService>;
  let catServiceSpy: jasmine.SpyObj<CategoryService>;
  let accServiceSpy: jasmine.SpyObj<AccountService>;
  let cardServiceSpy: jasmine.SpyObj<CreditCardService>;
  let cardProjectionSpy: jasmine.SpyObj<CardProjectionService>;
  let transferServiceSpy: jasmine.SpyObj<TransferService>;

  beforeEach(async () => {
    movServiceSpy = jasmine.createSpyObj<MovementService>('MovementService',
      ['search', 'summary', 'create', 'update', 'updateGroup', 'delete']);
    movServiceSpy.search.and.returnValue(of(pageOf([MOCK_MOVEMENT])));
    movServiceSpy.summary.and.returnValue(of({ totalIncome: 0, totalExpense: 86400, net: -86400, count: 1 }));
    movServiceSpy.create.and.returnValue(of([MOCK_MOVEMENT]));
    movServiceSpy.update.and.returnValue(of(MOCK_MOVEMENT));
    movServiceSpy.updateGroup.and.returnValue(of([MOCK_INSTALLMENT]));
    movServiceSpy.delete.and.returnValue(of(void 0));

    catServiceSpy = jasmine.createSpyObj<CategoryService>('CategoryService', ['getCategories']);
    catServiceSpy.getCategories.and.returnValue(of([...MOCK_CATS]));

    accServiceSpy = jasmine.createSpyObj<AccountService>('AccountService', ['getAccounts']);
    accServiceSpy.getAccounts.and.returnValue(of([MOCK_ACCOUNT]));

    cardServiceSpy = jasmine.createSpyObj<CreditCardService>('CreditCardService', ['getCards']);
    cardServiceSpy.getCards.and.returnValue(of([MOCK_CARD]));

    cardProjectionSpy = jasmine.createSpyObj<CardProjectionService>('CardProjectionService', ['getOverview']);
    cardProjectionSpy.getOverview.and.returnValue(of(MOCK_OVERVIEW));

    transferServiceSpy = jasmine.createSpyObj<TransferService>('TransferService', ['create', 'delete']);
    transferServiceSpy.create.and.returnValue(of({} as any));
    transferServiceSpy.delete.and.returnValue(of(void 0));

    await TestBed.configureTestingModule({
      imports: [MovimientosComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MovementService,       useValue: movServiceSpy       },
        { provide: CategoryService,       useValue: catServiceSpy       },
        { provide: AccountService,        useValue: accServiceSpy       },
        { provide: CreditCardService,     useValue: cardServiceSpy      },
        { provide: CardProjectionService, useValue: cardProjectionSpy   },
        { provide: TransferService,       useValue: transferServiceSpy  },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      ],
    }).compileComponents();

    fixture   = TestBed.createComponent(MovimientosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates and loads the current month by default', () => {
    expect(component).toBeTruthy();
    const args = movServiceSpy.search.calls.mostRecent().args[0];
    expect(args.from.slice(0, 7)).toBe(args.to.slice(0, 7)); // mismo mes
    expect(args.from.endsWith('-01')).toBeTrue();
    expect(component.page()?.content.length).toBe(1);
  });

  it('switching type to INCOME clears a selected card in the form', () => {
    component.openCreate();
    component.movementForm.patchValue({ type: 'EXPENSE', paymentSource: 'card:card-1' });
    expect(component.showInstallments()).toBeTrue();

    component.movementForm.controls.type.setValue('INCOME');
    component.onTypeOrSourceChange();

    expect(component.movementForm.controls.paymentSource.value).toBe('');
  });

  it('selecting a card on an expense enables installments', () => {
    component.openCreate();
    component.movementForm.patchValue({ type: 'EXPENSE', paymentSource: 'card:card-1' });
    component.onTypeOrSourceChange();
    expect(component.showInstallments()).toBeTrue();

    component.movementForm.patchValue({ paymentSource: 'acc:acc-1' });
    component.onTypeOrSourceChange();
    expect(component.showInstallments()).toBeFalse();
  });

  it('submit parses paymentSource and sends installments to the service', () => {
    component.openCreate();
    component.movementForm.setValue({
      description: 'Notebook', type: 'EXPENSE', categoryId: '2', paymentSource: 'card:card-1',
      ccy: 'ARS', amount: 60000, transactionDate: '2026-04-07', installments: 3,
      destAccountId: null, destAmount: null,
    });
    component.submit();

    expect(movServiceSpy.create).toHaveBeenCalled();
    const req = movServiceSpy.create.calls.mostRecent().args[0];
    expect(req.cardId).toBe('card-1');
    expect(req.accountId).toBeNull();
    expect(req.installments).toBe(3);
  });

  it('submit with account sends accountId and installments = 1', () => {
    component.openCreate();
    component.movementForm.setValue({
      description: 'Sueldo', type: 'INCOME', categoryId: '1', paymentSource: 'acc:acc-1',
      ccy: 'ARS', amount: 1240000, transactionDate: '2026-06-10', installments: 1,
      destAccountId: null, destAmount: null,
    });
    component.submit();

    const req = movServiceSpy.create.calls.mostRecent().args[0];
    expect(req.accountId).toBe('acc-1');
    expect(req.cardId).toBeNull();
    expect(req.installments).toBe(1);
  });

  it('openEdit opens a restricted modal for an installment row with the base description', () => {
    component.openEdit(MOCK_INSTALLMENT);
    const m = component.modal();
    expect(m).not.toBeNull();
    expect(m!.isInstallment).toBeTrue();
    expect(m!.installmentGroupId).toBe('grp-1');
    expect(component.movementForm.controls.description.value).toBe('Notebook');
  });

  it('submit calls updateGroup for installment edits and reloads', () => {
    component.openEdit(MOCK_INSTALLMENT);
    component.movementForm.patchValue({ description: 'Laptop', categoryId: '2' });
    component.submit();

    expect(movServiceSpy.updateGroup).toHaveBeenCalledWith('grp-1', { description: 'Laptop', categoryId: '2' });
    expect(movServiceSpy.search).toHaveBeenCalled();
  });

  it('confirmDelete calls the service and reloads', () => {
    component.openDelete(MOCK_MOVEMENT);
    movServiceSpy.search.calls.reset();
    component.confirmDelete();

    expect(movServiceSpy.delete).toHaveBeenCalledWith('mv-1');
    expect(movServiceSpy.search).toHaveBeenCalled();
  });

  it('type filter resets to first page and reloads with the type', () => {
    component.pageIndex.set(2);
    component.onTypeFilter('INCOME');

    expect(component.pageIndex()).toBe(0);
    const args = movServiceSpy.search.calls.mostRecent().args[0];
    expect(args.type).toBe('INCOME');
  });

  it('blocks submit when amount exceeds available card credit', () => {
    // card-1 tiene creditLimit=500000 y consumido=50000 → disponible=450000
    component.openCreate();
    component.movementForm.setValue({
      description: 'Compra grande', type: 'EXPENSE', categoryId: '2', paymentSource: 'card:card-1',
      ccy: 'ARS', amount: 500000, transactionDate: '2026-06-12', installments: 1,
      destAccountId: null, destAmount: null,
    });

    component.submit();

    expect(movServiceSpy.create).not.toHaveBeenCalled();
    expect(component.creditLimitError()).toContain('supera el saldo disponible');
  });

  it('allows submit when amount is within available credit', () => {
    component.openCreate();
    component.movementForm.setValue({
      description: 'Compra ok', type: 'EXPENSE', categoryId: '2', paymentSource: 'card:card-1',
      ccy: 'ARS', amount: 100000, transactionDate: '2026-06-12', installments: 1,
      destAccountId: null, destAmount: null,
    });

    component.submit();

    expect(movServiceSpy.create).toHaveBeenCalled();
  });

  it('warns when amount exceeds account balance but still allows submit', () => {
    // MOCK_ACCOUNT tiene computedBalance: 0 → advertencia visible pero no bloquea
    component.openCreate();
    component.movementForm.setValue({
      description: 'Mercado', type: 'EXPENSE', categoryId: '2',
      paymentSource: 'acc:acc-1', ccy: 'ARS', amount: 1000,
      transactionDate: '2026-06-18', installments: 1,
      destAccountId: null, destAmount: null,
    });
    expect(component.accountBalanceWarning()).toContain('Saldo insuficiente');
    component.submit();
    expect(movServiceSpy.create).toHaveBeenCalled();
  });

  it('allows submit when amount is within account balance', async () => {
    accServiceSpy.getAccounts.and.returnValue(of([{ ...MOCK_ACCOUNT, computedBalance: 200000 }]));
    // Recrear componente para que reciba la nueva lista de cuentas
    fixture = TestBed.createComponent(MovimientosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.openCreate();
    component.movementForm.setValue({
      description: 'Mercado', type: 'EXPENSE', categoryId: '2',
      paymentSource: 'acc:acc-1', ccy: 'ARS', amount: 50000,
      transactionDate: '2026-06-18', installments: 1,
      destAccountId: null, destAmount: null,
    });
    component.submit();
    expect(movServiceSpy.create).toHaveBeenCalled();
  });

  it('switching type to TRANSFER hides category and shows transfer fields', () => {
    component.openCreate();
    component.movementForm.controls.type.setValue('TRANSFER');
    component.onTypeOrSourceChange();

    expect(component.isTransfer()).toBeTrue();
    expect(component.showInstallments()).toBeFalse();
    expect(component.movementForm.controls.destAccountId.value).toBeNull();
  });

  it('submit with type TRANSFER calls transferService.create, not movementService.create', () => {
    const MOCK_ACCOUNT_2: AccountResponse = {
      ...MOCK_ACCOUNT, id: 'acc-2', name: 'Mercado Pago',
    };
    accServiceSpy.getAccounts.and.returnValue(of([MOCK_ACCOUNT, MOCK_ACCOUNT_2]));
    fixture = TestBed.createComponent(MovimientosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.openCreate();
    component.movementForm.setValue({
      description: 'Recarga', type: 'TRANSFER', categoryId: null,
      paymentSource: 'acc:acc-1', ccy: 'ARS', amount: 10000,
      transactionDate: '2026-06-21', installments: 1,
      destAccountId: 'acc-2', destAmount: null,
    });
    component.submit();

    expect(transferServiceSpy.create).toHaveBeenCalled();
    expect(movServiceSpy.create).not.toHaveBeenCalled();
    const req = transferServiceSpy.create.calls.mostRecent().args[0];
    expect(req.sourceAccountId).toBe('acc-1');
    expect(req.destAccountId).toBe('acc-2');
    expect(req.sourceAmount).toBe(10000);
  });

  it('confirmDelete with transferGroupId calls transferService.delete', () => {
    const TRANSFER_MOVEMENT: MovementResponse = {
      ...MOCK_MOVEMENT, id: 'mv-t1', transferGroupId: 'grp-transfer-1',
    };
    component.openDelete(TRANSFER_MOVEMENT);
    movServiceSpy.search.calls.reset();
    component.confirmDelete();

    expect(transferServiceSpy.delete).toHaveBeenCalledWith('grp-transfer-1');
    expect(movServiceSpy.delete).not.toHaveBeenCalled();
  });

  it('destAccounts excludes the selected source account', () => {
    const MOCK_ACCOUNT_2: AccountResponse = { ...MOCK_ACCOUNT, id: 'acc-2', name: 'MP' };
    accServiceSpy.getAccounts.and.returnValue(of([MOCK_ACCOUNT, MOCK_ACCOUNT_2]));
    fixture = TestBed.createComponent(MovimientosComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.openCreate();
    component.movementForm.patchValue({ type: 'TRANSFER', paymentSource: 'acc:acc-1' });
    component.onTypeOrSourceChange();

    expect(component.destAccounts().length).toBe(1);
    expect(component.destAccounts()[0].id).toBe('acc-2');
  });
});
