import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { CashflowComponent } from './cashflow.component';
import { CashflowResponse } from '../../core/models/cashflow.models';
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
  openingBalance: { total: '8900000.0000', accounts: [
    { accountId: 'a1', name: 'Galicia', ccy: 'ARS', balance: '8900000.0000' },
  ]},
  income:  { total: '1831420.0000', totalBudgeted: '0', byCategory: [
    { categoryId: 'c1', name: 'Sueldo', icon: 'briefcase', color: '#52eacd', amount: '1831420.0000', pctOfTotal: '100.00', budgeted: null, pctOfBudget: null },
  ]},
  expenses: { total: '1677400.0000', totalBudgeted: '460000.0000', byCategory: [
    { categoryId: 'c2', name: 'Vivienda', icon: 'home', color: '#ffb4ab', amount: '480000.0000', pctOfTotal: '28.62', budgeted: '460000.0000', pctOfBudget: '104.35' },
  ]},
  preInvestmentBalance: { balance: '9054020.0000', operativeResult: '153620.0000', savingRatePct: '8.39' },
  investments: { total: '800000.0000', pctOfPreBalance: '8.84', instruments: [
    { name: 'Inversiones', icon: 'trending-up', color: '#8b5cf6', amount: '800000.0000', teaPct: null },
  ]},
  closingBalance: { total: '8254020.0000', accounts: [
    { accountId: 'a1', name: 'Galicia', ccy: 'ARS', balance: '8254020.0000' },
  ]},
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

  /** Flush both the current-month and prev-month GET requests fired by forkJoin. */
  function flushCashflow(): void {
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

    it('canGoBack returns false when exactly 12 months before current month', () => {
      const now = new Date();
      let y = now.getFullYear();
      let m = now.getMonth() + 1 - 12;
      if (m < 1) { m += 12; y--; }
      component.year.set(y);
      component.month.set(m);
      expect(component.canGoBack()).toBeFalse();
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
    const reqs = httpMock.match(r => r.url === '/api/cashflow');
    reqs[0].flush({ ...MOCK_DATA, isProjection: true, status: 'proyectado' });
    if (reqs[1]) reqs[1].flush(MOCK_DATA);
    fixture.detectChanges();
    expect(component.isProjection()).toBeTrue();
  });

  it('should render error state when error signal is set', fakeAsync(() => {
    fixture.detectChanges();
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
      component['data'].set({ ...MOCK_DATA, closingBalance: { ...MOCK_DATA.closingBalance, total: '-500.0000' } });
      expect(component.isClosingNegative()).toBeTrue();
      expect(component.closingBalanceLabel()).toBe('Necesidad de liquidez');
    });

    it('isClosingNegative is false when closingBalance total is positive', () => {
      component['data'].set({ ...MOCK_DATA, closingBalance: { ...MOCK_DATA.closingBalance, total: '1000.0000' } });
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
});
