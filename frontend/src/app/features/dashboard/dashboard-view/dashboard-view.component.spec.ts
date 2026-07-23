import { TestBed, ComponentFixture } from '@angular/core/testing';
import { signal, computed } from '@angular/core';
import { of } from 'rxjs';
import { DashboardViewComponent } from './dashboard-view.component';
import { CurrencyService } from '../../../core/services/currency.service';
import { AccountService } from '../../../core/services/account.service';
import { CardProjectionService } from '../../../core/services/card-projection.service';
import { CashflowService } from '../../../core/services/cashflow.service';
import { InvestmentService } from '../../../core/services/investment.service';
import { AccountResponse } from '../../../core/models/account.models';
import { CardOverview, InstallmentPlan } from '../../../core/models/card-projection.models';
import { CashflowResponse } from '../../../core/models/cashflow.models';
import { InvestmentResponse } from '../../../core/models/investment.models';

/** Stub de FX con cotización fija (1 USD = 1000 ARS) para conversiones deterministas. */
class CurrencyStub {
  selected = signal<'ARS' | 'USD'>('ARS');
  symbol = computed(() => (this.selected() === 'ARS' ? '$' : 'US$'));
  convert(amount: number, from: 'ARS' | 'USD'): number | null {
    if (this.selected() === from) return amount;
    return from === 'ARS' ? amount / 1000 : amount * 1000;
  }
  toggle(): void {
    this.selected.update(c => (c === 'ARS' ? 'USD' : 'ARS'));
  }
}

// ── Fixtures ──────────────────────────────────────────────────────────────────
function account(partial: Partial<AccountResponse>): AccountResponse {
  return {
    id: 'a', name: 'Cuenta', kind: 'Banco', detail: null, ccy: 'ARS',
    balance: 0, computedBalance: 0, remunerada: false, tna: null,
    createdAt: '', updatedAt: '', ...partial,
  };
}

function inv(partial: Partial<InvestmentResponse>): InvestmentResponse {
  return {
    id: 'i', name: 'Activo', type: 'PLAZO_FIJO', currency: 'ARS', principal: 0,
    purchaseDate: '2026-01-01', maturityDate: null, tna: 0, accountId: null,
    accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
    createdAt: '', updatedAt: '', movements: [], valuations: [], ...partial,
  };
}

function cuota(partial: Partial<InstallmentPlan>): InstallmentPlan {
  return {
    groupId: 'g', description: 'Compra', cardName: 'Visa', categoryIcon: null, categoryColor: null,
    currentNumber: 1, totalInstallments: 6, monthlyAmount: 10_000, ccy: 'ARS', ...partial,
  };
}

const DEFAULT_ACCOUNTS: AccountResponse[] = [
  account({ id: 'ars', name: 'Galicia', ccy: 'ARS', computedBalance: 1_000_000 }),
  account({ id: 'usd', name: 'Brubank', ccy: 'USD', computedBalance: 500 }),
];

const DEFAULT_INVESTMENTS: InvestmentResponse[] = [
  inv({ id: 'pf', name: 'Plazo fijo', type: 'PLAZO_FIJO', currency: 'ARS', principal: 200_000 }),
  inv({
    id: 'bono', name: 'AL29', type: 'BONO', currency: 'USD', principal: 100,
    movements: [{ id: 'm1', movementDate: '2026-06-01', type: 'SUSCRIPCION', amount: 100, units: 100, createdAt: '' }],
    valuations: [
      { id: 'v0', valuationDate: '2026-06-30', pricePerUnit: 1.0, source: 'MANUAL', createdAt: '' },
      { id: 'v1', valuationDate: '2026-07-01', pricePerUnit: 1.2, source: 'MANUAL', createdAt: '' },
    ],
  }),
];

function overview(partial: Partial<CardOverview> = {}): CardOverview {
  return {
    cards: [
      { id: 'c1', bank: 'Visa', network: 'VISA', last4: '1234', ccy: 'ARS', accent: '#000', creditLimit: 500_000, consumido: 50_000, nextClosingDate: '2026-07-15', nextDueDate: '2026-07-20' },
      { id: 'c2', bank: 'Amex', network: 'AMEX', last4: '5678', ccy: 'USD', accent: '#000', creditLimit: 1000, consumido: 100, nextClosingDate: '2026-07-18', nextDueDate: '2026-07-25' },
    ],
    totalDue: 150_000,
    nextDueCardName: 'Visa',
    nextDueDate: '2026-07-20',
    nextDueAmount: 50_000,
    cuotasActivas: [cuota({})],
    vencimientos: [{ cardId: 'c1', cardName: 'Visa', dueDate: '2026-07-20', amount: 50_000 }],
    ...partial,
  };
}

function cashflow(partial: Partial<CashflowResponse> = {}): CashflowResponse {
  return {
    year: 2026, month: 7, periodLabel: 'julio 2026', monthShort: 'jul',
    status: 'curso', isProjection: false, recurringMaterialized: true, needsConfirmation: false,
    hasLiquidityDeficit: false, liquidityDeficit: '0',
    openingBalance: { total: '1000000', accounts: [] },
    income: { total: '300000', totalBudgeted: '400000', byCategory: [] },
    expenses: { total: '200000', totalBudgeted: '250000', byCategory: [] },
    preInvestmentBalance: { balance: '1100000', operativeResult: '100000', savingRatePct: '33.3' },
    investments: { total: '0', pctOfPreBalance: null, instruments: [] },
    closingBalance: { total: '1100000', accounts: [] },
    oficialRateAtPeriod: null, ...partial,
  };
}

interface SetupOpts {
  accounts?:    AccountResponse[];
  investments?: InvestmentResponse[];
  overview?:    CardOverview | null;
  cashflow?:    CashflowResponse | null;
}

async function setup(opts: SetupOpts = {}) {
  const accountSpy = jasmine.createSpyObj<AccountService>('AccountService', ['getAccounts']);
  accountSpy.getAccounts.and.returnValue(of(opts.accounts ?? DEFAULT_ACCOUNTS));

  const cardSpy = jasmine.createSpyObj<CardProjectionService>('CardProjectionService', ['getOverview']);
  cardSpy.getOverview.and.returnValue(of(opts.overview === undefined ? overview() : opts.overview) as any);

  const investmentSpy = jasmine.createSpyObj<InvestmentService>('InvestmentService', ['getInvestments']);
  investmentSpy.getInvestments.and.returnValue(of(opts.investments ?? DEFAULT_INVESTMENTS));

  const cashflowSpy = jasmine.createSpyObj<CashflowService>('CashflowService', ['getCashflow']);
  cashflowSpy.getCashflow.and.returnValue(of(opts.cashflow === undefined ? cashflow() : opts.cashflow) as any);

  await TestBed.configureTestingModule({
    imports: [DashboardViewComponent],
    providers: [
      { provide: AccountService, useValue: accountSpy },
      { provide: CardProjectionService, useValue: cardSpy },
      { provide: InvestmentService, useValue: investmentSpy },
      { provide: CashflowService, useValue: cashflowSpy },
      { provide: CurrencyService, useClass: CurrencyStub },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DashboardViewComponent);
  fixture.detectChanges();
  return {
    fixture,
    component: fixture.componentInstance,
    currency: TestBed.inject(CurrencyService) as unknown as CurrencyStub,
    spies: { accountSpy, cardSpy, investmentSpy, cashflowSpy },
  };
}

describe('DashboardViewComponent', () => {
  it('should create', async () => {
    const { component } = await setup();
    expect(component).toBeTruthy();
  });

  it('llama a cada endpoint una vez (cashflow sólo el mes actual)', async () => {
    const { spies } = await setup();
    expect(spies.accountSpy.getAccounts).toHaveBeenCalledTimes(1);
    expect(spies.cardSpy.getOverview).toHaveBeenCalledTimes(1);
    expect(spies.investmentSpy.getInvestments).toHaveBeenCalledTimes(1);
    expect(spies.cashflowSpy.getCashflow).toHaveBeenCalledTimes(1);
  });

  it('renderiza cuatro stat cards', async () => {
    const { fixture } = await setup();
    expect(fixture.nativeElement.querySelectorAll('.stat-card').length).toBe(4);
  });

  it('patrimonio neto = liquidez + valor de cartera − deuda de tarjetas', async () => {
    const { component } = await setup();
    expect(component.liquidez()).toBe(1_500_000);      // 1.000.000 + 500·1000
    expect(component.valorCartera()).toBe(320_000);    // 200.000 + 120·1000
    expect(component.deudaTarjetas()).toBe(150_000);   // 50.000 + 100·1000
    expect(component.patrimonioNeto()).toBe(1_670_000);
  });

  it('la primera stat card muestra el patrimonio neto formateado', async () => {
    const { fixture } = await setup();
    const value = fixture.nativeElement.querySelector('.stat-card__value');
    expect(value.textContent).toContain('1.670.000');
  });

  it('Ratio de Cobertura: liquidez cubre de sobra el próximo vencimiento → good', async () => {
    const { component } = await setup();
    // 1.500.000 / 50.000 × 100 = 3000
    expect(component.ratioCobertura()).toBeCloseTo(3000, 5);
    expect(component.ratioCoberturaTone()).toBe('good');
  });

  it('Ratio de Cobertura: liquidez insuficiente → alerta (bad)', async () => {
    const { component } = await setup({ accounts: [account({ id: 'ars', ccy: 'ARS', computedBalance: 10_000 })] });
    // 10.000 / 50.000 × 100 = 20 (<100)
    expect(component.ratioCobertura()).toBeCloseTo(20, 5);
    expect(component.ratioCoberturaTone()).toBe('bad');
  });

  it('Ratio de Cobertura: sin vencimientos → null', async () => {
    const { component } = await setup({ overview: overview({ vencimientos: [], nextDueDate: null }) });
    expect(component.ratioCobertura()).toBeNull();
    expect(component.proximoVencimiento()).toBeNull();
  });

  it('Índice de Ingreso Comprometido cambia con el toggle 3/6/12', async () => {
    const { component } = await setup();
    // cuota 10.000, remaining = 6−1 = 5; ingreso mes 300.000
    // N=6: min(5,6)=5 → 50.000 / (300.000·6) = 2,78%
    expect(component.ingresoComprometidoPct()).toBeCloseTo(50_000 / (300_000 * 6) * 100, 4);
    component.setHorizonte(3);
    // N=3: min(5,3)=3 → 30.000 / (300.000·3) = 3,33%
    expect(component.ingresoComprometidoPct()).toBeCloseTo(30_000 / (300_000 * 3) * 100, 4);
  });

  it('filas de cartera: una por instrumento, pesos suman 100% y variación diaria del bono es +20%', async () => {
    const { component } = await setup();
    const filas = component.filasCartera();
    expect(filas.length).toBe(2);
    expect(filas[0].name).toBe('Plazo fijo'); // 200.000 la mayor
    const bono = filas.find(f => f.name === 'AL29')!;
    expect(bono.varPct).toBeCloseTo(20, 5);                 // 1.2/1.0 − 1
    expect(bono.valor).toBe(120_000);                        // 120 USD × 1000
    expect(filas.reduce((s, f) => s + f.pesoPct, 0)).toBeCloseTo(100, 5);
  });

  it('variación diaria total de la cartera', async () => {
    const { component } = await setup();
    // bono: 100u × (1.2−1.0) = 20 USD → 20.000 ARS; plazo fijo: tna 0 → 0
    expect(component.variacionDiariaTotal()).toBeCloseTo(20_000, 6);
  });

  it('cada fila expone la valuación anterior (actual − variación diaria)', async () => {
    const { component } = await setup();
    const bono = component.filasCartera().find(f => f.name === 'AL29')!;
    // valor actual 120.000 ARS − variación 20.000 = 100.000 (100u × 1,0 × 1000)
    expect(bono.valorAnterior).toBeCloseTo(100_000, 6);
    expect(bono.valor - bono.varAmount).toBeCloseTo(bono.valorAnterior, 6);
  });

  it('Resultado del mes toma ingresos, egresos, resultado y tasa de ahorro del cashflow', async () => {
    const { component } = await setup();
    expect(component.ingresosMes()).toBe(300_000);
    expect(component.egresosMes()).toBe(200_000);
    expect(component.resultadoMes()).toBe(100_000);
    expect(component.tasaAhorroPct()).toBeCloseTo(33.3, 1);
    expect(component.ingresosPctPpto()).toBeCloseTo(75, 5);   // 300.000/400.000
    expect(component.egresosPctPpto()).toBeCloseTo(80, 5);    // 200.000/250.000
  });

  it('muestra la liquidez total en el panel de Cuentas', async () => {
    const { fixture, component } = await setup();
    expect(component.liquidez()).toBe(1_500_000);
    const liq = fixture.nativeElement.querySelector('.liquidez-value');
    expect(liq.textContent).toContain('1.500.000');
  });

  it('el toggle de moneda reconsolida a USD', async () => {
    const { component, currency, fixture } = await setup();
    currency.toggle();
    fixture.detectChanges();
    expect(component.patrimonioNeto()).toBeCloseTo(1670, 5);
    const symbol = fixture.nativeElement.querySelector('.stat-card__currency');
    expect(symbol.textContent).toContain('US$');
  });

  it('estado vacío: sin cuentas muestra el microcopy de configuración', async () => {
    const { fixture, component } = await setup({ accounts: [] });
    expect(component.hasAccounts()).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Configurá tus cuentas');
  });

  it('estado vacío: sin inversiones no hay tabla de cartera', async () => {
    const { component, fixture } = await setup({ investments: [] });
    expect(component.hasCartera()).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Todavía no registraste inversiones');
  });

  it('fmt formatea en ARS por defecto', async () => {
    const { component } = await setup();
    expect(component.fmt(1_000_000)).toBe('1.000.000');
  });

  it('fmtPct agrega signo y coma decimal, y degrada no-finitos a null', async () => {
    const { component } = await setup();
    expect(component.fmtPct(12.34)).toBe('+12,3%');
    expect(component.fmtPct(-5)).toBe('−5,0%');
    expect(component.fmtPct(null)).toBeNull();
    expect(component.fmtPct(Infinity)).toBeNull();
  });
});
