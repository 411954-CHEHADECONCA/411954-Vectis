import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { MovimientosComponent } from './movimientos.component';
import { MovementService } from '../../core/services/movement.service';
import { CategoryService } from '../../core/services/category.service';
import { AccountService } from '../../core/services/account.service';
import { CreditCardService } from '../../core/services/credit-card.service';
import { MovementResponse } from '../../core/models/movement.models';
import { PageResponse } from '../../core/models/pagination.models';
import { CategoryResponse } from '../../core/models/category.models';
import { AccountResponse } from '../../core/models/account.models';
import { CardResponse } from '../../core/models/card.models';

const MOCK_CATS: CategoryResponse[] = [
  { id: '1', name: 'Sueldo',       icon: 'briefcase', color: '#52eacd', type: 'INCOME',  isDefault: true,  estimatedAmount: null },
  { id: '2', name: 'Supermercado', icon: 'utensils',  color: '#ffb4ab', type: 'EXPENSE', isDefault: true,  estimatedAmount: null },
];

const MOCK_ACCOUNT: AccountResponse = {
  id: 'acc-1', name: 'Galicia', kind: 'Banco', detail: null,
  ccy: 'ARS', balance: 0, remunerada: false, tna: null,
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

const MOCK_CARD: CardResponse = {
  id: 'card-1', bank: 'Galicia', network: 'Visa', last4: '4821',
  ccy: 'ARS', creditLimit: 500000, closingDay: 5, dueDay: 15, accent: '#52eacd',
  createdAt: '2026-06-10T00:00:00Z', updatedAt: '2026-06-10T00:00:00Z',
};

const MOCK_MOVEMENT: MovementResponse = {
  id: 'mv-1', type: 'EXPENSE', description: 'Coto', amount: 86400, ccy: 'ARS',
  categoryId: '2', categoryName: 'Supermercado', categoryIcon: 'utensils', categoryColor: '#ffb4ab',
  accountId: 'acc-1', accountName: 'Galicia', cardId: null, cardName: null,
  transactionDate: '2026-06-09', dueDate: '2026-06-09',
  installment: false, installmentNumber: null, totalInstallments: null,
  installmentGroupId: null, createdAt: '2026-06-09T00:00:00Z',
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

  beforeEach(async () => {
    movServiceSpy = jasmine.createSpyObj<MovementService>('MovementService',
      ['search', 'summary', 'create', 'update', 'delete']);
    movServiceSpy.search.and.returnValue(of(pageOf([MOCK_MOVEMENT])));
    movServiceSpy.summary.and.returnValue(of({ totalIncome: 0, totalExpense: 86400, net: -86400, count: 1 }));
    movServiceSpy.create.and.returnValue(of([MOCK_MOVEMENT]));
    movServiceSpy.update.and.returnValue(of(MOCK_MOVEMENT));
    movServiceSpy.delete.and.returnValue(of(void 0));

    catServiceSpy = jasmine.createSpyObj<CategoryService>('CategoryService', ['getCategories']);
    catServiceSpy.getCategories.and.returnValue(of([...MOCK_CATS]));

    accServiceSpy = jasmine.createSpyObj<AccountService>('AccountService', ['getAccounts']);
    accServiceSpy.getAccounts.and.returnValue(of([MOCK_ACCOUNT]));

    cardServiceSpy = jasmine.createSpyObj<CreditCardService>('CreditCardService', ['getCards']);
    cardServiceSpy.getCards.and.returnValue(of([MOCK_CARD]));

    await TestBed.configureTestingModule({
      imports: [MovimientosComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MovementService,   useValue: movServiceSpy  },
        { provide: CategoryService,   useValue: catServiceSpy  },
        { provide: AccountService,    useValue: accServiceSpy  },
        { provide: CreditCardService, useValue: cardServiceSpy },
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
    });
    component.submit();

    const req = movServiceSpy.create.calls.mostRecent().args[0];
    expect(req.accountId).toBe('acc-1');
    expect(req.cardId).toBeNull();
    expect(req.installments).toBe(1);
  });

  it('openEdit does not open the modal for an installment row', () => {
    component.openEdit(MOCK_INSTALLMENT);
    expect(component.modal()).toBeNull();
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
});
