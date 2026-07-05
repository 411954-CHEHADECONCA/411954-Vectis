import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import { InversionesComponent } from './inversiones.component';
import { InvestmentService } from '../../core/services/investment.service';
import { AccountService } from '../../core/services/account.service';
import { MacroService } from '../../core/services/macro.service';
import { CurrencyService } from '../../core/services/currency.service';
import { FciFundOption, InstrumentOption, InvestmentMovement, InvestmentResponse, ASSET_TINTS } from '../../core/models/investment.models';
import { InvestmentValuationRequest } from '../../core/models/investment.models';
import { InflationResponse, ExchangeRateResponse } from '../../core/models/macro.models';
import { signal } from '@angular/core';

// ── Mock data ─────────────────────────────────────────────────────────────────

const MOCK_FCI_MOVEMENTS: InvestmentMovement[] = [
  { id: 'mov-1', movementDate: '2026-01-10', type: 'SUSCRIPCION', amount: 500000, units: null, createdAt: '2026-01-10T00:00:00Z' },
  { id: 'mov-2', movementDate: '2026-02-10', type: 'SUSCRIPCION', amount: 100000, units: null, createdAt: '2026-02-10T00:00:00Z' },
];

const MOCK_ASSETS: InvestmentResponse[] = [
  {
    id: 'inv-1', name: 'FCI Ahorro', type: 'FCI', currency: 'ARS',
    principal: 0, purchaseDate: '2026-01-10', maturityDate: null,
    tna: 65.5, accountId: null, accountName: null,
    autoTrack: false, externalId: null, includeInCashflow: true,
    createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z',
    movements: MOCK_FCI_MOVEMENTS,
    valuations: [],
  },
  {
    id: 'inv-2', name: 'LECAP S31G5', type: 'LETRA', currency: 'ARS',
    principal: 850000, purchaseDate: '2026-02-01', maturityDate: '2026-08-31',
    tna: 72.0, accountId: null, accountName: null,
    autoTrack: false, externalId: null, includeInCashflow: true,
    createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z',
    // 850000 nominales × VN=1 → calcValorActualCP = 850000 (usada en capitalARS)
    movements: [
      { id: 'mov-letra-1', movementDate: '2026-02-01', type: 'SUSCRIPCION' as const, amount: 850000, units: 850000, createdAt: '2026-02-01T00:00:00Z' },
    ],
    valuations: [],
  },
];

const MOCK_INFLATION: InflationResponse = {
  monthlyRate: '2.4000',
  periodDate:  '2026-05-31',
  source:      'argentinadatos.com',
};

const MOCK_RATE: ExchangeRateResponse = {
  rateType: 'OFICIAL',
  buy:      '1060.0000',
  sell:     '1062.5000',
  rateDate: '2026-06-22',
  source:   'dolarapi.com',
};

// ── Helpers ───────────────────────────────────────────────────────────────────

const MOCK_FCI_FUNDS: FciFundOption[] = [
  { fondo: 'FCI Galileo Growth',  categoria: 'mercadoDinero', vcp: 1523.456, fecha: '2026-06-24' },
  { fondo: 'FCI Compass Growth',  categoria: 'rentaFija',     vcp: 2100.12,  fecha: '2026-06-24' },
];

const MOCK_INSTRUMENTS: InstrumentOption[] = [
  { ticker: 'S31G5', nombre: 'LECAP 31/08/2025', tipo: 'LETRA', lastPrice: 1020.5, priceDate: '2026-06-24', maturityDate: '2025-08-31' },
  { ticker: 'GD30',  nombre: 'Global 2030',       tipo: 'BONO',  lastPrice: 57.40,  priceDate: '2026-06-24', maturityDate: '2030-07-09' },
];

function buildSpies() {
  const investSpy = jasmine.createSpyObj<InvestmentService>('InvestmentService', [
    'getInvestments', 'createInvestment', 'updateInvestment', 'deleteInvestment', 'collectInvestment',
    'addMovement', 'updateMovement', 'deleteMovement', 'addValuation', 'updateValuation', 'deleteValuation',
    'getFciFunds', 'getInstruments', 'getFciVcp', 'getInstrumentPrice',
  ]);
  investSpy.getInvestments.and.returnValue(of([...MOCK_ASSETS]));
  investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0] }));
  investSpy.updateInvestment.and.returnValue(of({ ...MOCK_ASSETS[1], name: 'LECAP Actualizada' }));
  investSpy.deleteInvestment.and.returnValue(of(undefined));
  investSpy.collectInvestment.and.returnValue(of({
    investmentId: MOCK_ASSETS[0].id, amount: 500000, currency: 'ARS', transactionCreated: true,
  }));
  investSpy.addMovement.and.returnValue(of({ ...MOCK_ASSETS[0] }));
  investSpy.updateMovement.and.returnValue(of({ ...MOCK_ASSETS[0] }));
  investSpy.deleteMovement.and.returnValue(of({ ...MOCK_ASSETS[0], movements: [] }));
  investSpy.addValuation.and.returnValue(of({ ...MOCK_ASSETS[0] }));
  investSpy.updateValuation.and.returnValue(of({ ...MOCK_ASSETS[0] }));
  investSpy.deleteValuation.and.returnValue(of({ ...MOCK_ASSETS[0] }));
  investSpy.getFciFunds.and.returnValue(of(MOCK_FCI_FUNDS));
  investSpy.getInstruments.and.returnValue(of(MOCK_INSTRUMENTS));
  investSpy.getFciVcp.and.returnValue(of(null));
  investSpy.getInstrumentPrice.and.returnValue(of(null));

  const accountSpy = jasmine.createSpyObj<AccountService>('AccountService', ['getAccounts']);
  accountSpy.getAccounts.and.returnValue(of([]));

  const macroSpy = jasmine.createSpyObj<MacroService>('MacroService', [
    'getLatestInflation', 'getLatestOficialRate', 'getLatestMepRate',
  ]);
  macroSpy.getLatestInflation.and.returnValue(of(MOCK_INFLATION));
  macroSpy.getLatestOficialRate.and.returnValue(of(MOCK_RATE));
  macroSpy.getLatestMepRate.and.returnValue(of(MOCK_RATE));

  const currencySpy = jasmine.createSpyObj<CurrencyService>('CurrencyService', ['convert', 'toggle'], {
    currentRateARS: signal<string | null>('1062.50'),
    selected:       signal<'ARS' | 'USD'>('ARS'),
  });
  currencySpy.convert.and.callFake((amount: number, fromCcy: 'ARS' | 'USD') => {
    const sel = currencySpy.selected();
    if (sel === fromCcy) return amount;
    const rate = parseFloat(currencySpy.currentRateARS() ?? '');
    if (!rate) return null;
    return fromCcy === 'ARS' ? amount / rate : amount * rate;
  });

  return { investSpy, accountSpy, macroSpy, currencySpy };
}

// ── Suite ─────────────────────────────────────────────────────────────────────

describe('InversionesComponent', () => {
  let fixture:   ComponentFixture<InversionesComponent>;
  let component: InversionesComponent;
  let investSpy:    jasmine.SpyObj<InvestmentService>;
  let macroSpy:     jasmine.SpyObj<MacroService>;
  let currencySpy:  jasmine.SpyObj<CurrencyService>;

  beforeEach(async () => {
    const spies = buildSpies();
    investSpy   = spies.investSpy;
    macroSpy    = spies.macroSpy;
    currencySpy = spies.currencySpy;

    await TestBed.configureTestingModule({
      imports: [InversionesComponent, ReactiveFormsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InvestmentService, useValue: investSpy         },
        { provide: AccountService,    useValue: spies.accountSpy  },
        { provide: MacroService,      useValue: macroSpy          },
        { provide: CurrencyService,   useValue: spies.currencySpy },
      ],
    })
    .overrideComponent(InversionesComponent, { set: { schemas: [NO_ERRORS_SCHEMA] } })
    .compileComponents();

    fixture   = TestBed.createComponent(InversionesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // ── 1. Carga inicial ───────────────────────────────────────────────────────

  it('ngOnInit carga los activos y los renderiza en la tabla', () => {
    expect(investSpy.getInvestments).toHaveBeenCalled();
    expect(component.assets().length).toBe(2);
    expect(component.loading()).toBeFalse();
    expect(component.error()).toBeNull();

    const rows = fixture.nativeElement.querySelectorAll('.asset-row');
    expect(rows.length).toBe(2);
  });

  // ── 2. KPI computed ────────────────────────────────────────────────────────

  it('capitalARS computed suma los activos en ARS correctamente (FCI usa saldo de movimientos)', () => {
    // FCI inv-1: suscripcion 500k + suscripcion 100k = 600k
    // LETRA inv-2: principal 850k
    expect(component.capitalARS()).toBe(600000 + 850000);
  });

  // ── 2a. Capital activo (excluye vencidos y rescatados en su totalidad) ──────

  describe('isActivo / capitalARS excluye vencidos y rescatados en su totalidad', () => {
    it('excluye un PLAZO_FIJO vencido pero incluye uno vigente', () => {
      const vencido: InvestmentResponse = {
        id: 'pf-venc', name: 'PF Vencido', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 500000, purchaseDate: '2025-01-01', maturityDate: '2025-06-01',
        tna: 40, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      const vigente: InvestmentResponse = { ...vencido, id: 'pf-vig', maturityDate: '2099-01-01', principal: 300000 };
      component.assets.set([vencido, vigente]);

      expect(component.isActivo(vencido)).toBeFalse();
      expect(component.isActivo(vigente)).toBeTrue();
      expect(component.capitalARS()).toBe(300000);
    });

    it('excluye un FCI cuyo saldo neteó a 0 tras un rescate total (sigue en assets())', () => {
      const fciCerrado: InvestmentResponse = {
        ...MOCK_ASSETS[0], id: 'fci-cerrado',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 100000, units: null, createdAt: '' },
          { id: 'm2', movementDate: '2026-02-01', type: 'RESCATE',     amount: 100000, units: null, createdAt: '' },
        ],
      };
      component.assets.set([fciCerrado]);

      expect(component.isActivo(fciCerrado)).toBeFalse();
      expect(component.capitalARS()).toBe(0);
      expect(component.assets().length).toBe(1);
    });

    it('excluye una LETRA vencida aunque tenga nominales sin rescatar (saldo > 0)', () => {
      const letraVencida: InvestmentResponse = { ...MOCK_ASSETS[1], id: 'letra-venc', maturityDate: '2025-01-01' };
      component.assets.set([letraVencida]);

      expect(component.isActivo(letraVencida)).toBeFalse();
      expect(component.capitalARS()).toBe(0);
    });

    it('capitalARS/capitalUSD filtran por currency sin mezclar activos', () => {
      const usdAsset: InvestmentResponse = {
        ...MOCK_ASSETS[1], id: 'letra-usd', currency: 'USD', maturityDate: '2099-01-01', principal: 1000,
        movements: [{ id: 'mu1', movementDate: '2026-02-01', type: 'SUSCRIPCION', amount: 1000, units: 1000, createdAt: '' }],
      };
      component.assets.set([MOCK_ASSETS[1], usdAsset]);

      expect(component.capitalARS()).toBe(850000);
      expect(component.capitalUSD()).toBe(1000);
    });
  });

  describe('capitalMostrado / fmtMontoSel — toggle bimonetario', () => {
    it('capitalMostrado convierte capitalUSD a ARS cuando selected() es ARS', () => {
      const usdAsset: InvestmentResponse = {
        ...MOCK_ASSETS[1], id: 'letra-usd', currency: 'USD', maturityDate: '2099-01-01', principal: 1000,
        movements: [{ id: 'mu1', movementDate: '2026-02-01', type: 'SUSCRIPCION', amount: 1000, units: 1000, createdAt: '' }],
      };
      component.assets.set([MOCK_ASSETS[1], usdAsset]);
      const rate = 1062.50;
      expect(component.capitalMostrado()).toBeCloseTo(850000 + 1000 * rate, 2);
    });

    it('capitalMostrado convierte capitalARS a USD cuando selected() es USD', () => {
      const usdAsset: InvestmentResponse = {
        ...MOCK_ASSETS[1], id: 'letra-usd', currency: 'USD', maturityDate: '2099-01-01', principal: 1000,
        movements: [{ id: 'mu1', movementDate: '2026-02-01', type: 'SUSCRIPCION', amount: 1000, units: 1000, createdAt: '' }],
      };
      component.assets.set([MOCK_ASSETS[1], usdAsset]);
      currencySpy.selected.set('USD');
      const rate = 1062.50;
      expect(component.capitalMostrado()).toBeCloseTo(850000 / rate + 1000, 4);
    });

    it('fmtMontoSel degrada al monto nativo formateado cuando no hay cotización disponible', () => {
      currencySpy.selected.set('USD');
      currencySpy.convert.and.returnValue(null);
      expect(component.fmtMontoSel(500000, 'ARS')).toBe(component.fmtARS(500000));
      expect(component.fmtMontoSel(500, 'USD')).toBe(component.fmtUSD(500));
    });
  });

  // ── 2b-i. Ganancia real por rango de fechas (tramos por mes calendario) ────

  describe('overlapDias / gananciaEnRango', () => {
    const isoDate = (d: Date) => [
      d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0'),
    ].join('-');

    it('overlapDias da 0 cuando los rangos no se solapan', () => {
      expect((component as any).overlapDias('2026-01-01', '2026-01-10', '2026-02-01', '2026-02-10')).toBe(0);
    });

    it('overlapDias da los días completos cuando un rango contiene al otro', () => {
      expect((component as any).overlapDias('2026-01-01', '2026-01-31', '2026-01-10', '2026-01-20')).toBe(10);
    });

    it('overlapDias da 0 cuando el rango tiene 0 días (startA === endA)', () => {
      expect((component as any).overlapDias('2026-01-01', '2026-01-01', '2026-01-01', '2026-02-01')).toBe(0);
    });

    it('gananciaEnRango PLAZO_FIJO: sólo cuenta desde purchaseDate si se compró a mitad del mes en curso', () => {
      const today   = new Date();
      const desde   = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
      const hasta   = isoDate(today);
      const midDay  = Math.max(1, Math.min(today.getDate(), 15));
      const purchaseDate = isoDate(new Date(today.getFullYear(), today.getMonth(), midDay));
      const pf: InvestmentResponse = {
        id: 'pf-mid', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 100000, purchaseDate, maturityDate: null,
        tna: 36.5, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      const diasEsperados = Math.floor(
        (new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
          - new Date(today.getFullYear(), today.getMonth(), midDay).getTime()) / 86400000,
      );
      const esperado = 100000 * (36.5 / 100) * (diasEsperados / 365);
      expect(component.gananciaEnRango(pf, desde, hasta)).toBeCloseTo(esperado, 2);
    });

    it('gananciaEnRango PLAZO_FIJO vencido el mes pasado: 0 en el rango del mes actual', () => {
      const today = new Date();
      const desdeActual = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
      const hastaActual = isoDate(today);
      const pf: InvestmentResponse = {
        id: 'pf-venc-pasado', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 100000, purchaseDate: '2020-01-01',
        maturityDate: isoDate(new Date(today.getFullYear(), today.getMonth() - 1, 15)),
        tna: 36.5, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      expect(component.gananciaEnRango(pf, desdeActual, hastaActual)).toBe(0);
    });

    it('gananciaEnRango FCI: el rango exacto del tramo devuelve el 100% de su interés; sin solape da 0', () => {
      const fci: InvestmentResponse = {
        ...MOCK_ASSETS[0],
        movements: [{ id: 'm1', movementDate: '2020-01-01', type: 'SUSCRIPCION', amount: 100000, units: null, createdAt: '' }],
      };
      const tramos  = component.calcTramosFCI(fci);
      const enCurso = tramos.find(t => t.enCurso)!;
      const completo = component.gananciaEnRango(fci, enCurso.mov!.movementDate, enCurso.endDate);
      expect(completo).toBeCloseTo(enCurso.intereses, 6);

      expect(component.gananciaEnRango(fci, '1999-01-01', '1999-02-01')).toBe(0);
    });

    it('gananciaEnRango FCI sin movimientos da 0 para cualquier rango', () => {
      const fciVacio: InvestmentResponse = { ...MOCK_ASSETS[0], movements: [] };
      expect(component.gananciaEnRango(fciVacio, '2020-01-01', '2026-12-31')).toBe(0);
    });

    it('gananciaEnRango familia CP: el rango exacto del tramo en curso devuelve el 100% de su ganancia', () => {
      const asset   = MOCK_ASSETS[1]; // LETRA
      const tramos  = component.calcTramosCP(asset);
      const enCurso = tramos.find(t => t.enCurso)!;
      const completo = component.gananciaEnRango(asset, enCurso.startDate, enCurso.endDate);
      expect(completo).toBeCloseTo(enCurso.ganancia, 6);
    });
  });

  // ── 2b-ii. Ganancia del mes en curso vs. mes pasado y tasa real mensual ─────

  describe('gananciaMesActual / gananciaMesAnterior', () => {
    it('suman activos activos (isActivo) de ambas monedas, convirtiendo USD a la moneda seleccionada', () => {
      const today = new Date();
      const isoDate = (d: Date) => [
        d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0'),
      ].join('-');
      const desdeActual   = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
      const hastaActual   = isoDate(today);
      const desdeAnterior = isoDate(new Date(today.getFullYear(), today.getMonth() - 1, 1));
      const hastaAnterior = desdeActual;

      const pfActivo: InvestmentResponse = {
        id: 'pf-1', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 100000, purchaseDate: '2020-01-01', maturityDate: '2099-01-01',
        tna: 36, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      const pfUSD:     InvestmentResponse = { ...pfActivo, id: 'pf-usd',  currency: 'USD' };
      const pfVencido: InvestmentResponse = { ...pfActivo, id: 'pf-venc', maturityDate: '2020-06-01' };

      component.assets.set([pfActivo, pfUSD, pfVencido]);

      const rate = 1062.50; // coincide con el mock de currentRateARS en buildSpies()
      const esperadoActual =
        component.gananciaEnRango(pfActivo, desdeActual, hastaActual) +
        component.gananciaEnRango(pfUSD, desdeActual, hastaActual) * rate;
      const esperadoAnterior =
        component.gananciaEnRango(pfActivo, desdeAnterior, hastaAnterior) +
        component.gananciaEnRango(pfUSD, desdeAnterior, hastaAnterior) * rate;

      expect(component.gananciaMesActual()).toBeCloseTo(esperadoActual, 2);
      expect(component.gananciaMesAnterior()).toBeCloseTo(esperadoAnterior, 2);
    });

    it('al togglear a USD, convierte la porción ARS en vez de la USD', () => {
      const today = new Date();
      const isoDate = (d: Date) => [
        d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0'),
      ].join('-');
      const desdeActual = isoDate(new Date(today.getFullYear(), today.getMonth(), 1));
      const hastaActual = isoDate(today);

      const pfActivo: InvestmentResponse = {
        id: 'pf-1', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 100000, purchaseDate: '2020-01-01', maturityDate: '2099-01-01',
        tna: 36, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      const pfUSD: InvestmentResponse = { ...pfActivo, id: 'pf-usd', currency: 'USD' };
      component.assets.set([pfActivo, pfUSD]);

      currencySpy.selected.set('USD');

      const rate = 1062.50;
      const esperado =
        component.gananciaEnRango(pfActivo, desdeActual, hastaActual) / rate +
        component.gananciaEnRango(pfUSD, desdeActual, hastaActual);

      expect(component.gananciaMesActual()).toBeCloseTo(esperado, 6);
    });
  });

  describe('gananciaMesEstimada / variacionEstimadaPct', () => {
    it('el último día del mes coincide exactamente con gananciaMesActual (factor de proyección 1)', () => {
      const today = new Date();
      const ultimoDia = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate();
      if (today.getDate() !== ultimoDia) {
        // Sólo aplica el día exacto de cierre de mes; en cualquier otro día se salta (se cubre con el caso general).
        return;
      }
      const pf: InvestmentResponse = {
        id: 'pf-ultimo-dia', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 100000, purchaseDate: '2020-01-01', maturityDate: '2099-01-01',
        tna: 36, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      component.assets.set([pf]);
      expect(component.gananciaMesEstimada()).toBeCloseTo(component.gananciaMesActual(), 6);
    });

    it('proyecta gananciaMesActual al total de días del mes según los días transcurridos', () => {
      const today = new Date();
      const diasEnMes  = new Date(today.getFullYear(), today.getMonth() + 1, 0).getDate();
      const diasHoy    = today.getDate();
      const pf: InvestmentResponse = {
        id: 'pf-proy', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 100000, purchaseDate: '2020-01-01', maturityDate: '2099-01-01',
        tna: 36, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      component.assets.set([pf]);

      const esperado = component.gananciaMesActual() * (diasEnMes / diasHoy);
      expect(component.gananciaMesEstimada()).toBeCloseTo(esperado, 6);
    });

    it('da 0 cuando gananciaMesActual es 0 (sin activos)', () => {
      component.assets.set([]);
      expect(component.gananciaMesActual()).toBe(0);
      expect(component.gananciaMesEstimada()).toBe(0);
    });

    it('variacionEstimadaPct da null cuando no hay ganancia el mes pasado (sin base de comparación)', () => {
      component.assets.set([]);
      expect(component.gananciaMesAnterior()).toBe(0);
      expect(component.variacionEstimadaPct()).toBeNull();
    });

    it('variacionEstimadaPct calcula el % de variación entre la estimación del mes y el mes pasado', () => {
      const pf: InvestmentResponse = {
        id: 'pf-var', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 100000, purchaseDate: '2020-01-01', maturityDate: '2099-01-01',
        tna: 36, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      component.assets.set([pf]);

      const estimada  = component.gananciaMesEstimada();
      const anterior  = component.gananciaMesAnterior();
      const esperado  = anterior !== 0 ? ((estimada - anterior) / Math.abs(anterior)) * 100 : null;

      if (esperado === null) {
        expect(component.variacionEstimadaPct()).toBeNull();
      } else {
        expect(component.variacionEstimadaPct()).toBeCloseTo(esperado, 6);
      }
    });
  });

  describe('tasaEfectivaMensual / tasaRealMensual', () => {
    it('tasaEfectivaMensual da 0 si no hay capital activo (evita división por 0)', () => {
      component.assets.set([]);
      expect(component.tasaEfectivaMensual()).toBe(0);
    });

    it('tasaRealMensual da null si no hay datos de inflación disponibles', async () => {
      const spies = buildSpies();
      spies.macroSpy.getLatestInflation.and.returnValue(throwError(() => new Error('sin datos')));

      await TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [InversionesComponent, ReactiveFormsModule],
        providers: [
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: InvestmentService, useValue: spies.investSpy    },
          { provide: AccountService,    useValue: spies.accountSpy   },
          { provide: MacroService,      useValue: spies.macroSpy     },
          { provide: CurrencyService,   useValue: spies.currencySpy  },
        ],
      })
      .overrideComponent(InversionesComponent, { set: { schemas: [NO_ERRORS_SCHEMA] } })
      .compileComponents();

      const f = TestBed.createComponent(InversionesComponent);
      const c = f.componentInstance;
      f.detectChanges();

      expect(c.tasaRealMensual()).toBeNull();
    });

    it('tasaRealMensual aplica Fisher mensual entre tasaEfectivaMensual e IPC', () => {
      const pf: InvestmentResponse = {
        id: 'pf-tasa', name: 'PF', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 1000000, purchaseDate: '2020-01-01', maturityDate: '2099-01-01',
        tna: 60, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '', movements: [], valuations: [],
      };
      component.assets.set([pf]);

      const tem = component.tasaEfectivaMensual();
      const ipcMensual = parseFloat(MOCK_INFLATION.monthlyRate);
      const esperado = ((1 + tem / 100) / (1 + ipcMensual / 100) - 1) * 100;

      expect(component.tasaRealMensual()).toBeCloseTo(esperado, 6);
    });
  });

  // ── 2b. Total capital + intereses ──────────────────────────────────────────

  describe('calcMontoTotal', () => {
    const pf: InvestmentResponse = {
      id: 'inv-pf', name: 'PF Banco', type: 'PLAZO_FIJO', currency: 'ARS',
      principal: 1000000, purchaseDate: '2026-06-01', maturityDate: '2026-07-01',
      tna: 36.5, accountId: null, accountName: null,
      autoTrack: false, externalId: null, includeInCashflow: true,
      createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z',
      movements: [], valuations: [],
    };

    it('PLAZO_FIJO: capital + intereses del plazo completo hasta el vencimiento', () => {
      // 30 días, TNA 36.5% → interés = 1.000.000 × 0,365 × 30/365 = 30.000
      expect(component.calcInteresesVencimiento(pf)).toBeCloseTo(30000, 2);
      expect(component.calcMontoTotal(pf)).toBeCloseTo(1030000, 2);
    });

    it('PLAZO_FIJO sin vencimiento: usa los intereses devengados a la fecha', () => {
      const sinVenc = { ...pf, maturityDate: null };
      expect(component.calcMontoTotal(sinVenc))
        .toBeCloseTo(sinVenc.principal + component.calcIntereses(sinVenc), 2);
    });

    it('FCI (Cuenta Remunerada): saldo actual + interés devengado del tramo en curso', () => {
      const fci = component.assets().find(a => a.type === 'FCI')!;
      const enCurso = component.calcTramosFCI(fci).find(t => t.enCurso)!;
      expect(component.calcMontoTotal(fci))
        .toBeCloseTo(component.calcSaldoFCI(fci) + enCurso.intereses, 2);
    });

    it('LETRA/BONO/ON/cuotapartes: devuelve el valor actual (ya incluye la ganancia)', () => {
      const letra = component.assets().find(a => a.type === 'LETRA')!;
      expect(component.calcMontoTotal(letra)).toBe(component.saldoBase(letra));
    });
  });

  // ── 2c. calcTNA ────────────────────────────────────────────────────────────

  describe('calcTNA', () => {
    it('FCI: devuelve asset.tna directo', () => {
      const fci = component.assets().find(a => a.type === 'FCI')!;
      expect(component.calcTNA(fci)).toBe(fci.tna);
    });

    it('PLAZO_FIJO: devuelve asset.tna directo', () => {
      const pf: InvestmentResponse = {
        id: 'inv-pf', name: 'PF Banco', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 1000000, purchaseDate: '2026-06-01', maturityDate: '2026-07-01',
        tna: 36.5, accountId: null, accountName: null,
        autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z',
        movements: [], valuations: [],
      };
      expect(component.calcTNA(pf)).toBe(36.5);
    });

    it('LETRA/BONO/ON/cuotapartes: anualiza la ganancia realizada por interés simple sobre el capital', () => {
      const letra = component.assets().find(a => a.type === 'LETRA')!;
      const dias = component.diasPeriodo(letra);
      const esperado = (component.calcIntereses(letra) / letra.principal) * (365 / dias) * 100;
      expect(component.calcTNA(letra)).toBeCloseTo(esperado, 6);
    });

    it('LETRA/BONO/ON/cuotapartes con principal 0: devuelve 0 (evita división por cero)', () => {
      const letra = component.assets().find(a => a.type === 'LETRA')!;
      const sinPrincipal = { ...letra, principal: 0 };
      expect(component.calcTNA(sinPrincipal)).toBe(0);
    });
  });

  // ── 2d. calcTEA (distinta de calcTIR fuera del caso trivial) ────────────────

  describe('calcTEA', () => {
    it('CP con una sola suscripción y sin flujos intermedios: calcTEA ≈ calcTIR (caso trivial)', () => {
      const cp: InvestmentResponse = {
        id: 'cp-una', name: 'CP Una Suscripcion', type: 'FCI_CUOTAPARTES', currency: 'ARS',
        principal: 100000, purchaseDate: '2026-01-01', maturityDate: null, tna: 0,
        accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 100000, units: 400, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: '2026-06-01', pricePerUnit: 300, source: 'MANUAL', createdAt: '' },
        ],
      };
      expect(component.calcTEA(cp)).toBeCloseTo(component.calcTIR(cp), 4);
    });

    it('CP con dos suscripciones en fechas distintas: calcTEA difiere de calcTIR', () => {
      const cp: InvestmentResponse = {
        id: 'cp-dos', name: 'CP Dos Suscripciones', type: 'FCI_CUOTAPARTES', currency: 'ARS',
        principal: 700000, purchaseDate: '2026-01-01', maturityDate: null, tna: 0,
        accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 500000, units: 400, createdAt: '' },
          { id: 'm2', movementDate: '2026-03-15', type: 'SUSCRIPCION', amount: 200000, units: 150, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: '2026-06-01', pricePerUnit: 1500, source: 'MANUAL', createdAt: '' },
        ],
      };
      expect(component.calcTEA(cp)).not.toBeCloseTo(component.calcTIR(cp), 4);
    });

    it('PLAZO_FIJO: anualiza por compounding la tasa nominal del período', () => {
      const pf: InvestmentResponse = {
        id: 'inv-pf', name: 'PF Banco', type: 'PLAZO_FIJO', currency: 'ARS',
        principal: 1000000, purchaseDate: '2026-06-01', maturityDate: '2026-07-01',
        tna: 36.5, accountId: null, accountName: null,
        autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z',
        movements: [], valuations: [],
      };
      const d = component.diasPeriodo(pf);
      const tasaPer = (pf.tna / 100) * (d / 365);
      const esperado = (Math.pow(1 + tasaPer, 365 / d) - 1) * 100;
      expect(component.calcTEA(pf)).toBeCloseTo(esperado, 6);
    });
  });

  // ── 3. openCreate ──────────────────────────────────────────────────────────

  it('openCreate abre el selector de tipo y resetea el formulario', () => {
    component.openCreate();
    expect(component.modal()?.kind).toBe('type-select');
    expect(component.plazoFijoForm.controls.name.value).toBe('');
    expect(component.plazoFijoForm.controls.currency.value).toBe('ARS');
    expect(component.plazoFijoForm.controls.principal.value).toBeNull();
    expect(component.formError()).toBeNull();
  });

  // ── includeInCashflow: default true en los 4 formularios ────────────────────

  describe('includeInCashflow — default en los 4 formularios', () => {
    it('plazoFijoForm.includeInCashflow es true por defecto', () => {
      component.openCreate();
      expect(component.plazoFijoForm.controls.includeInCashflow.value).toBeTrue();
    });

    it('fciForm.includeInCashflow es true por defecto', () => {
      component.openCreate();
      component.selectType('FCI');
      expect(component.fciForm.controls.includeInCashflow.value).toBeTrue();
    });

    it('fciCPForm.includeInCashflow es true por defecto', () => {
      component.openCreate();
      component.selectType('FCI_CUOTAPARTES');
      expect(component.fciCPForm.controls.includeInCashflow.value).toBeTrue();
    });

    it('letraForm.includeInCashflow es true por defecto', () => {
      component.openCreate();
      component.selectType('LETRA');
      expect(component.letraForm.controls.includeInCashflow.value).toBeTrue();
    });
  });

  // ── includeInCashflow: presente en el payload de creación de cada tipo ──────

  describe('includeInCashflow — presente en el payload de submit', () => {
    it('submit de PLAZO_FIJO envía includeInCashflow en el request', () => {
      component.openCreate();
      component.selectType('PLAZO_FIJO');
      component.plazoFijoForm.setValue({
        name: 'PF Test', currency: 'ARS',
        principal: 500000, purchaseDate: '2026-06-01',
        dias: 30, tna: 60.0, accountId: null, includeInCashflow: false,
      });
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[1], id: 'inv-new' }));
      component.submit();
      const req = investSpy.createInvestment.calls.mostRecent().args[0];
      expect(req.includeInCashflow).toBeFalse();
    });

    it('submit de FCI envía includeInCashflow en el request', () => {
      component.openCreate();
      component.selectType('FCI');
      component.fciForm.patchValue({ name: 'FCI Test', currency: 'ARS', tna: 65, purchaseDate: '2026-02-15', includeInCashflow: false });
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0], id: 'new-fci' }));
      component.submit();
      const req = investSpy.createInvestment.calls.mostRecent().args[0];
      expect(req.includeInCashflow).toBeFalse();
    });

    it('submit de FCI_CUOTAPARTES envía includeInCashflow en el request', () => {
      component.openCreate();
      component.selectType('FCI_CUOTAPARTES');
      component.setTrackingMode('manual');
      component.fciCPForm.patchValue({ name: 'FCI CP Test', currency: 'ARS', purchaseDate: '2026-02-15', includeInCashflow: false });
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0], id: 'new-cp', type: 'FCI_CUOTAPARTES' }));
      component.submit();
      const req = investSpy.createInvestment.calls.mostRecent().args[0];
      expect(req.includeInCashflow).toBeFalse();
    });

    it('submit de LETRA/BONO/ON envía includeInCashflow en el request', () => {
      component.openCreate();
      component.selectType('LETRA');
      component.setTrackingMode('manual');
      component.letraForm.patchValue({ name: 'LECAP Test', purchaseDate: '2026-06-01', maturityDate: '2026-12-31', includeInCashflow: false });
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[1], id: 'new-letra' }));
      component.submit();
      const req = investSpy.createInvestment.calls.mostRecent().args[0];
      expect(req.includeInCashflow).toBeFalse();
    });
  });

  // ── 3b. Selector de categoría FCI Cuotaparte ───────────────────────────────

  it('el tipo FCI se rotula "Cuenta Remunerada"', () => {
    const fci = component.typeCards.find(c => c.value === 'FCI');
    expect(fci?.label).toBe('Cuenta Remunerada');
  });

  it('filteredFunds devuelve sólo los fondos de la categoría seleccionada', () => {
    component.selectedFciCategory.set('mercadoDinero');
    expect(component.filteredFunds().length).toBeGreaterThan(0);
    expect(component.filteredFunds().every(f => f.categoria === 'mercadoDinero')).toBeTrue();

    component.selectedFciCategory.set('rentaFija');
    expect(component.filteredFunds().every(f => f.categoria === 'rentaFija')).toBeTrue();
  });

  it('setFciCategory limpia la selección de fondo previa', () => {
    component.selectFund({ fondo: 'FCI Galileo Growth', categoria: 'mercadoDinero', vcp: 1523.456, fecha: '2026-06-24' });
    expect(component.fciCPForm.controls.externalId.value).toBe('FCI Galileo Growth');

    component.setFciCategory('rentaFija');
    expect(component.selectedFciCategory()).toBe('rentaFija');
    expect(component.fciCPForm.controls.externalId.value).toBeNull();
    expect(component.fundSearchQuery()).toBe('');
  });

  it('selectType(FCI_CUOTAPARTES) preselecciona la categoría Money Market', () => {
    component.selectedFciCategory.set('rentaVariable');
    component.selectType('FCI_CUOTAPARTES');
    expect(component.selectedFciCategory()).toBe('mercadoDinero');
  });

  // ── 4. openEdit ────────────────────────────────────────────────────────────

  it('openEdit precarga letraForm con datos del activo LETRA', () => {
    const asset = MOCK_ASSETS[1]; // LECAP S31G5 — tipo LETRA
    component.openEdit(asset);
    expect(component.modal()?.kind).toBe('form-edit');
    expect(component.modal()?.id).toBe(asset.id);
    expect(component.letraForm.controls.name.value).toBe(asset.name);
    expect(component.letraForm.controls.maturityDate.value).toBe(asset.maturityDate ?? '');
    expect(component.letraForm.controls.currency.value).toBe(asset.currency);
  });

  // ── 5. submit create (PF) ────────────────────────────────────────────────

  it('submit con form válido en modo create llama a createInvestment y cierra el modal', () => {
    component.openCreate();
    component.selectType('PLAZO_FIJO');
    component.plazoFijoForm.setValue({
      name: 'PF Test', currency: 'ARS',
      principal: 500000, purchaseDate: '2026-06-01',
      dias: 30, tna: 60.0, accountId: null, includeInCashflow: true,
    });
    // Mock returns MOCK_ASSETS[0] (FCI) but we just verify the call and modal close
    investSpy.createInvestment.and.returnValue(of({
      ...MOCK_ASSETS[1], id: 'inv-new', name: 'PF Test', type: 'PLAZO_FIJO',
    }));
    component.submit();
    expect(investSpy.createInvestment).toHaveBeenCalled();
    expect(component.modal()).toBeNull();
  });

  // ── 6. submit edit (PF) ───────────────────────────────────────────────────

  it('submit en modo edit llama a updateInvestment y actualiza el activo en la lista', () => {
    const asset = MOCK_ASSETS[1]; // LETRA — tiene maturityDate → form válido
    component.openEdit(asset);
    component.submit();
    expect(investSpy.updateInvestment).toHaveBeenCalledWith(asset.id, jasmine.any(Object));
    const updated = component.assets().find(a => a.id === asset.id);
    expect(updated?.name).toBe('LECAP Actualizada');
  });

  // ── 7. confirmDelete ─────────────────────────────────────────────────────

  it('confirmDelete llama a deleteInvestment y filtra el activo de la lista', () => {
    const asset = MOCK_ASSETS[0];
    component.openDelete(asset);
    component.confirmDelete();
    expect(investSpy.deleteInvestment).toHaveBeenCalledWith(asset.id);
    const remaining = component.assets().find(a => a.id === asset.id);
    expect(remaining).toBeUndefined();
  });

  // ── 7b. Cobrar ───────────────────────────────────────────────────────────

  describe('Cobrar', () => {
    it('openCollect abre el modal "collect" con el activo seleccionado', () => {
      const asset = MOCK_ASSETS[1]; // LETRA — accountId null
      component.openCollect(asset);
      expect(component.modal()?.kind).toBe('collect');
      expect(component.modal()?.asset?.id).toBe(asset.id);
    });

    it('collectWillCreateMovement es false cuando el activo no tiene cuenta vinculada', () => {
      const asset = { ...MOCK_ASSETS[1], accountId: null };
      expect(component.collectWillCreateMovement(asset)).toBeFalse();
    });

    it('collectWillCreateMovement es true cuando hay cuenta vinculada e includeInCashflow', () => {
      const asset = { ...MOCK_ASSETS[1], accountId: 'acc-1', accountName: 'Cuenta Pesos', includeInCashflow: true };
      expect(component.collectWillCreateMovement(asset)).toBeTrue();
      expect(component.collectAccountName(asset)).toBe('Cuenta Pesos');
    });

    it('collectWillCreateMovement es false cuando includeInCashflow es false aunque haya cuenta', () => {
      const asset = { ...MOCK_ASSETS[1], accountId: 'acc-1', accountName: 'Cuenta Pesos', includeInCashflow: false };
      expect(component.collectWillCreateMovement(asset)).toBeFalse();
    });

    it('confirmCollect llama a collectInvestment, quita el activo de la lista y cierra el modal', () => {
      const asset = MOCK_ASSETS[0];
      investSpy.collectInvestment.and.returnValue(of({
        investmentId: asset.id, amount: 500000, currency: 'ARS', transactionCreated: false,
      }));
      component.openCollect(asset);
      component.confirmCollect();
      expect(investSpy.collectInvestment).toHaveBeenCalledWith(asset.id);
      expect(component.assets().find(a => a.id === asset.id)).toBeUndefined();
      expect(component.modal()).toBeNull();
    });

    it('confirmCollect surfacea err.error.message en formError cuando falla', () => {
      const asset = MOCK_ASSETS[0];
      investSpy.collectInvestment.and.returnValue(
        throwError(() => ({ error: { message: 'No se pudo cobrar: mes cerrado' } })),
      );
      component.openCollect(asset);
      component.confirmCollect();
      expect(component.formError()).toBe('No se pudo cobrar: mes cerrado');
      expect(component.assets().find(a => a.id === asset.id)).toBeDefined();
    });
  });

  // ── 8. toggleExpanded ────────────────────────────────────────────────────

  it('toggleExpanded agrega y quita un ID del Set', () => {
    const id = 'inv-1';
    component.toggleExpanded(id);
    expect(component.expanded().has(id)).toBeTrue();
    component.toggleExpanded(id);
    expect(component.expanded().has(id)).toBeFalse();
  });

  // ── 9. fmtARS ────────────────────────────────────────────────────────────

  it('fmtARS formatea números en formato argentino', () => {
    const result = component.fmtARS(1234567);
    expect(result).toContain('$');
    expect(result).toMatch(/1[\.,]234[\.,]567/);
  });

  it('fmtARSPrecio conserva decimales de precisión (2 a 4)', () => {
    const result = component.fmtARSPrecio(1001.3);
    expect(result).toContain('$');
    expect(result).not.toContain('US$');
    expect(result).toMatch(/1[\.,]001,3/);
  });

  it('fmtUSDPrecio conserva decimales de precisión (2 a 4)', () => {
    const result = component.fmtUSDPrecio(1001.3456);
    expect(result).toContain('US$');
    expect(result).toMatch(/1[\.,]001,3456/);
  });

  // ── 10. tintFor ──────────────────────────────────────────────────────────

  it('tintFor retorna colores del array rotativo', () => {
    expect(component.tintFor(0)).toBe(ASSET_TINTS[0]);
    expect(component.tintFor(1)).toBe(ASSET_TINTS[1]);
    expect(component.tintFor(ASSET_TINTS.length)).toBe(ASSET_TINTS[0]);
  });

  // ── 11. Error en carga ────────────────────────────────────────────────────

  it('error de servicio en load muestra mensaje de error', () => {
    investSpy.getInvestments.and.returnValue(throwError(() => new Error('Server error')));
    component.ngOnInit();
    fixture.detectChanges();
    expect(component.error()).toBe('No se pudieron cargar los activos');
    expect(component.loading()).toBeFalse();
    const errorEl = fixture.nativeElement.querySelector('.error-state');
    expect(errorEl).toBeTruthy();
  });

  // ── 12. selectType FCI ────────────────────────────────────────────────────

  it('selectType("FCI") cambia el modal a kind=form con assetType=FCI', () => {
    component.openCreate();
    component.selectType('FCI');
    expect(component.modal()?.kind).toBe('form-create');
    expect(component.modal()?.assetType).toBe('FCI');
    expect(component.pendingMovements().length).toBe(0);
    expect(component.showAddMovement()).toBeFalse();
  });

  // ── 13. calcTramosFCI ─────────────────────────────────────────────────────

  it('calcTramosFCI con 2 movimientos produce 2 tramos (atribución hacia adelante, sin fantasma)', () => {
    const asset = MOCK_ASSETS[0]; // FCI con 2 suscripciones (500k + 100k)
    const tramos = component.calcTramosFCI(asset);
    // Un tramo por movimiento; cada uno con el saldo RESULTANTE de aplicar ese movimiento.
    expect(tramos.length).toBe(2);
    // Primer tramo: abre con la suscripción mov-1, saldo tras aplicarla = 500k
    expect(tramos[0].mov?.id).toBe('mov-1');
    expect(tramos[0].saldo).toBe(500000);
    expect(tramos[0].enCurso).toBeFalse();
    // Segundo tramo: mov-2, saldo final = 600k, en curso (último movimiento)
    expect(tramos[1].mov?.id).toBe('mov-2');
    expect(tramos[1].saldo).toBe(600000);
    expect(tramos[1].enCurso).toBeTrue();
    // Ya no existe el tramo fantasma con mov null
    expect(tramos.some(t => t.mov === null)).toBeFalse();
  });

  it('calcTramosFCI atribuye el interés del período a la fila del movimiento que lo abre', () => {
    // Caso del usuario: suscripción y luego rescate. El interés del período en que se
    // mantuvo el saldo debe verse en la fila de la suscripción, no en la del rescate.
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      tna: 19,
      movements: [
        { id: 's1', movementDate: '2026-05-01', type: 'SUSCRIPCION', amount: 1000000, units: null, createdAt: '' },
        { id: 'r1', movementDate: '2026-06-29', type: 'RESCATE',     amount: 500000,  units: null, createdAt: '' },
      ],
    };
    const tramos = component.calcTramosFCI(asset);
    expect(tramos.length).toBe(2);
    // Tramo de la suscripción: saldo 1.000.000, 59 días, interés ~30.726
    expect(tramos[0].mov?.type).toBe('SUSCRIPCION');
    expect(tramos[0].saldo).toBe(1000000);
    expect(tramos[0].dias).toBe(59);
    expect(tramos[0].intereses).toBeCloseTo(1000000 * 0.19 * 59 / 365, 2);
    expect(tramos[0].enCurso).toBeFalse();
    // Tramo del rescate: saldo 500.000, en curso, sin arrastrar el resultado anterior
    expect(tramos[1].mov?.type).toBe('RESCATE');
    expect(tramos[1].saldo).toBe(500000);
    expect(tramos[1].enCurso).toBeTrue();
    // La sumatoria de intereses suma todos los tramos
    const totalInt = tramos.reduce((a, t) => a + t.intereses, 0);
    expect(component.calcIntereses(asset)).toBeCloseTo(totalInt, 6);
  });

  // ── Cuenta Remunerada: override de interés y crear tramo ─────────────────

  it('calcTramosFCI usa el interestOverride de un tramo SUSCRIPCION en lugar de la proyección por TNA', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      tna: 19,
      movements: [
        { id: 's1', movementDate: '2026-05-01', type: 'SUSCRIPCION', amount: 1000000, units: null, interestOverride: 8000, createdAt: '' },
        // Rescate por el saldo total → el tramo en curso queda con saldo 0 (test independiente de la fecha)
        { id: 'r1', movementDate: '2026-06-29', type: 'RESCATE',     amount: 1000000, units: null, createdAt: '' },
      ],
    };
    const tramos = component.calcTramosFCI(asset);
    // El tramo de la suscripción muestra el override, no la proyección por TNA
    expect(tramos[0].mov?.type).toBe('SUSCRIPCION');
    expect(tramos[0].intereses).toBe(8000);
    // El otro tramo (rescate) no se ve afectado por el override; con saldo 0 no devenga interés
    expect(tramos[1].mov?.type).toBe('RESCATE');
    expect(tramos[1].intereses).toBe(0);
    // El total de intereses refleja el override
    expect(component.calcIntereses(asset)).toBeCloseTo(8000, 6);
  });

  it('displayValuations colapsa las automáticas a una por mes y conserva TODAS las manuales', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[1],
      valuations: [
        // Automáticas diarias de marzo → sólo la última (03-31) debe quedar
        { id: 'a1', valuationDate: '2026-03-05', pricePerUnit: 100, source: 'ARGENTINADATOS', createdAt: '' },
        { id: 'a2', valuationDate: '2026-03-20', pricePerUnit: 101, source: 'ARGENTINADATOS', createdAt: '' },
        { id: 'a3', valuationDate: '2026-03-31', pricePerUnit: 102, source: 'ARGENTINADATOS', createdAt: '' },
        // Manuales → todas
        { id: 'm1', valuationDate: '2026-03-10', pricePerUnit: 150, source: 'MANUAL', createdAt: '' },
        { id: 'm2', valuationDate: '2026-04-15', pricePerUnit: 160, source: 'MANUAL', createdAt: '' },
      ],
    };
    const shown = component.displayValuations(asset);
    // 1 automática (03-31) + 2 manuales = 3
    expect(shown.length).toBe(3);
    expect(shown.map(v => v.id)).toEqual(['m1', 'a3', 'm2']); // ordenadas por fecha
    expect(shown.find(v => v.id === 'a1')).toBeUndefined();
    expect(shown.find(v => v.id === 'a2')).toBeUndefined();
  });

  it('submitEditTramo en un tramo SUSCRIPCION guarda interestOverride (no amount)', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      tna: 19,
      movements: [
        { id: 's1', movementDate: '2026-05-01', type: 'SUSCRIPCION', amount: 1000000, units: null, createdAt: '' },
      ],
    };
    component.assets.set([asset]);
    investSpy.updateMovement.and.returnValue(of(asset));

    const tramo = component.calcTramosFCI(asset)[0];
    component.openEditTramoModal(asset, tramo);
    component.editTramoForm.setValue({ intereses: 5000 });
    component.submitEditTramo();

    expect(investSpy.updateMovement).toHaveBeenCalledWith('inv-1', 's1', { interestOverride: 5000 });
  });

  it('restoreTramoTNA limpia el override (interestOverride null)', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      movements: [
        { id: 's1', movementDate: '2026-05-01', type: 'SUSCRIPCION', amount: 1000000, units: null, interestOverride: 5000, createdAt: '' },
      ],
    };
    component.assets.set([asset]);
    investSpy.updateMovement.and.returnValue(of(asset));

    const tramo = component.calcTramosFCI(asset)[0];
    component.openEditTramoModal(asset, tramo);
    component.restoreTramoTNA();

    expect(investSpy.updateMovement).toHaveBeenCalledWith('inv-1', 's1', { interestOverride: null });
  });

  it('al elegir REVALUO en el modal de movimiento FCI precarga el interés por TNA a la fecha elegida', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      tna: 19,
      movements: [
        { id: 's1', movementDate: '2026-05-01', type: 'SUSCRIPCION', amount: 1000000, units: null, createdAt: '' },
      ],
    };
    component.openAddMovementModal(asset);
    component.addMovementForm.controls.movementDate.setValue('2026-06-29');
    component.addMovementForm.controls.type.setValue('REVALUO');

    // 59 días entre 01/05 y 29/06; interés = 1.000.000 × 19% × 59/365 ≈ 30.712,33
    const esperado = Math.round(1000000 * 0.19 * 59 / 365 * 100) / 100;
    expect(component.addMovementForm.controls.amount.value).toBe(esperado);
  });

  it('la precarga de REVALUO se recalcula al cambiar la fecha y no pisa tipos SUSCRIPCION/RESCATE', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      tna: 19,
      movements: [
        { id: 's1', movementDate: '2026-05-01', type: 'SUSCRIPCION', amount: 1000000, units: null, createdAt: '' },
      ],
    };
    component.openAddMovementModal(asset);

    // En SUSCRIPCION no se precarga nada.
    component.addMovementForm.controls.movementDate.setValue('2026-06-29');
    expect(component.addMovementForm.controls.amount.value).toBeNull();

    // Al pasar a REVALUO se precarga; al mover la fecha se recalcula.
    component.addMovementForm.controls.type.setValue('REVALUO');
    const a59 = component.addMovementForm.controls.amount.value;
    component.addMovementForm.controls.movementDate.setValue('2026-05-31');
    const a30 = component.addMovementForm.controls.amount.value;
    expect(a59).toBeCloseTo(Math.round(1000000 * 0.19 * 59 / 365 * 100) / 100, 2);
    expect(a30).toBeCloseTo(Math.round(1000000 * 0.19 * 30 / 365 * 100) / 100, 2);
  });

  it('un interestOverride en un movimiento LETRA no afecta los tramos CP (aislamiento)', () => {
    const base: InvestmentResponse = { ...MOCK_ASSETS[1] };
    const conOverride: InvestmentResponse = {
      ...base,
      movements: base.movements.map(m => ({ ...m, interestOverride: 99999 })),
    };
    // La ganancia CP y los tramos CP son idénticos con o sin el campo override
    expect(component.calcGananciaCP(conOverride)).toBeCloseTo(component.calcGananciaCP(base), 6);
    expect(component.calcTramosCP(conOverride).length).toBe(component.calcTramosCP(base).length);
  });

  it('una valuación en la misma fecha de una suscripción (precio implícito) no altera los tramos CP (aislamiento)', () => {
    const base: InvestmentResponse = { ...MOCK_ASSETS[1] };
    const susc = base.movements[0];
    const implied = susc.amount / (susc.units ?? 1);
    const conVal: InvestmentResponse = {
      ...base,
      valuations: [
        { id: 'vsusc', valuationDate: susc.movementDate, pricePerUnit: implied, source: 'MANUAL', createdAt: '' },
      ],
    };
    const tramosBase   = component.calcTramosCP(base);
    const tramosConVal = component.calcTramosCP(conVal);
    const sumGanancia  = (ts: { ganancia: number }[]) => ts.reduce((a, t) => a + t.ganancia, 0);

    expect(tramosConVal.length).toBe(tramosBase.length);
    expect(sumGanancia(tramosConVal)).toBeCloseTo(sumGanancia(tramosBase), 6);
  });

  // ── Tramo de cierre de mes (auto-generado) ───────────────────────────────

  it('calcTramosFCI expone el flag autoGenerated del REVALUO de cierre sin alterar el cálculo', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      tna: 19,
      movements: [
        { id: 's1', movementDate: '2026-05-01', type: 'SUSCRIPCION', amount: 1000000, units: null, createdAt: '' },
        { id: 'rv', movementDate: '2026-05-31', type: 'REVALUO',     amount: 30000, units: null, autoGenerated: true, createdAt: '' },
      ],
    };
    const tramos = component.calcTramosFCI(asset);
    // Atribución hacia atrás: el REVALUO cierra el tramo de la suscripción, cuyo interés
    // es el monto realizado del revalúo (no la proyección por TNA).
    const cerrado = tramos.find(t => t.mov?.id === 's1');
    expect(cerrado).toBeDefined();
    expect(cerrado!.intereses).toBe(30000);
    expect(cerrado!.closingRevaluo?.id).toBe('rv');
    expect(cerrado!.closingRevaluo?.autoGenerated).toBeTrue();
    // El tramo que abre el propio REVALUO devenga por TNA sobre el saldo capitalizado (1.030.000).
    const abierto = tramos.find(t => t.mov?.id === 'rv');
    expect(abierto).toBeDefined();
    expect(abierto!.saldo).toBe(1030000);
    expect(abierto!.intereses).toBeCloseTo(1030000 * 0.19 * (abierto!.dias / 365), 2);
  });

  it('calcTramosCP marca endAutoGenerated en la valuación de cierre de mes', () => {
    const base: InvestmentResponse = { ...MOCK_ASSETS[1] };
    const conCierre: InvestmentResponse = {
      ...base,
      valuations: [
        ...base.valuations,
        { id: 'vsys', valuationDate: '2026-05-31', pricePerUnit: base.valuations[0]?.pricePerUnit ?? 1, source: 'SYSTEM', autoGenerated: true, createdAt: '' },
      ],
    };
    const tramoCierre = component.calcTramosCP(conCierre).find(t => t.endDate === '2026-05-31');
    expect(tramoCierre).toBeDefined();
    expect(tramoCierre!.endAutoGenerated).toBeTrue();
  });

  // ── 14. calcSaldoFCI ─────────────────────────────────────────────────────

  it('calcSaldoFCI con suscripción $500k + rescate $100k devuelve $400k', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      movements: [
        { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 500000, units: null, createdAt: '2026-01-01T00:00:00Z' },
        { id: 'm2', movementDate: '2026-02-01', type: 'RESCATE',     amount: 100000, units: null, createdAt: '2026-02-01T00:00:00Z' },
      ],
    };
    expect(component.calcSaldoFCI(asset)).toBe(400000);
  });

  // ── 15. calcInteresesFCI con TNA 0 ───────────────────────────────────────

  it('calcInteresesFCI con TNA 0 devuelve 0', () => {
    const asset: InvestmentResponse = {
      ...MOCK_ASSETS[0],
      tna: 0,
      movements: [
        { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 500000, units: null, createdAt: '2026-01-01T00:00:00Z' },
      ],
    };
    const tramos = component.calcTramosFCI(asset);
    const intereses = tramos.reduce((acc, t) => acc + t.intereses, 0);
    expect(intereses).toBe(0);
  });

  // ── 16. FCI_CUOTAPARTES ───────────────────────────────────────────────────

  describe('FCI_CUOTAPARTES', () => {
    let asset: InvestmentResponse;

    beforeEach(() => {
      asset = {
        id: 'cp-1', name: 'FCI CP Test', type: 'FCI_CUOTAPARTES', currency: 'ARS',
        principal: 700000, purchaseDate: '2026-01-01', maturityDate: null, tna: 0,
        accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 500000, units: 400, createdAt: '' },
          { id: 'm2', movementDate: '2026-03-15', type: 'SUSCRIPCION', amount: 200000, units: 150, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: '2026-06-01', pricePerUnit: 1500, source: 'MANUAL', createdAt: '' },
        ],
      };
    });

    it('selectType FCI_CUOTAPARTES abre modal con assetType correcto', () => {
      component.openCreate();
      component.selectType('FCI_CUOTAPARTES');
      expect(component.modal()?.assetType).toBe('FCI_CUOTAPARTES');
    });

    it('calcCuotapartesHeld devuelve 550 con 2 suscripciones sin rescates', () => {
      expect(component.calcCuotapartesHeld(asset)).toBe(550);
    });

    it('calcValorActualCP = 550 CP × $1500 = $825.000', () => {
      expect(component.calcValorActualCP(asset)).toBeCloseTo(825000, 0);
    });

    it('calcGananciaCP = valor_actual - principal', () => {
      expect(component.calcGananciaCP(asset)).toBeCloseTo(825000 - 700000, 0);
    });

    it('calcTramosCP devuelve al menos 2 tramos y el último está en curso', () => {
      const tramos = component.calcTramosCP(asset);
      expect(tramos.length).toBeGreaterThanOrEqual(2);
      expect(tramos[tramos.length - 1].enCurso).toBeTrue();
    });

    it('calcTramosCP: el tramo en curso usa la última valuación conocida como endPrice (no repite prevPrice fijo)', () => {
      // Suscripción muy anterior (precio implícito $500) + valuación automática de hoy a $600:
      // no forma un límite de tramo (es del mes en curso), pero sí es la última valuación conocida,
      // que ahora debe reflejarse como endPrice del tramo en curso.
      const today = new Date();
      const todayStr = [
        today.getFullYear(),
        String(today.getMonth() + 1).padStart(2, '0'),
        String(today.getDate()).padStart(2, '0'),
      ].join('-');
      const assetHoy: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2025-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: todayStr, pricePerUnit: 600, source: 'ARGENTINADATOS' as const, createdAt: '' },
        ],
      };
      const tramos = component.calcTramosCP(assetHoy);
      const enCurso = tramos.find(t => t.enCurso);
      expect(enCurso).toBeDefined();
      expect(enCurso!.endPrice).toBeCloseTo(600, 4);
      // Precio implícito de arranque ($500) distinto al de hoy ($600) → ganancia/tna/tea ya no son 0 fijos.
      expect(enCurso!.ganancia).toBeCloseTo(200 * (600 - 500), 2);
      expect(enCurso!.tna).not.toBe(0);
      expect(enCurso!.tea).not.toBe(0);
    });

    it('calcTramosCP excluye la valuación AUTOMÁTICA del mes en curso: el tramo en curso cubre el mes actual', () => {
      const today = new Date();
      const todayStr = [
        today.getFullYear(),
        String(today.getMonth() + 1).padStart(2, '0'),
        String(today.getDate()).padStart(2, '0'),
      ].join('-');
      const assetHoy: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2025-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: todayStr, pricePerUnit: 600, source: 'ARGENTINADATOS' as const, createdAt: '' },
        ],
      };
      const tramos = component.calcTramosCP(assetHoy);
      // La valuación automática de hoy (mes en curso) no genera un límite mensual; el tramo en curso la cubre.
      expect(tramos.some(t => t.enCurso)).toBeTrue();
      expect(tramos.every(t => t.dias > 0)).toBeTrue();
    });

    it('calcTramosCP incluye una valuación MANUAL del mes en curso como tramo (regla del usuario)', () => {
      const today = new Date();
      const yesterday = new Date(today.getTime() - 86400000);
      const iso = (d: Date) => [
        d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0'),
      ].join('-');
      // Suscripción muy anterior + valuación manual AYER (mes en curso) → debe cerrar un tramo a esa fecha.
      const assetManualHoy: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2024-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: iso(yesterday), pricePerUnit: 600, source: 'MANUAL' as const, createdAt: '' },
        ],
      };
      const tramos = component.calcTramosCP(assetManualHoy);
      // El corte manual de ayer genera un tramo cerrado con esa fecha, además del tramo en curso.
      expect(tramos.some(t => !t.enCurso && t.endDate === iso(yesterday))).toBeTrue();
      expect(tramos.some(t => t.enCurso)).toBeTrue();
    });

    it('calcTramosCP agrupa valuaciones AUTOMÁTICAS diarias en un tramo por mes (no uno por día)', () => {
      // Suscripción en febrero + ~5 valuaciones diarias en marzo y ~5 en abril (meses pasados).
      const dailyMar = ['2026-03-02', '2026-03-09', '2026-03-16', '2026-03-23', '2026-03-30']
        .map((d, i) => ({ id: `vm${i}`, valuationDate: d, pricePerUnit: 100 + i, source: 'ARGENTINADATOS' as const, createdAt: '' }));
      const dailyApr = ['2026-04-06', '2026-04-13', '2026-04-20', '2026-04-27', '2026-04-30']
        .map((d, i) => ({ id: `va${i}`, valuationDate: d, pricePerUnit: 110 + i, source: 'ARGENTINADATOS' as const, createdAt: '' }));
      const assetDiario: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2026-02-01', type: 'SUSCRIPCION', amount: 100000, units: 1000, createdAt: '' },
        ],
        valuations: [...dailyMar, ...dailyApr],
      };
      const tramos = component.calcTramosCP(assetDiario);
      // 2 límites mensuales (cierre de marzo + cierre de abril) + tramo en curso = 3 tramos, no 10.
      expect(tramos.length).toBe(3);
      // El cierre de cada mes usa la última valuación de ese mes.
      expect(tramos.find(t => t.endDate === '2026-03-30')).toBeDefined();
      expect(tramos.find(t => t.endDate === '2026-04-30')).toBeDefined();
      expect(tramos[tramos.length - 1].enCurso).toBeTrue();
    });

    it('calcTramosCP agrega un corte extra cuando hay una operación a mitad de mes', () => {
      const dailyMar = ['2026-03-10', '2026-03-20', '2026-03-31']
        .map((d, i) => ({ id: `vm${i}`, valuationDate: d, pricePerUnit: 100 + i, source: 'ARGENTINADATOS' as const, createdAt: '' }));
      const sinOperacion: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2026-02-01', type: 'SUSCRIPCION', amount: 100000, units: 1000, createdAt: '' },
        ],
        valuations: dailyMar,
      };
      const conOperacion: InvestmentResponse = {
        ...sinOperacion,
        movements: [
          ...sinOperacion.movements,
          { id: 'm2', movementDate: '2026-03-15', type: 'SUSCRIPCION', amount: 20000, units: 200, createdAt: '' },
        ],
      };
      // La suscripción del 15/03 parte el mes de marzo en dos tramos.
      expect(component.calcTramosCP(conOperacion).length)
        .toBe(component.calcTramosCP(sinOperacion).length + 1);
    });

    it('calcTramosCP ignora una valuación futura (no genera ganancia adelantada) y deja el tramo en curso', () => {
      const assetFuturo: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2025-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: '2099-01-01', pricePerUnit: 999, source: 'MANUAL' as const, createdAt: '' },
        ],
      };
      const tramos = component.calcTramosCP(assetFuturo);
      // La valuación futura se descarta: no hay tramo "completado" adelantado; queda el tramo en curso.
      expect(tramos.some(t => t.enCurso)).toBeTrue();
      expect(tramos.every(t => t.dias > 0)).toBeTrue();
      // No debe existir ningún tramo con la fecha de la valuación futura descartada.
      expect(tramos.some(t => t.endDate === '2099-01-01')).toBeFalse();
    });

    // ── REVALUO en FCI_CUOTAPARTES (retrocompatibilidad) ─────────────────────
    it('calcTramosCP con REVALUO movement merges correctamente y popula revaluoMovId', () => {
      const assetConRevaluo: InvestmentResponse = {
        ...asset,
        autoTrack: false,
        externalId: null,
        valuations: [],   // sin valuaciones externas
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 200000, units: 1000, createdAt: '' },
          { id: 'r1', movementDate: '2026-04-01', type: 'REVALUO',     amount: 15000,  units: null, createdAt: '' },
        ],
      };
      const tramos = component.calcTramosCP(assetConRevaluo);
      // Debe haber: 1 tramo de suscripción a REVALUO + 1 en curso = 2 tramos
      expect(tramos.length).toBe(2);
      const tramoRevaluo = tramos[0];
      expect(tramoRevaluo.ganancia).toBeCloseTo(15000, 0);
      expect(tramoRevaluo.revaluoMovId).toBe('r1');
      expect(tramoRevaluo.valuacionId).toBeUndefined();
      expect(tramos[1].enCurso).toBeTrue();
    });

    // ── Rendimiento efectivo y TIR acumulada por tramo ────────────────────────

    it('calcTramosCP: el tramo en curso tiene tirAcumulada exactamente igual a calcTIR(asset)', () => {
      const tramos = component.calcTramosCP(asset);
      const enCurso = tramos[tramos.length - 1];
      expect(enCurso.enCurso).toBeTrue();
      expect(enCurso.tirAcumulada).toBe(component.calcTIR(asset));
    });

    it('calcTramosCP: un tramo cerrado intermedio tiene tasaEfectiva = (ganancia/capitalInicio)×100, distinta de tna/tea', () => {
      const tramos = component.calcTramosCP(asset);
      const cerrado = tramos.find(t => !t.enCurso);
      expect(cerrado).toBeDefined();
      const capitalInicio = cerrado!.units * cerrado!.startPrice;
      const tasaEsperada = capitalInicio > 0 ? (cerrado!.ganancia / capitalInicio) * 100 : 0;
      expect(cerrado!.tasaEfectiva).toBeCloseTo(tasaEsperada, 6);
      if (cerrado!.dias !== 365) {
        expect(cerrado!.tasaEfectiva).not.toBeCloseTo(cerrado!.tna, 6);
        expect(cerrado!.tasaEfectiva).not.toBeCloseTo(cerrado!.tea, 6);
      }
    });

    it('tirAcumuladaCP con una sola suscripción y valorFinal = capital inicial da TIR ≈ 0', () => {
      const assetUnaSuscripcion: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [],
      };
      const tir = (component as any).tirAcumuladaCP(assetUnaSuscripcion, '2026-06-01', 100000);
      expect(tir).toBeCloseTo(0, 4);
    });

    it('tirAcumuladaCP con movements vacíos hasta hastaFecha devuelve 0', () => {
      const assetSinMovs: InvestmentResponse = {
        ...asset,
        movements: [
          { id: 'm1', movementDate: '2026-09-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [],
      };
      // Todos los movimientos son posteriores a hastaFecha → no quedan movimientos en el filtro.
      const tir = (component as any).tirAcumuladaCP(assetSinMovs, '2026-01-01', 100000);
      expect(tir).toBe(0);
    });
  });

  // ── REVALUO en FCI Money Market ───────────────────────────────────────────

  describe('REVALUO en FCI', () => {
    let assetConRevaluo: InvestmentResponse;

    beforeEach(() => {
      assetConRevaluo = {
        ...MOCK_ASSETS[0],
        tna: 65.5,
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 500000, units: null, createdAt: '' },
          { id: 'r1', movementDate: '2026-04-01', type: 'REVALUO',     amount: 15000,  units: null, createdAt: '' },
        ],
      };
    });

    it('calcSaldoFCI incluye REVALUO en el NAV: 500k + 15k = 515k', () => {
      expect(component.calcSaldoFCI(assetConRevaluo)).toBeCloseTo(515000, 0);
    });

    it('calcTramosFCI atribuye el amount del REVALUO al tramo que CIERRA (el anterior)', () => {
      const tramos = component.calcTramosFCI(assetConRevaluo);
      // El tramo de la suscripción (01/01 → 01/04) lo cierra el revalúo: interés realizado 15.000.
      const tramoSusc = tramos.find(t => t.mov?.id === 'm1');
      expect(tramoSusc).toBeDefined();
      expect(tramoSusc!.intereses).toBeCloseTo(15000, 0);
      expect(tramoSusc!.closingRevaluo?.id).toBe('r1');
      // El tramo en curso (abierto por el revalúo) proyecta por TNA sobre el saldo capitalizado.
      const encurso = tramos.find(t => t.enCurso);
      expect(encurso).toBeDefined();
      expect(encurso!.mov?.type).toBe('REVALUO');
      expect(encurso!.saldo).toBeCloseTo(515000, 0);
      expect(encurso!.intereses).toBeCloseTo(515000 * 0.655 * (encurso!.dias / 365), 2);
    });

    it('calcIntereses suma TODOS los intereses generados: el realizado por REVALUO + la proyección en curso', () => {
      const tramos = component.calcTramosFCI(assetConRevaluo);
      const encurso = tramos.find(t => t.enCurso)!;
      expect(component.calcIntereses(assetConRevaluo)).toBeCloseTo(15000 + encurso.intereses, 6);
    });

    it('calcMontoTotal = saldo capitalizado + interés del tramo en curso (no re-suma el revalúo)', () => {
      const encurso = component.calcTramosFCI(assetConRevaluo).find(t => t.enCurso)!;
      expect(component.calcMontoTotal(assetConRevaluo)).toBeCloseTo(515000 + encurso.intereses, 6);
    });

    it('editar el tramo cerrado por un REVALUO actualiza el amount de ese revalúo (capitaliza)', () => {
      component.assets.set([assetConRevaluo]);
      investSpy.updateMovement.and.returnValue(of(assetConRevaluo));

      const tramoSusc = component.calcTramosFCI(assetConRevaluo).find(t => t.mov?.id === 'm1')!;
      component.openEditTramoModal(assetConRevaluo, tramoSusc);
      component.editTramoForm.setValue({ intereses: 20000 });
      component.submitEditTramo();

      expect(investSpy.updateMovement).toHaveBeenCalledWith('inv-1', 'r1', { amount: 20000 });
    });
  });

  describe('LETRA/BONO/ON — comportamiento tipo cuotapartes', () => {
    function buildLetraAsset(): InvestmentResponse {
      return {
        id: 'letra-1', name: 'LECAP S31G5', type: 'LETRA', currency: 'ARS',
        principal: 97000,       // 1000 nominales a $97
        purchaseDate: '2026-01-01', maturityDate: '2026-12-31',
        tna: 0, accountId: null, accountName: null,
        autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 97000, units: 1000, createdAt: '2026-01-01T00:00:00Z' },
        ],
        valuations: [
          { id: 'v1', valuationDate: '2026-02-01', pricePerUnit: 98,  source: 'MANUAL', createdAt: '2026-02-01T00:00:00Z' },
          { id: 'v2', valuationDate: '2026-03-01', pricePerUnit: 100, source: 'MANUAL', createdAt: '2026-03-01T00:00:00Z' },
        ],
      };
    }

    it('calcValorActualCP usa la última valuación × nominales: 1000 × $100 = $100.000', () => {
      const asset = buildLetraAsset();
      const valor = component.calcValorActualCP(asset);
      expect(valor).toBeCloseTo(100000, 0);
    });

    it('calcGananciaCP = valor actual − costo base: $100.000 − $97.000 = $3.000', () => {
      const asset = buildLetraAsset();
      const ganancia = component.calcGananciaCP(asset);
      expect(ganancia).toBeCloseTo(3000, 0);
    });

    it('calcTramosCP para LETRA devuelve tramos basados en valuaciones con enCurso al final', () => {
      const asset = buildLetraAsset();
      const tramos = component.calcTramosCP(asset);
      expect(tramos.length).toBeGreaterThanOrEqual(2);
      const enCurso = tramos.find(t => t.enCurso);
      expect(enCurso).toBeDefined();
      // Primer tramo cerrado debe tener valuacionId poblado
      const cerrado = tramos.find(t => !t.enCurso);
      expect(cerrado?.valuacionId).toBeTruthy();
      expect(cerrado?.revaluoMovId).toBeUndefined();
    });

    it('calcCuotapartesHeld para LETRA suma units de SUSCRIPCION y descuenta RESCATE', () => {
      const asset = buildLetraAsset();
      // 1000 suscriptas — sin rescates
      expect(component.calcCuotapartesHeld(asset)).toBe(1000);

      // Agrego un rescate de 200 nominales
      asset.movements.push(
        { id: 'm2', movementDate: '2026-04-01', type: 'RESCATE', amount: 20000, units: 200, createdAt: '2026-04-01T00:00:00Z' },
      );
      expect(component.calcCuotapartesHeld(asset)).toBe(800);
    });
  });

  // ── subModal ──────────────────────────────────────────────────────────────

  describe('subModal', () => {

    it('openAddMovementModal setea subModal con el asset correcto', () => {
      const asset = MOCK_ASSETS[0];
      component.openAddMovementModal(asset);
      expect(component.subModal()?.kind).toBe('add-movement');
      expect(component.subModal()?.asset).toBe(asset);
      expect(component.movFormError()).toBeNull();
    });

    it('openAddValuationModal setea subModal add-valuation', () => {
      const asset = MOCK_ASSETS[0];
      component.openAddValuationModal(asset);
      expect(component.subModal()?.kind).toBe('add-valuation');
      expect(component.subModal()?.asset).toBe(asset);
    });

    it('addPendingMovementCP con type=REVALUO registra una valuación (corte de precio) y NO addMovement', () => {
      const cpAsset: InvestmentResponse = {
        ...MOCK_ASSETS[0],
        type: 'FCI_CUOTAPARTES',
        autoTrack: false,
        externalId: null,
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [],
      };
      investSpy.addValuation.and.returnValue(of({ ...cpAsset }));

      component.openAddMovementModal(cpAsset);
      component.addMovementCPForm.patchValue({ movementDate: '2026-07-01', type: 'REVALUO', amount: null, units: null, pricePerUnit: 525 });
      component.addPendingMovementCP();

      expect(investSpy.addValuation).toHaveBeenCalledWith(
        cpAsset.id,
        jasmine.objectContaining({ valuationDate: '2026-07-01', pricePerUnit: 525 }),
      );
      expect(investSpy.addMovement).not.toHaveBeenCalled();
      expect(component.subModal()).toBeNull();
    });

    it('addPendingMovementCP con REVALUO en fecha ya valuada actualiza la valuación (upsert)', () => {
      const cpAsset: InvestmentResponse = {
        ...MOCK_ASSETS[0],
        type: 'FCI_CUOTAPARTES',
        autoTrack: false,
        externalId: null,
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [
          { id: 'v1', valuationDate: '2026-07-01', pricePerUnit: 500, source: 'MANUAL', createdAt: '' },
        ],
      };
      investSpy.updateValuation.and.returnValue(of({ ...cpAsset }));

      component.openAddMovementModal(cpAsset);
      component.addMovementCPForm.patchValue({ movementDate: '2026-07-01', type: 'REVALUO', amount: null, units: null, pricePerUnit: 525 });
      component.addPendingMovementCP();

      expect(investSpy.updateValuation).toHaveBeenCalledWith(
        cpAsset.id, 'v1',
        jasmine.objectContaining({ valuationDate: '2026-07-01', pricePerUnit: 525 }),
      );
      expect(investSpy.addValuation).not.toHaveBeenCalled();
    });

    it('_seedTodayValuation persiste la valuación PPI en la FECHA REAL del cierre (no hoy)', () => {
      const asset: InvestmentResponse = {
        ...MOCK_ASSETS[1], // LETRA
        autoTrack: true,
        externalId: 'S31L6',
        valuations: [],
      };
      // Cierre real de un instrumento ilíquido: hace semanas, no hoy.
      investSpy.getInstrumentPrice.and.returnValue(of({ pricePerUnit: 1650, fecha: '2026-05-22' }));
      investSpy.addValuation.and.returnValue(of(asset));

      component['_seedTodayValuation'](asset);

      expect(investSpy.addValuation).toHaveBeenCalledWith(
        asset.id,
        jasmine.objectContaining({ valuationDate: '2026-05-22', pricePerUnit: 1650 }),
      );
    });

    it('_seedTodayValuation NO re-persiste si ya existe una valuación en esa fecha (evita 409)', () => {
      const asset: InvestmentResponse = {
        ...MOCK_ASSETS[1],
        autoTrack: true,
        externalId: 'S31L6',
        valuations: [
          { id: 'v', valuationDate: '2026-05-22', pricePerUnit: 1650, source: 'MANUAL', createdAt: '' },
        ],
      };
      investSpy.getInstrumentPrice.and.returnValue(of({ pricePerUnit: 1650, fecha: '2026-05-22' }));
      investSpy.addValuation.calls.reset();

      component['_seedTodayValuation'](asset);

      expect(investSpy.addValuation).not.toHaveBeenCalled();
    });

    it('addPendingMovementCP con SUSCRIPCION envía pricePerUnit en el payload de addMovement', () => {
      const cpAsset: InvestmentResponse = {
        ...MOCK_ASSETS[0],
        type: 'FCI_CUOTAPARTES',
        autoTrack: false,
        externalId: null,
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 100000, units: 200, createdAt: '' },
        ],
        valuations: [],
      };
      investSpy.addMovement.and.returnValue(of({ ...cpAsset }));

      component.openAddMovementModal(cpAsset);
      component.addMovementCPForm.patchValue({
        movementDate: '2026-03-15', type: 'SUSCRIPCION', amount: 500000, units: 400, pricePerUnit: 1250.5,
      });
      component.addPendingMovementCP();

      expect(investSpy.addMovement).toHaveBeenCalledWith(
        cpAsset.id,
        jasmine.objectContaining({ movementDate: '2026-03-15', type: 'SUSCRIPCION', pricePerUnit: 1250.5 }),
      );
    });

    it('closeSubModal limpia subModal y movFormError', () => {
      const asset = MOCK_ASSETS[0];
      component.openAddMovementModal(asset);
      component.movFormError.set('Error de prueba');
      component.closeSubModal();
      expect(component.subModal()).toBeNull();
      expect(component.movFormError()).toBeNull();
    });

    it('addPendingMovement desde subModal persiste inmediatamente y cierra subModal', () => {
      const asset = MOCK_ASSETS[0];
      const updatedAsset: InvestmentResponse = {
        ...asset,
        movements: [
          ...MOCK_FCI_MOVEMENTS,
          { id: 'mov-3', movementDate: '2026-03-01', type: 'SUSCRIPCION', amount: 200000, units: null, createdAt: '2026-03-01T00:00:00Z' },
        ],
      };
      investSpy.addMovement.and.returnValue(of(updatedAsset));

      component.openAddMovementModal(asset);
      component.addMovementForm.setValue({ movementDate: '2026-03-01', type: 'SUSCRIPCION', amount: 200000, units: null, pricePerUnit: null });
      component.addPendingMovement();

      expect(investSpy.addMovement).toHaveBeenCalledWith(
        asset.id,
        jasmine.objectContaining({ type: 'SUSCRIPCION', amount: 200000 }),
      );
      expect(component.subModal()).toBeNull();
      const updated = component.assets().find(a => a.id === asset.id);
      expect(updated?.movements.length).toBe(3);
    });

  });

  // ── form-create sin movimientos ───────────────────────────────────────────

  describe('form-create sin movimientos', () => {

    it('submitFCI en create llama createInvestment sin requerir pendingMovements y cierra modal', () => {
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0], id: 'new-fci' }));
      component.openCreate();
      component.selectType('FCI');
      component.fciForm.setValue({ name: 'FCI Test', currency: 'ARS', tna: 65.0, purchaseDate: component['todayIso'](), initialAmount: null, accountId: null, includeInCashflow: true });
      component.submit();
      expect(investSpy.createInvestment).toHaveBeenCalled();
      expect(component.modal()).toBeNull();
    });

  });

  // ── Cuenta Remunerada (FCI) — fecha de apertura + depósito inicial ──────────

  describe('Cuenta Remunerada — backdating', () => {

    it('selectType(FCI) deja la fecha de apertura en hoy e initialAmount en null', () => {
      component.openCreate();
      component.selectType('FCI');
      expect(component.fciForm.controls.purchaseDate.value).toBe(component['todayIso']());
      expect(component.fciForm.controls.initialAmount.value).toBeNull();
    });

    it('fciForm es inválido sin fecha de apertura', () => {
      component.openCreate();
      component.selectType('FCI');
      component.fciForm.patchValue({ name: 'FCI X', tna: 60, purchaseDate: '' });
      expect(component.fciForm.controls.purchaseDate.invalid).toBeTrue();
      expect(component.fciForm.invalid).toBeTrue();
    });

    it('submit FCI envía la fecha de apertura elegida al createInvestment', () => {
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0], id: 'new-fci' }));
      component.openCreate();
      component.selectType('FCI');
      component.fciForm.patchValue({ name: 'FCI Pasada', currency: 'ARS', tna: 65, purchaseDate: '2026-02-15' });
      component.submit();
      const req = investSpy.createInvestment.calls.mostRecent().args[0];
      expect(req.purchaseDate).toBe('2026-02-15');
      expect(req.type).toBe('FCI');
    });

    it('con monto inicial, submit crea la suscripción inicial en la fecha de apertura', () => {
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0], id: 'new-fci' }));
      investSpy.addMovement.and.returnValue(of({ ...MOCK_ASSETS[0], id: 'new-fci' }));
      component.openCreate();
      component.selectType('FCI');
      component.fciForm.patchValue({ name: 'FCI Pasada', currency: 'ARS', tna: 65, purchaseDate: '2026-02-15', initialAmount: 300000 });
      component.submit();
      expect(investSpy.addMovement).toHaveBeenCalled();
      const [id, mov] = investSpy.addMovement.calls.mostRecent().args;
      expect(id).toBe('new-fci');
      expect(mov.movementDate).toBe('2026-02-15');
      expect(mov.type).toBe('SUSCRIPCION');
      expect(mov.amount).toBe(300000);
    });

    it('sin monto inicial, submit NO crea movimiento', () => {
      investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0], id: 'new-fci' }));
      investSpy.addMovement.calls.reset();
      component.openCreate();
      component.selectType('FCI');
      component.fciForm.patchValue({ name: 'FCI Vacía', currency: 'ARS', tna: 65, purchaseDate: '2026-02-15' });
      component.submit();
      expect(investSpy.addMovement).not.toHaveBeenCalled();
    });

  });

  // ── Auto-tracking ─────────────────────────────────────────────────────────

  describe('Auto-tracking', () => {

    it('ngOnInit llama a getFciFunds tres veces (mercadoDinero, rentaFija, rentaVariable)', () => {
      // buildSpies ya configuró getFciFunds para retornar MOCK_FCI_FUNDS
      // fixture.detectChanges() en beforeEach ya llamó ngOnInit
      expect(investSpy.getFciFunds).toHaveBeenCalledWith('mercadoDinero');
      expect(investSpy.getFciFunds).toHaveBeenCalledWith('rentaFija');
      expect(investSpy.getFciFunds).toHaveBeenCalledWith('rentaVariable');
    });

    it('ngOnInit llama a getInstruments tres veces (LETRA, BONO, ON)', () => {
      expect(investSpy.getInstruments).toHaveBeenCalledWith('LETRA');
      expect(investSpy.getInstruments).toHaveBeenCalledWith('BONO');
      expect(investSpy.getInstruments).toHaveBeenCalledWith('ON');
    });

    it('ngOnInit popula fciFunds con la concatenación de los cuatro catálogos', () => {
      // getFciFunds devuelve MOCK_FCI_FUNDS (2 items) para cada categoría (mm/rf/rv/rmix) → 8 total
      expect(component.fciFunds().length).toBe(8);
    });

    it('ngOnInit popula instruments con la concatenación de los tres catálogos', () => {
      // getInstruments devuelve MOCK_INSTRUMENTS (2 items) para cada llamada → 6 total
      expect(component.instruments().length).toBe(6);
    });

    it('setTrackingMode("manual") limpia externalId del fciCPForm', () => {
      component.fciCPForm.controls.externalId.setValue('FCI Galileo Growth');
      component.setTrackingMode('manual');
      expect(component.fciCPForm.controls.externalId.value).toBeNull();
    });

    it('setTrackingMode("manual") limpia externalId del letraForm', () => {
      component.letraForm.controls.externalId.setValue('S31G5');
      component.setTrackingMode('manual');
      expect(component.letraForm.controls.externalId.value).toBeNull();
    });

    it('setTrackingMode("manual") resetea queries y oculta dropdowns', () => {
      component.fundSearchQuery.set('galileo');
      component.instrSearchQuery.set('S31G5');
      component.showFundDropdown.set(true);
      component.showInstrDropdown.set(true);
      component.setTrackingMode('manual');
      expect(component.fundSearchQuery()).toBe('');
      expect(component.instrSearchQuery()).toBe('');
      expect(component.showFundDropdown()).toBeFalse();
      expect(component.showInstrDropdown()).toBeFalse();
    });

    it('setTrackingMode("auto") NO limpia los campos (solo manual hace limpieza)', () => {
      component.fciCPForm.controls.externalId.setValue('FCI X');
      component.setTrackingMode('auto');
      expect(component.fciCPForm.controls.externalId.value).toBe('FCI X');
    });

    it('selectFund() setea name, externalId del fciCPForm, oculta el dropdown y pre-llena pricePerUnit sin API call', () => {
      const fund: FciFundOption = { fondo: 'FCI Galileo Growth', categoria: 'mercadoDinero', vcp: 1523.45, fecha: '2026-06-24' };
      investSpy.getFciVcp.calls.reset();
      component.selectFund(fund);
      expect(component.fciCPForm.controls.name.value).toBe('FCI Galileo Growth');
      expect(component.fciCPForm.controls.externalId.value).toBe('FCI Galileo Growth');
      expect(component.fundSearchQuery()).toBe('FCI Galileo Growth');
      expect(component.showFundDropdown()).toBeFalse();
      expect(component.addMovementCPForm.controls.pricePerUnit.value).toBe(fund.vcp);
      expect(investSpy.getFciVcp).not.toHaveBeenCalled();
    });

    it('selectType(FCI_CUOTAPARTES) deja la fecha de compra en hoy por defecto', () => {
      component.openCreate();
      component.selectType('FCI_CUOTAPARTES');
      expect(component.fciCPForm.controls.purchaseDate.value).toBe(component['todayIso']());
    });

    it('cambiar la fecha de compra a una fecha pasada recarga el VCP histórico (auto)', () => {
      component.openCreate();
      component.selectType('FCI_CUOTAPARTES');           // auto + purchaseDate hoy
      component.fciCPForm.controls.externalId.setValue('FCI Galileo Growth');
      investSpy.getFciVcp.calls.reset();
      investSpy.getFciVcp.and.returnValue(of({ fondo: 'FCI Galileo Growth', vcp: 1200, fecha: '2026-03-01' }));

      component.fciCPForm.controls.purchaseDate.setValue('2026-03-01');

      expect(investSpy.getFciVcp).toHaveBeenCalledWith('FCI Galileo Growth', '2026-03-01');
      expect(component.priceAtCreation()).toBe(1200);
      expect(component.addMovementCPForm.controls.pricePerUnit.value).toBe(1200);
    });

    it('submit de creación usa la fecha de compra para el activo y el movimiento inicial', () => {
      component.openCreate();
      component.selectType('FCI_CUOTAPARTES');
      component.setTrackingMode('manual');
      component.fciCPForm.patchValue({ name: 'FCI Backdated', currency: 'ARS', purchaseDate: '2026-02-15' });
      component.addMovementCPForm.patchValue({ amount: 100000, units: 100, pricePerUnit: 1000 });

      component.submit();

      const req = investSpy.createInvestment.calls.mostRecent().args[0];
      expect(req.purchaseDate).toBe('2026-02-15');
      const movArgs = investSpy.addMovement.calls.mostRecent().args;
      expect(movArgs[1].movementDate).toBe('2026-02-15');
    });

    it('selectInstrument() setea name y externalId del letraForm y oculta el dropdown', () => {
      const instr: InstrumentOption = { ticker: 'S31G5', nombre: 'LECAP 31/08/2025', tipo: 'LETRA', lastPrice: 1020.5, priceDate: '2026-06-24', maturityDate: '2025-08-31' };
      component.selectInstrument(instr);
      expect(component.letraForm.controls.name.value).toBe('S31G5 — LECAP 31/08/2025');
      expect(component.letraForm.controls.externalId.value).toBe('S31G5');
      expect(component.instrSearchQuery()).toBe('S31G5 — LECAP 31/08/2025');
      expect(component.showInstrDropdown()).toBeFalse();
    });

    it('filteredFunds() con query < 2 chars devuelve los fondos de la categoría activa (default Money Market)', () => {
      // ngOnInit cargó 8 fondos (2 × 4 categorías): 4 mercadoDinero + 4 rentaFija.
      // La categoría por defecto es mercadoDinero → 4 resultados.
      const result = component.filteredFunds();
      expect(result.length).toBe(4);
      expect(result.every(f => f.categoria === 'mercadoDinero')).toBeTrue();
    });

    it('filteredFunds() con query >= 2 chars filtra por nombre (case-insensitive)', () => {
      component.fundSearchQuery.set('galileo');
      const result = component.filteredFunds();
      expect(result.every(f => f.fondo.toLowerCase().includes('galileo'))).toBeTrue();
    });

    it('filteredFunds() no retorna más de 50 resultados', () => {
      // Llenar con 60 fondos
      const manyFunds: FciFundOption[] = Array.from({ length: 60 }, (_, i) => ({
        fondo: `Fondo ${i}`, categoria: 'mercadoDinero', vcp: 1000 + i, fecha: '2026-06-24',
      }));
      component.fciFunds.set(manyFunds);
      component.fundSearchQuery.set('');
      const result = component.filteredFunds();
      expect(result.length).toBeLessThanOrEqual(50);
    });

    it('selectType("FCI_CUOTAPARTES") setea trackingMode a "auto" (Bug #1)', () => {
      component.trackingMode.set('manual');
      component.openCreate();
      component.selectType('FCI_CUOTAPARTES');
      expect(component.trackingMode()).toBe('auto');
    });

    it('selectType("LETRA") setea trackingMode a "auto" (Bug #1)', () => {
      component.trackingMode.set('manual');
      component.openCreate();
      component.selectType('LETRA');
      expect(component.trackingMode()).toBe('auto');
    });

    it('selectType("BONO") setea trackingMode a "auto" (Bug #1)', () => {
      component.trackingMode.set('manual');
      component.openCreate();
      component.selectType('BONO');
      expect(component.trackingMode()).toBe('auto');
    });

    it('selectType("FCI") setea trackingMode a "manual"', () => {
      component.trackingMode.set('auto');
      component.openCreate();
      component.selectType('FCI');
      expect(component.trackingMode()).toBe('manual');
    });

    it('selectType("PLAZO_FIJO") setea trackingMode a "manual"', () => {
      component.trackingMode.set('auto');
      component.openCreate();
      component.selectType('PLAZO_FIJO');
      expect(component.trackingMode()).toBe('manual');
    });

    it('selectType() resetea priceAtCreation a null (Bug minor: etiqueta VCP no aparece al reabrir)', () => {
      component.priceAtCreation.set(1523.45);
      component.priceSource.set('fci');
      component.selectType('FCI_CUOTAPARTES');
      expect(component.priceAtCreation()).toBeNull();
      expect(component.priceSource()).toBeNull();
    });

  });

  // ── Triángulo monto/unidades/precio ───────────────────────────────────────

  describe('Triángulo monto/unidades/precio', () => {

    it('onAmountInput: con pricePerUnit=500 y amount=1000 → units=2', () => {
      component.addMovementCPForm.controls.pricePerUnit.setValue(500, { emitEvent: false });
      component.addMovementCPForm.controls.amount.setValue(1000, { emitEvent: false });
      component.onAmountInput(component.addMovementCPForm);
      expect(component.addMovementCPForm.controls.units.value).toBeCloseTo(2, 6);
    });

    it('onUnitsInput: con pricePerUnit=500 y units=3 → amount=1500', () => {
      component.addMovementCPForm.controls.pricePerUnit.setValue(500, { emitEvent: false });
      component.addMovementCPForm.controls.units.setValue(3, { emitEvent: false });
      component.onUnitsInput(component.addMovementCPForm);
      expect(component.addMovementCPForm.controls.amount.value).toBeCloseTo(1500, 2);
    });

    it('onPricePerUnitInput: con amount=1000 y price=250 → units=4', () => {
      component.addMovementCPForm.controls.amount.setValue(1000, { emitEvent: false });
      component.addMovementCPForm.controls.pricePerUnit.setValue(250, { emitEvent: false });
      component.onPricePerUnitInput(component.addMovementCPForm);
      expect(component.addMovementCPForm.controls.units.value).toBeCloseTo(4, 6);
    });

    it('onAmountInput: con price=0 y units no nulo calcula price desde amount/units', () => {
      component.addMovementCPForm.controls.pricePerUnit.setValue(0, { emitEvent: false });
      component.addMovementCPForm.controls.units.setValue(5, { emitEvent: false });
      component.addMovementCPForm.controls.amount.setValue(2500, { emitEvent: false });
      component.onAmountInput(component.addMovementCPForm);
      // price es 0, no puede calcular units. Calcula price = amount/units = 500
      expect(component.addMovementCPForm.controls.pricePerUnit.value).toBeCloseTo(500, 2);
    });

  });

  // ── autoTrack — modal de movimientos ─────────────────────────────────────

  describe('autoTrack — apertura del modal de movimientos', () => {

    it('openAddMovementModal llama getFciVcp para activo FCI_CUOTAPARTES con autoTrack', () => {
      const autoAsset: typeof MOCK_ASSETS[0] = {
        ...MOCK_ASSETS[0],
        type:       'FCI_CUOTAPARTES',
        autoTrack:  true,
        externalId: 'FCI Galileo Growth',
        movements:  [],
        valuations: [],
      };
      investSpy.getFciVcp.and.returnValue(of({ fondo: 'FCI Galileo Growth', vcp: 1500, fecha: '2026-06-26' }));

      component.openAddMovementModal(autoAsset);

      expect(investSpy.getFciVcp).toHaveBeenCalledWith(
        'FCI Galileo Growth',
        jasmine.any(String),
      );
    });

    it('openAddMovementModal NO llama getFciVcp para activo sin autoTrack', () => {
      investSpy.getFciVcp.calls.reset();
      component.openAddMovementModal(MOCK_ASSETS[0]); // autoTrack: false
      expect(investSpy.getFciVcp).not.toHaveBeenCalled();
    });

  });

  // ── letraForm — validación cross-field (Bug #3) ───────────────────────────

  describe('letraForm — validación cross-field maturityDate > purchaseDate', () => {

    it('letraForm es inválido cuando maturityDate = purchaseDate', () => {
      component.openCreate();
      component.selectType('LETRA');
      component.setTrackingMode('manual');
      component.letraForm.patchValue({ name: 'LECAP Test', purchaseDate: '2026-06-01', maturityDate: '2026-06-01' });
      expect(component.letraForm.errors?.['maturityBeforePurchase']).toBeTrue();
      expect(component.letraForm.invalid).toBeTrue();
    });

    it('letraForm es inválido cuando maturityDate < purchaseDate', () => {
      component.openCreate();
      component.selectType('LETRA');
      component.setTrackingMode('manual');
      component.letraForm.patchValue({ name: 'LECAP Test', purchaseDate: '2026-06-01', maturityDate: '2026-05-01' });
      expect(component.letraForm.errors?.['maturityBeforePurchase']).toBeTrue();
    });

    it('letraForm es válido cuando maturityDate > purchaseDate', () => {
      component.openCreate();
      component.selectType('LETRA');
      component.setTrackingMode('manual');
      component.letraForm.patchValue({ name: 'LECAP Test', purchaseDate: '2026-06-01', maturityDate: '2026-12-31' });
      expect(component.letraForm.errors?.['maturityBeforePurchase']).toBeFalsy();
    });

  });

  // ── submitLetraBO con compra inicial (Bug #4) ──────────────────────────────

  describe('submitLetraBO — crea movimiento inicial al crear', () => {

    it('al crear LETRA con amount y units llama addMovement con SUSCRIPCION', () => {
      const savedAsset: InvestmentResponse = {
        ...MOCK_ASSETS[1], id: 'nueva-letra', type: 'LETRA',
        purchaseDate: '2026-06-01', maturityDate: '2026-12-31',
      };
      investSpy.createInvestment.and.returnValue(of(savedAsset));
      investSpy.addMovement.and.returnValue(of({ ...savedAsset, principal: 9500 }));

      component.openCreate();
      component.selectType('LETRA');
      component.setTrackingMode('manual');
      component.letraForm.patchValue({ name: 'LECAP Test', purchaseDate: '2026-06-01', maturityDate: '2026-12-31' });
      component.addMovementCPForm.patchValue({ amount: 9500, units: 10000, pricePerUnit: 0.95 });

      component.submit();

      expect(investSpy.createInvestment).toHaveBeenCalled();
      expect(investSpy.addMovement).toHaveBeenCalledWith(
        savedAsset.id,
        jasmine.objectContaining({ type: 'SUSCRIPCION', amount: 9500, units: 10000 }),
      );
      expect(component.modal()).toBeNull();
    });

    it('al crear LETRA MANUAL sin amount no llama addMovement (compra inicial opcional en manual)', () => {
      const savedAsset: InvestmentResponse = {
        ...MOCK_ASSETS[1], id: 'nueva-letra-sin-mov', type: 'LETRA',
        purchaseDate: '2026-06-01', maturityDate: '2026-12-31',
      };
      investSpy.createInvestment.and.returnValue(of(savedAsset));
      investSpy.addMovement.calls.reset();

      component.openCreate();
      component.selectType('LETRA');
      component.setTrackingMode('manual');
      component.letraForm.patchValue({ name: 'LECAP Test', purchaseDate: '2026-06-01', maturityDate: '2026-12-31' });
      // addMovementCPForm queda vacío (sin amount ni units)

      component.submit();

      expect(investSpy.createInvestment).toHaveBeenCalled();
      expect(investSpy.addMovement).not.toHaveBeenCalled();
      expect(component.modal()).toBeNull();
    });

    it('al crear LETRA con seguimiento AUTO sin compra inicial, bloquea el submit y muestra error', () => {
      investSpy.createInvestment.calls.reset();
      investSpy.addMovement.calls.reset();

      component.openCreate();
      component.selectType('LETRA'); // por defecto trackingMode = 'auto'
      component.letraForm.patchValue({ name: 'LECAP Test', purchaseDate: '2026-06-01', maturityDate: '2026-12-31' });
      component.letraForm.controls.externalId.setValue('S31L6'); // simula selección de instrumento
      // addMovementCPForm vacío → sin compra inicial

      component.submit();

      // No se crea nada; se informa el error y el modal sigue abierto.
      expect(investSpy.createInvestment).not.toHaveBeenCalled();
      expect(investSpy.addMovement).not.toHaveBeenCalled();
      expect(component.formError()).toContain('compra inicial');
      expect(component.modal()).not.toBeNull();
    });

  });

  // ── calcValorActualCP para LETRA sin valuaciones (Bug #6) ─────────────────

  describe('calcValorActualCP — LETRA sin valuaciones usa VN=1', () => {

    it('LETRA sin valuaciones devuelve nominales × 1 (no el principal)', () => {
      const letraSinValuaciones: InvestmentResponse = {
        id: 'l-1', name: 'LECAP Test', type: 'LETRA', currency: 'ARS',
        principal: 9500,
        purchaseDate: '2026-01-01', maturityDate: '2026-12-31',
        tna: 0, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 9500, units: 10000, createdAt: '' },
        ],
        valuations: [],
      };
      // 10000 nominales × VN=1 = 10000
      expect(component.calcValorActualCP(letraSinValuaciones)).toBeCloseTo(10000, 0);
    });

    it('LETRA sin valuaciones: el tramo en curso usa endPrice = 1 (fallback zero-coupon)', () => {
      const letraSinValuaciones: InvestmentResponse = {
        id: 'l-3', name: 'LECAP Test', type: 'LETRA', currency: 'ARS',
        principal: 9500,
        purchaseDate: '2026-01-01', maturityDate: '2026-12-31',
        tna: 0, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 9500, units: 10000, createdAt: '' },
        ],
        valuations: [],
      };
      const tramos = component.calcTramosCP(letraSinValuaciones);
      const enCurso = tramos.find(t => t.enCurso);
      expect(enCurso).toBeDefined();
      expect(enCurso!.endPrice).toBe(1);
    });

    it('LETRA sin valuaciones: calcGananciaCP = 10000 − 9500 = 500', () => {
      const letraSinValuaciones: InvestmentResponse = {
        id: 'l-2', name: 'LECAP Test', type: 'LETRA', currency: 'ARS',
        principal: 9500,
        purchaseDate: '2026-01-01', maturityDate: '2026-12-31',
        tna: 0, accountId: null, accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
        createdAt: '', updatedAt: '',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 9500, units: 10000, createdAt: '' },
        ],
        valuations: [],
      };
      expect(component.calcGananciaCP(letraSinValuaciones)).toBeCloseTo(500, 0);
    });

    it('FCI_CUOTAPARTES sin valuaciones sigue devolviendo principal (comportamiento previo)', () => {
      const fciSinValuaciones: InvestmentResponse = {
        ...MOCK_ASSETS[0],
        type: 'FCI_CUOTAPARTES',
        principal: 700000,
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 700000, units: 500, createdAt: '' },
        ],
        valuations: [],
      };
      expect(component.calcValorActualCP(fciSinValuaciones)).toBe(700000);
    });

  });

  // ── movementTypesFor ──────────────────────────────────────────────────────

  describe('movementTypesFor', () => {

    it('retorna REVALUO para activos manuales', () => {
      const manualAsset = { ...MOCK_ASSETS[0], type: 'FCI_CUOTAPARTES' as const, autoTrack: false };
      const types = component.movementTypesFor(manualAsset);
      expect(types.some(t => t.value === 'REVALUO')).toBeTrue();
    });

    it('incluye REVALUO también cuando el activo ES autoTrack', () => {
      const autoAsset = {
        ...MOCK_ASSETS[0],
        type:       'FCI_CUOTAPARTES' as const,
        autoTrack:  true,
        externalId: 'FCI X',
      };
      const types = component.movementTypesFor(autoAsset);
      expect(types.some(t => t.value === 'REVALUO')).toBeTrue();
    });

  });

});
