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
    autoTrack: false, externalId: null,
    createdAt: '2026-06-01T00:00:00Z', updatedAt: '2026-06-01T00:00:00Z',
    movements: MOCK_FCI_MOVEMENTS,
    valuations: [],
  },
  {
    id: 'inv-2', name: 'LECAP S31G5', type: 'LETRA', currency: 'ARS',
    principal: 850000, purchaseDate: '2026-02-01', maturityDate: '2026-08-31',
    tna: 72.0, accountId: null, accountName: null,
    autoTrack: false, externalId: null,
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
    'getInvestments', 'createInvestment', 'updateInvestment', 'deleteInvestment',
    'addMovement', 'deleteMovement', 'addValuation', 'updateValuation', 'deleteValuation',
    'getFciFunds', 'getInstruments', 'getFciVcp', 'getInstrumentPrice',
  ]);
  investSpy.getInvestments.and.returnValue(of([...MOCK_ASSETS]));
  investSpy.createInvestment.and.returnValue(of({ ...MOCK_ASSETS[0] }));
  investSpy.updateInvestment.and.returnValue(of({ ...MOCK_ASSETS[1], name: 'LECAP Actualizada' }));
  investSpy.deleteInvestment.and.returnValue(of(undefined));
  investSpy.addMovement.and.returnValue(of({ ...MOCK_ASSETS[0] }));
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

  const currencySpy = jasmine.createSpyObj<CurrencyService>('CurrencyService', [], {
    currentRateARS: signal<string | null>('1062.50'),
    selected:       signal<'ARS' | 'USD'>('ARS'),
  });

  return { investSpy, accountSpy, macroSpy, currencySpy };
}

// ── Suite ─────────────────────────────────────────────────────────────────────

describe('InversionesComponent', () => {
  let fixture:   ComponentFixture<InversionesComponent>;
  let component: InversionesComponent;
  let investSpy: jasmine.SpyObj<InvestmentService>;
  let macroSpy:  jasmine.SpyObj<MacroService>;

  beforeEach(async () => {
    const spies = buildSpies();
    investSpy = spies.investSpy;
    macroSpy  = spies.macroSpy;

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

  // ── 3. openCreate ──────────────────────────────────────────────────────────

  it('openCreate abre el selector de tipo y resetea el formulario', () => {
    component.openCreate();
    expect(component.modal()?.kind).toBe('type-select');
    expect(component.plazoFijoForm.controls.name.value).toBe('');
    expect(component.plazoFijoForm.controls.currency.value).toBe('ARS');
    expect(component.plazoFijoForm.controls.principal.value).toBeNull();
    expect(component.formError()).toBeNull();
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
      dias: 30, tna: 60.0, accountId: null,
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

  it('calcTramosFCI con 2 movimientos produce 3 tramos: inicial vacío, mov1, mov2 y en-curso', () => {
    const asset = MOCK_ASSETS[0]; // FCI con 2 movimientos
    const tramos = component.calcTramosFCI(asset);
    // El primer tramo tiene el movimiento mov-1 (con saldo 0 antes de él)
    // El segundo tramo tiene movimiento mov-2 (con saldo 500k)
    // El tercer tramo es "en curso" (sin movimiento)
    expect(tramos.length).toBe(3);
    // Primer tramo: saldo previo al primer mov = 0
    expect(tramos[0].mov?.id).toBe('mov-1');
    expect(tramos[0].saldo).toBe(0);
    expect(tramos[0].enCurso).toBeFalse();
    // Segundo tramo: saldo después de primera suscripción = 500k
    expect(tramos[1].mov?.id).toBe('mov-2');
    expect(tramos[1].saldo).toBe(500000);
    expect(tramos[1].enCurso).toBeFalse();
    // Tercer tramo: en curso, saldo = 500k + 100k = 600k
    expect(tramos[2].mov).toBeNull();
    expect(tramos[2].saldo).toBe(600000);
    expect(tramos[2].enCurso).toBeTrue();
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
        accountId: null, accountName: null, autoTrack: false, externalId: null,
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

    it('calcTramosCP con valuación de hoy no genera tramo enCurso con 0 días', () => {
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
          { id: 'v1', valuationDate: todayStr, pricePerUnit: 600, source: 'MANUAL' as const, createdAt: '' },
        ],
      };
      const tramos = component.calcTramosCP(assetHoy);
      expect(tramos.some(t => t.enCurso)).toBeFalse();
      expect(tramos.every(t => t.dias > 0)).toBeTrue();
    });

    it('calcTramosCP con valuación futura no genera tramo enCurso con días negativos', () => {
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
      // Debe haber 1 tramo cerrado (hasta 2099-01-01) pero NO el tramo en curso con días negativos
      expect(tramos.some(t => t.enCurso)).toBeFalse();
      expect(tramos.every(t => t.dias >= 0)).toBeTrue();
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

    it('calcTramosFCI usa el amount real del REVALUO como intereses del tramo', () => {
      const tramos = component.calcTramosFCI(assetConRevaluo);
      const tramoRevaluo = tramos.find(t => t.mov?.type === 'REVALUO');
      expect(tramoRevaluo).toBeDefined();
      expect(tramoRevaluo!.intereses).toBeCloseTo(15000, 0);
    });

    it('calcTramosFCI excluye REVALUO del cálculo estimado del tramo en curso', () => {
      const tramos = component.calcTramosFCI(assetConRevaluo);
      // El tramo en curso no debe ser REVALUO
      const encurso = tramos.find(t => t.enCurso);
      expect(encurso).toBeDefined();
      expect(encurso!.mov).toBeNull();
      // Solo los tramos con mov no-REVALUO aportan estimado
      const tramosNoRevaluo = tramos.filter(t => t.mov?.type !== 'REVALUO');
      const interesesEstimados = tramosNoRevaluo.reduce((acc, t) => acc + t.intereses, 0);
      // Con saldo 515k y TNA 65.5%, los intereses estimados en curso deben ser > 0
      expect(interesesEstimados).toBeGreaterThanOrEqual(0);
    });
  });

  describe('LETRA/BONO/ON — comportamiento tipo cuotapartes', () => {
    function buildLetraAsset(): InvestmentResponse {
      return {
        id: 'letra-1', name: 'LECAP S31G5', type: 'LETRA', currency: 'ARS',
        principal: 97000,       // 1000 nominales a $97
        purchaseDate: '2026-01-01', maturityDate: '2026-12-31',
        tna: 0, accountId: null, accountName: null,
        autoTrack: false, externalId: null,
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
      component.fciForm.setValue({ name: 'FCI Test', currency: 'ARS', tna: 65.0, accountId: null });
      component.submit();
      expect(investSpy.createInvestment).toHaveBeenCalled();
      expect(component.modal()).toBeNull();
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

    it('ngOnInit popula fciFunds con la concatenación de los tres catálogos', () => {
      // getFciFunds devuelve MOCK_FCI_FUNDS (2 items) para cada llamada → 6 total
      expect(component.fciFunds().length).toBe(6);
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

    it('selectInstrument() setea name y externalId del letraForm y oculta el dropdown', () => {
      const instr: InstrumentOption = { ticker: 'S31G5', nombre: 'LECAP 31/08/2025', tipo: 'LETRA', lastPrice: 1020.5, priceDate: '2026-06-24', maturityDate: '2025-08-31' };
      component.selectInstrument(instr);
      expect(component.letraForm.controls.name.value).toBe('S31G5 — LECAP 31/08/2025');
      expect(component.letraForm.controls.externalId.value).toBe('S31G5');
      expect(component.instrSearchQuery()).toBe('S31G5 — LECAP 31/08/2025');
      expect(component.showInstrDropdown()).toBeFalse();
    });

    it('filteredFunds() con query < 2 chars devuelve hasta 50 resultados sin filtrar', () => {
      // ngOnInit cargó 6 fondos (2 × 3 calls)
      const result = component.filteredFunds();
      expect(result.length).toBe(6);
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

    it('al crear LETRA sin amount no llama addMovement', () => {
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

  });

  // ── calcValorActualCP para LETRA sin valuaciones (Bug #6) ─────────────────

  describe('calcValorActualCP — LETRA sin valuaciones usa VN=1', () => {

    it('LETRA sin valuaciones devuelve nominales × 1 (no el principal)', () => {
      const letraSinValuaciones: InvestmentResponse = {
        id: 'l-1', name: 'LECAP Test', type: 'LETRA', currency: 'ARS',
        principal: 9500,
        purchaseDate: '2026-01-01', maturityDate: '2026-12-31',
        tna: 0, accountId: null, accountName: null, autoTrack: false, externalId: null,
        createdAt: '', updatedAt: '',
        movements: [
          { id: 'm1', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 9500, units: 10000, createdAt: '' },
        ],
        valuations: [],
      };
      // 10000 nominales × VN=1 = 10000
      expect(component.calcValorActualCP(letraSinValuaciones)).toBeCloseTo(10000, 0);
    });

    it('LETRA sin valuaciones: calcGananciaCP = 10000 − 9500 = 500', () => {
      const letraSinValuaciones: InvestmentResponse = {
        id: 'l-2', name: 'LECAP Test', type: 'LETRA', currency: 'ARS',
        principal: 9500,
        purchaseDate: '2026-01-01', maturityDate: '2026-12-31',
        tna: 0, accountId: null, accountName: null, autoTrack: false, externalId: null,
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
