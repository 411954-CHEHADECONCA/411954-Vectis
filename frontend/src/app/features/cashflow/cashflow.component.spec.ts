import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { CashflowComponent } from './cashflow.component';
import { CashflowResponse } from '../../core/models/cashflow.models';
import { CurrencyService } from '../../core/services/currency.service';
import { environment } from '../../../environments/environment';

const MOCK_DATA: CashflowResponse = {
  year:                  2026,
  month:                 6,
  periodLabel:           'Junio 2026',
  monthShort:            'jun',
  status:                'curso',
  isProjection:          false,
  recurringMaterialized: false,
  needsConfirmation:     false,
  hasLiquidityDeficit:   false,
  liquidityDeficit:      '0.0000',
  openingBalance: { total: { ars: 8900000, usd: 0 }, accounts: [
    { accountId: 'a1', name: 'Galicia', ccy: 'ARS', balance: '8900000.0000' },
  ]},
  income:  { total: { ars: 1831420, usd: 0 }, totalBudgeted: { ars: 0, usd: 0 }, byCategory: [
    { categoryId: 'c1', name: 'Sueldo', icon: 'briefcase', color: '#52eacd', amount: { ars: 1831420, usd: 0 }, pctOfTotal: '100.00', budgeted: null, pctOfBudget: null },
  ]},
  expenses: { total: { ars: 1677400, usd: 0 }, totalBudgeted: { ars: 460000, usd: 0 }, byCategory: [
    { categoryId: 'c2', name: 'Vivienda', icon: 'home', color: '#ffb4ab', amount: { ars: 480000, usd: 0 }, pctOfTotal: '28.62', budgeted: { ars: 460000, usd: 0 }, pctOfBudget: '104.35' },
  ]},
  preInvestmentBalance: { balance: { ars: 9054020, usd: 0 }, operativeResult: { ars: 153620, usd: 0 }, savingRatePct: '8.39' },
  investments: { total: { ars: 800000, usd: 0 }, pctOfPreBalance: '8.84', instruments: [
    { name: 'Inversiones', icon: 'trending-up', color: '#8b5cf6', amount: { ars: 800000, usd: 0 }, teaPct: null },
  ]},
  closingBalance: { total: { ars: 8254020, usd: 0 }, accounts: [
    { accountId: 'a1', name: 'Galicia', ccy: 'ARS', balance: '8254020.0000' },
  ]},
  oficialRateAtPeriod: '1062.5000',
  earliestNavigableYear:  2026,
  earliestNavigableMonth: 6,
};

describe('CashflowComponent', () => {
  let component: CashflowComponent;
  let fixture: ComponentFixture<CashflowComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CashflowComponent, HttpClientTestingModule],
    }).compileComponents();

    fixture   = TestBed.createComponent(CashflowComponent);
    component = fixture.componentInstance;
    httpMock  = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** Flush the exchange-rate call and both cashflow GET requests fired by forkJoin. */
  function flushCashflow(): void {
    httpMock.match(r => r.url.includes('exchange-rates/oficial/latest'))
            .forEach(r => r.flush({ rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000', rateDate: '2026-06-22', source: 'test' }));
    const reqs = httpMock.match(r => r.url === '/api/cashflow');
    reqs.forEach(r => r.flush(MOCK_DATA));
    fixture.detectChanges();
  }

  it('should create', () => {
    fixture.detectChanges();
    flushCashflow();
    expect(component).toBeTruthy();
  });

  it('loading signal is true while request is in flight', () => {
    fixture.detectChanges();
    expect(component.loading()).toBeTrue();
    flushCashflow();
    expect(component.loading()).toBeFalse();
  });

  it('data signal is populated after successful load', () => {
    fixture.detectChanges();
    flushCashflow();
    expect(component.data()).toEqual(MOCK_DATA);
  });

  it('error signal is set on HTTP error', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.match(r => r.url.includes('exchange-rates/oficial/latest'))
            .forEach(r => r.flush({ rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000', rateDate: '2026-06-22', source: 'test' }));
    // forkJoin fires current + prev month; failing one cancels the other (forkJoin behaviour)
    const reqs = httpMock.match(r => r.url === '/api/cashflow');
    // Fail the current-month request; forkJoin will cancel the prev-month request automatically
    reqs[0].flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    // The second request is already cancelled after forkJoin fails — do not try to flush it
    tick();
    fixture.detectChanges();
    expect(component.error()).not.toBeNull();
    expect(component.loading()).toBeFalse();
  }));

  describe('prevMonth / nextMonth', () => {
    beforeEach(() => {
      fixture.detectChanges();
      flushCashflow();
    });

    it('prevMonth decrements month and calls loadData', () => {
      const initialMonth = component.month();
      component.prevMonth();
      const reqs = httpMock.match(r => r.url === '/api/cashflow');
      reqs.forEach(r => r.flush(MOCK_DATA));
      fixture.detectChanges();

      const expectedMonth = initialMonth === 1 ? 12 : initialMonth - 1;
      expect(component.month()).toBe(expectedMonth);
    });

    it('nextMonth increments month and calls loadData', () => {
      const initialMonth = component.month();
      component.nextMonth();
      const reqs = httpMock.match(r => r.url === '/api/cashflow');
      reqs.forEach(r => r.flush(MOCK_DATA));
      fixture.detectChanges();

      const expectedMonth = initialMonth === 12 ? 1 : initialMonth + 1;
      expect(component.month()).toBe(expectedMonth);
    });

    it('canGoForward returns false when already 3 months ahead', () => {
      const now = new Date();
      let y = now.getFullYear();
      let m = now.getMonth() + 1 + 3;
      if (m > 12) { m -= 12; y++; }
      component.year.set(y);
      component.month.set(m);
      expect(component.canGoForward()).toBeFalse();
    });

    it('canGoBack is true while the viewed month is after the navigable floor', () => {
      // Piso = jul 2025; mes visto = ago 2025 → todavía se puede retroceder
      component.earliestNavigable.set({ year: 2025, month: 7 });
      component.year.set(2025);
      component.month.set(8);
      expect(component.canGoBack()).toBeTrue();
    });

    it('canGoBack is false exactly at the navigable floor (mes más antiguo con movimientos)', () => {
      component.earliestNavigable.set({ year: 2025, month: 7 });
      component.year.set(2025);
      component.month.set(7);
      expect(component.canGoBack()).toBeFalse();
    });

    it('canGoBack is false when the floor is not yet known (sin cargar)', () => {
      component.earliestNavigable.set(null);
      expect(component.canGoBack()).toBeFalse();
    });

    it('prevMonth does nothing once the viewed month reaches the floor', () => {
      component.earliestNavigable.set({ year: 2025, month: 7 });
      component.year.set(2025);
      component.month.set(7);
      const yearBefore  = component.year();
      const monthBefore = component.month();
      component.prevMonth();
      expect(component.year()).toBe(yearBefore);
      expect(component.month()).toBe(monthBefore);
    });

    it('nextMonth does nothing when canGoForward is false', () => {
      const now = new Date();
      let y = now.getFullYear();
      let m = now.getMonth() + 1 + 3;
      if (m > 12) { m -= 12; y++; }
      component.year.set(y);
      component.month.set(m);
      const yearBefore  = component.year();
      const monthBefore = component.month();
      component.nextMonth();
      expect(component.year()).toBe(yearBefore);
      expect(component.month()).toBe(monthBefore);
    });
  });

  it('isProjection returns true when data has isProjection=true', () => {
    fixture.detectChanges();
    httpMock.match(r => r.url.includes('exchange-rates/oficial/latest'))
            .forEach(r => r.flush({ rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000', rateDate: '2026-06-22', source: 'test' }));
    const reqs = httpMock.match(r => r.url === '/api/cashflow');
    reqs[0].flush({ ...MOCK_DATA, isProjection: true, status: 'proyectado' });
    if (reqs[1]) reqs[1].flush(MOCK_DATA);
    fixture.detectChanges();
    expect(component.isProjection()).toBeTrue();
  });

  it('should render error state when error signal is set', fakeAsync(() => {
    fixture.detectChanges();
    httpMock.match(r => r.url.includes('exchange-rates/oficial/latest'))
            .forEach(r => r.flush({ rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000', rateDate: '2026-06-22', source: 'test' }));
    const reqs = httpMock.match(r => r.url === '/api/cashflow');
    // Fail the current-month request; forkJoin cancels the second automatically
    reqs[0].flush('Error', { status: 503, statusText: 'Service Unavailable' });
    tick();
    fixture.detectChanges();
    const errorEl = fixture.debugElement.query(By.css('.error-state'));
    expect(errorEl).not.toBeNull();
  }));

  // ── leadText ───────────────────────────────────────────────────────────────

  describe('leadText', () => {
    beforeEach(() => {
      fixture.detectChanges();
      flushCashflow();
    });

    it('leadText retorna texto correcto para status abierto', () => {
      component.data.set({ ...MOCK_DATA, status: 'abierto' });
      expect(component.leadText()).toContain('Mes abierto');
    });

    it('leadText retorna texto correcto para status cerrado', () => {
      component.data.set({ ...MOCK_DATA, status: 'cerrado' });
      expect(component.leadText()).toContain('Mes cerrado');
    });

    it('leadText retorna texto correcto para status proyectado', () => {
      component.data.set({ ...MOCK_DATA, status: 'proyectado' });
      expect(component.leadText()).toContain('Proyección estimada');
    });
  });

  // ── status badge abierto ───────────────────────────────────────────────────

  describe('badge status--abierto', () => {
    it('se renderiza cuando status es abierto', () => {
      fixture.detectChanges();
      flushCashflow();
      component.data.set({ ...MOCK_DATA, status: 'abierto' });
      fixture.detectChanges();
      const badge = fixture.debugElement.query(By.css('.status-badge--abierto'));
      expect(badge).not.toBeNull();
    });
  });

  // ── closePeriod ────────────────────────────────────────────────────────────

  describe('closePeriod', () => {
    beforeEach(() => {
      fixture.detectChanges();
      flushCashflow();
    });

    it('confirma y llama al servicio con año/mes correctos', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.data.set({ ...MOCK_DATA, status: 'curso' });

      component.closePeriod();

      const req = httpMock.expectOne(r =>
        r.url === `${environment.apiUrl}/cashflow/${MOCK_DATA.year}/${MOCK_DATA.month}/close`
        && r.method === 'POST'
      );
      expect(req).toBeTruthy();
      req.flush({ ...MOCK_DATA, status: 'cerrado' });
      fixture.detectChanges();
    });

    it('cancela si el usuario rechaza confirm', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      component.data.set({ ...MOCK_DATA, status: 'curso' });

      component.closePeriod();

      httpMock.expectNone(r =>
        r.url.includes('/close')
      );
    });

    it('limpia periodActionLoading tras completar', fakeAsync(() => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.data.set({ ...MOCK_DATA, status: 'curso' });

      component.closePeriod();
      expect(component.periodActionLoading()).toBeTrue();

      const req = httpMock.expectOne(r => r.url.includes('/close'));
      req.flush({ ...MOCK_DATA, status: 'cerrado' });
      tick();
      fixture.detectChanges();

      expect(component.periodActionLoading()).toBeFalse();
    }));
  });

  // ── openPeriod ─────────────────────────────────────────────────────────────

  describe('openPeriod', () => {
    beforeEach(() => {
      fixture.detectChanges();
      flushCashflow();
    });

    it('sin recurringMaterialized muestra mensaje de primera apertura', () => {
      const confirmSpy = spyOn(window, 'confirm').and.returnValue(false);
      component.data.set({ ...MOCK_DATA, status: 'cerrado', recurringMaterialized: false });

      component.openPeriod();

      const msg: string = confirmSpy.calls.mostRecent().args[0] as string;
      expect(msg).toContain('se cargarán');
    });

    it('con recurringMaterialized muestra mensaje de reapertura', () => {
      const confirmSpy = spyOn(window, 'confirm').and.returnValue(false);
      component.data.set({ ...MOCK_DATA, status: 'cerrado', recurringMaterialized: true });

      component.openPeriod();

      const msg: string = confirmSpy.calls.mostRecent().args[0] as string;
      expect(msg).toContain('no se volverán a cargar');
    });

    it('llama al servicio con año/mes correctos cuando confirma', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.data.set({ ...MOCK_DATA, status: 'cerrado', recurringMaterialized: false });

      component.openPeriod();

      const req = httpMock.expectOne(r =>
        r.url === `${environment.apiUrl}/cashflow/${MOCK_DATA.year}/${MOCK_DATA.month}/open`
        && r.method === 'POST'
      );
      expect(req).toBeTruthy();
      req.flush({ ...MOCK_DATA, status: 'abierto', recurringMaterialized: true });
      fixture.detectChanges();
    });

    it('cancela si el usuario rechaza confirm', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      component.data.set({ ...MOCK_DATA, status: 'cerrado' });

      component.openPeriod();

      httpMock.expectNone(r => r.url.includes('/open'));
    });
  });

  // ── isClosingNegative / closingBalanceLabel ────────────────────────────────

  describe('isClosingNegative and closingBalanceLabel', () => {
    beforeEach(() => {
      fixture.detectChanges();
      flushCashflow();
    });

    it('isClosingNegative is true when closingBalance total is negative', () => {
      component['data'].set({ ...MOCK_DATA, closingBalance: { ...MOCK_DATA.closingBalance, total: { ars: -500, usd: 0 } } });
      expect(component.isClosingNegative()).toBeTrue();
      expect(component.closingBalanceLabel()).toBe('Necesidad de liquidez');
    });

    it('isClosingNegative is false when closingBalance total is positive', () => {
      component['data'].set({ ...MOCK_DATA, closingBalance: { ...MOCK_DATA.closingBalance, total: { ars: 1000, usd: 0 } } });
      expect(component.isClosingNegative()).toBeFalse();
      expect(component.closingBalanceLabel()).toBe('Saldo final disponible');
    });
  });

  // ── confirmProjection ──────────────────────────────────────────────────────

  describe('confirmProjection', () => {
    beforeEach(() => {
      fixture.detectChanges();
      flushCashflow();
    });

    it('confirmProjection() calls the project endpoint and updates data', fakeAsync(() => {
      // Alinear el período del componente con MOCK_DATA: confirmProjection() usa this.year()/this.month()
      // (inicializados desde la fecha actual), por lo que fijarlos evita que el test dependa del reloj.
      component.year.set(MOCK_DATA.year);
      component.month.set(MOCK_DATA.month);

      component.data.set({
        ...MOCK_DATA,
        status: 'proyectado',
        isProjection: true,
        needsConfirmation: true,
      });

      component.confirmProjection();
      expect(component.periodActionLoading()).toBeTrue();

      const req = httpMock.expectOne(r =>
        r.url === `${environment.apiUrl}/cashflow/${MOCK_DATA.year}/${MOCK_DATA.month}/project`
        && r.method === 'POST'
      );
      expect(req).toBeTruthy();

      const updatedData: CashflowResponse = {
        ...MOCK_DATA,
        status: 'proyectado',
        isProjection: true,
        needsConfirmation: false,
      };
      req.flush(updatedData);
      tick();
      fixture.detectChanges();

      expect(component.periodActionLoading()).toBeFalse();
      expect(component.data()).toEqual(updatedData);
      expect(component.needsConfirmation()).toBeFalse();
    }));
  });

  // ── Regresión: cashflow bimonetario (fmtMoney) ─────────────────────────────
  // El bug original sumaba ARS y USD como si fueran la misma unidad (una amortización de
  // 10,16 USD aparecía como $10). El backend ahora desglosa cada agregado por moneda
  // (MoneyByCcy); fmtMoney es el punto único donde el frontend convierte y suma ambos
  // buckets a la moneda seleccionada.
  describe('fmtMoney — conversión bimonetaria (regresión del bug ARS+USD)', () => {
    let currencyService: CurrencyService;

    beforeEach(() => {
      fixture.detectChanges();
      flushCashflow();
      currencyService = TestBed.inject(CurrencyService);
      // periodRate() lee data().oficialRateAtPeriod; MOCK_DATA trae '1062.5000' por defecto,
      // lo pisamos con una cotización redonda para que el cálculo sea fácil de verificar.
      component.data.set({ ...MOCK_DATA, oficialRateAtPeriod: '1000.0000' });
    });

    it('con toggle en ARS, suma ars + (usd convertido a ars) — no colapsa USD a "mismo número"', () => {
      currencyService.selected.set('ARS');
      // 1000 ARS + 10 USD a 1000 = 1000 + 10000 = 11000
      expect(component.fmtMoney({ ars: 1000, usd: 10 })).toBe('$ 11.000');
    });

    it('con toggle en USD, suma (ars convertido a usd) + usd', () => {
      currencyService.selected.set('USD');
      // 1000 ARS a 1000 = 1 USD; + 10 USD = 11 USD
      expect(component.fmtMoney({ ars: 1000, usd: 10 })).toBe('US$ 11,00');
    });

    it('un monto puramente en USD ya no aparece truncado como si fuera ARS (bug original)', () => {
      currencyService.selected.set('ARS');
      // 10,16 USD a 1000 = 10160 ARS (antes del fix, se mostraba "$ 10" por tratar el monto como ARS)
      expect(component.fmtMoney({ ars: 0, usd: 10.16 })).toBe('$ 10.160');
    });

    it('sin cotización disponible, cae al bucket ARS sin romper', () => {
      component.data.set({ ...MOCK_DATA, oficialRateAtPeriod: null });
      currencyService.selected.set('USD');
      // Sin cotización oficial vigente (ni de período ni actual): convertHistorical no puede convertir.
      (currencyService as unknown as { currentRateARS: () => string | null }).currentRateARS = () => null;
      expect(component.fmtMoney({ ars: 1000, usd: 10 })).toBe('$ 1.000');
    });
  });
});
