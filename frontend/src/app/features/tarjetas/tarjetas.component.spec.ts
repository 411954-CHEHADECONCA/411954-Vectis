import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TarjetasComponent } from './tarjetas.component';
import { CardProjectionService } from '../../core/services/card-projection.service';
import { CreditCardService } from '../../core/services/credit-card.service';
import { AccountService } from '../../core/services/account.service';
import { CardDue, CardMatrix, CardOverview } from '../../core/models/card-projection.models';
import { AccountResponse } from '../../core/models/account.models';
import { CardPaymentHistory, CardPaymentResponse } from '../../core/models/card-payment.models';

const OVERVIEW: CardOverview = {
  cards: [
    { id: 'c1', bank: 'Galicia', network: 'Visa', last4: '4821', ccy: 'ARS', accent: '#52eacd',
      creditLimit: 1500000, consumido: 612300, nextClosingDate: '2026-06-24', nextDueDate: '2026-07-02' },
    { id: 'c2', bank: 'Brubank', network: 'Mastercard', last4: '0937', ccy: 'USD', accent: '#9ed1c5',
      creditLimit: 1750000, consumido: 420000, nextClosingDate: '2026-06-20', nextDueDate: '2026-06-28' },
  ],
  totalDue: 1032300,
  nextDueCardName: 'Brubank ····0937', nextDueDate: '2026-06-28', nextDueAmount: 420000,
  cuotasActivas: [
    { groupId: 'g1', description: 'Notebook', cardName: 'Galicia ····4821', categoryIcon: 'monitor',
      categoryColor: '#52eacd', currentNumber: 3, totalInstallments: 6, monthlyAmount: 102050, ccy: 'ARS' },
  ],
  vencimientos: [
    { cardId: 'c2', cardName: 'Brubank ····0937', dueDate: '2026-06-28', amount: 420000 },
    { cardId: 'c1', cardName: 'Galicia ····4821', dueDate: '2026-07-02', amount: 612300 },
  ],
};

const MATRIX: CardMatrix = {
  months: ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'],
  cards: [
    {
      cardId: 'c1', cardName: 'Galicia ····4821', accent: '#52eacd',
      consumos: [
        { description: 'Notebook', installmentLabel: '3/6', categoryIcon: 'monitor', categoryColor: '#52eacd',
          cellsByMonth: [102050, 102050, 102050, null, null, null],
          paidByMonth: [false, false, false, null, null, null] },
      ],
      subtotalsByMonth: [102050, 102050, 102050, 0, 0, 0],
    },
  ],
  totalsByMonth: [102050, 102050, 102050, 0, 0, 0],
};

const MOCK_ACCOUNTS: AccountResponse[] = [
  { id: 'acc-1', name: 'Galicia ARS', kind: 'Banco', detail: null, ccy: 'ARS', balance: 500000,
    computedBalance: null, remunerada: false, tna: null,
    createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
];

const MOCK_DUE: CardDue = { cardId: 'c2', cardName: 'Brubank ····0937', dueDate: '2026-06-28', amount: 420000 };

describe('TarjetasComponent', () => {
  let fixture: ComponentFixture<TarjetasComponent>;
  let component: TarjetasComponent;
  let serviceSpy: jasmine.SpyObj<CardProjectionService>;
  let creditCardSpy: jasmine.SpyObj<CreditCardService>;
  let accountSpy: jasmine.SpyObj<AccountService>;

  beforeEach(async () => {
    serviceSpy    = jasmine.createSpyObj<CardProjectionService>('CardProjectionService', ['getOverview', 'getMatrix']);
    creditCardSpy = jasmine.createSpyObj<CreditCardService>('CreditCardService', ['payCard', 'getCardPayments']);
    accountSpy    = jasmine.createSpyObj<AccountService>('AccountService', ['getAccounts']);

    serviceSpy.getOverview.and.returnValue(of(OVERVIEW));
    serviceSpy.getMatrix.and.returnValue(of(MATRIX));
    accountSpy.getAccounts.and.returnValue(of(MOCK_ACCOUNTS));
    creditCardSpy.getCardPayments.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [TarjetasComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CardProjectionService, useValue: serviceSpy },
        { provide: CreditCardService,     useValue: creditCardSpy },
        { provide: AccountService,        useValue: accountSpy },
      ],
    }).compileComponents();

    fixture   = TestBed.createComponent(TarjetasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads overview and matrix on init', () => {
    expect(component).toBeTruthy();
    expect(serviceSpy.getOverview).toHaveBeenCalled();
    expect(serviceSpy.getMatrix).toHaveBeenCalledWith(6);
    expect(component.overview()?.cards.length).toBe(2);
  });

  it('renders two card faces and shows stat cards with deuda total', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.card-face').length).toBe(2);
    expect(el.textContent).toContain('Galicia');
    // deuda total = 1.032.300 debe aparecer en alguna stat card
    expect(el.textContent).toContain('1.032.300');
    // stats row: 3 tarjetas de resumen
    expect(el.querySelectorAll('.stat-card').length).toBe(3);
  });

  it('switches tabs', () => {
    expect(component.activeTab()).toBe('cuotas');
    component.setTab('matriz');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('matriz');
    expect((fixture.nativeElement as HTMLElement).querySelector('table.matrix')).toBeTruthy();
  });

  it('expands all cards by default and toggles a card', () => {
    expect(component.isExpanded('c1')).toBeTrue();
    component.toggleCard('c1');
    expect(component.isExpanded('c1')).toBeFalse();
    component.toggleCard('c1');
    expect(component.isExpanded('c1')).toBeTrue();
  });

  it('changing months reloads the matrix', () => {
    serviceSpy.getMatrix.calls.reset();
    component.setMonths(12);
    expect(component.months()).toBe(12);
    expect(serviceSpy.getMatrix).toHaveBeenCalledWith(12);
  });

  it('fmtCell renders a dash for null or zero', () => {
    expect(component.fmtCell(null)).toBe('—');
    expect(component.fmtCell(0)).toBe('—');
    expect(component.fmtCell(102050)).toContain('102.050');
  });

  it('renders the matrix total footer when on matriz tab', () => {
    component.setTab('matriz');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const footer = el.querySelector('.mx-total');
    expect(footer?.textContent).toContain('TOTAL');
  });

  // ── Payment modal ───────────────────────────────────────────────────────────

  it('openPayModal pre-fills paidAmount from the CardDue and sets modal state', () => {
    component.openPayModal(MOCK_DUE);
    expect(component.payModal()).toEqual(jasmine.objectContaining({
      cardId: 'c2', periodYear: 2026, periodMonth: 6,
    }));
    expect(component.payForm.controls.paidAmount.value).toBe(420000);
    expect(accountSpy.getAccounts).toHaveBeenCalled();
  });

  it('closePayModal clears modal signal and resets the form', () => {
    component.openPayModal(MOCK_DUE);
    component.closePayModal();
    expect(component.payModal()).toBeNull();
    expect(component.payError()).toBeNull();
    expect(component.extraChargesArray.length).toBe(0);
  });

  it('addExtraCharge adds a FormGroup to extraChargesArray', () => {
    expect(component.extraChargesArray.length).toBe(0);
    component.addExtraCharge();
    expect(component.extraChargesArray.length).toBe(1);
    component.addExtraCharge();
    expect(component.extraChargesArray.length).toBe(2);
  });

  it('removeExtraCharge removes the correct entry by index', () => {
    component.addExtraCharge();
    component.addExtraCharge();
    component.extraChargesArray.at(0).get('description')!.setValue('Interés');
    component.extraChargesArray.at(1).get('description')!.setValue('IVA');
    component.removeExtraCharge(0);
    expect(component.extraChargesArray.length).toBe(1);
    expect(component.extraChargesArray.at(0).get('description')!.value).toBe('IVA');
  });

  // ── Pagos tab ───────────────────────────────────────────────────────────────

  it('setTab("pagos") llama getCardPayments() cuando payments está vacío', () => {
    const mockPayment: CardPaymentHistory = {
      paymentTransactionId: 'tx-1', cardId: 'c1', cardName: 'Galicia ····4821',
      periodYear: 2026, periodMonth: 6, paidDate: '2026-07-05',
      paidAmount: 102050, ccy: 'ARS', cuotas: [], extraCharges: [],
    };
    creditCardSpy.getCardPayments.and.returnValue(of([mockPayment]));

    component.setTab('pagos');

    expect(creditCardSpy.getCardPayments).toHaveBeenCalled();
    expect(component.payments().length).toBe(1);
    expect(component.payments()[0].paymentTransactionId).toBe('tx-1');
  });

  it('setTab("pagos") NO llama getCardPayments() cuando payments ya está cargado', () => {
    const mockPayment: CardPaymentHistory = {
      paymentTransactionId: 'tx-2', cardId: 'c1', cardName: 'Galicia ····4821',
      periodYear: 2026, periodMonth: 5, paidDate: '2026-06-05',
      paidAmount: 50000, ccy: 'ARS', cuotas: [], extraCharges: [],
    };
    creditCardSpy.getCardPayments.and.returnValue(of([mockPayment]));
    component.setTab('pagos'); // primera carga
    creditCardSpy.getCardPayments.calls.reset();

    component.setTab('cuotas');
    component.setTab('pagos'); // segunda vez — no debe recargar

    expect(creditCardSpy.getCardPayments).not.toHaveBeenCalled();
  });

  it('togglePayment expande y colapsa un pago', () => {
    expect(component.isPaymentExpanded('tx-1')).toBeFalse();
    component.togglePayment('tx-1');
    expect(component.isPaymentExpanded('tx-1')).toBeTrue();
    component.togglePayment('tx-1');
    expect(component.isPaymentExpanded('tx-1')).toBeFalse();
  });

  it('reload limpia el cache de payments para forzar re-fetch', () => {
    const mockPayment: CardPaymentHistory = {
      paymentTransactionId: 'tx-3', cardId: 'c1', cardName: 'Galicia ····4821',
      periodYear: 2026, periodMonth: 4, paidDate: '2026-05-05',
      paidAmount: 30000, ccy: 'ARS', cuotas: [], extraCharges: [],
    };
    creditCardSpy.getCardPayments.and.returnValue(of([mockPayment]));
    component.setTab('pagos');
    expect(component.payments().length).toBe(1);

    component.reload();
    expect(component.payments().length).toBe(0);
  });

  it('submitPayment calls payCard() with correct data and reloads overview', () => {
    const mockResponse: CardPaymentResponse = { cuotasMarcadas: 2, paymentTransactionId: 'tx-1', extraChargesCreated: 0 };
    creditCardSpy.payCard.and.returnValue(of(mockResponse));
    serviceSpy.getOverview.calls.reset();

    component.openPayModal(MOCK_DUE);
    component.payForm.patchValue({ accountId: 'acc-1', paidAmount: 420000, paidDate: '2026-06-28' });
    component.submitPayment();

    expect(creditCardSpy.payCard).toHaveBeenCalledWith('c2', jasmine.objectContaining({
      accountId: 'acc-1', paidAmount: 420000, periodYear: 2026, periodMonth: 6,
    }));
    expect(component.payModal()).toBeNull();
    expect(serviceSpy.getOverview).toHaveBeenCalled();
  });
});
