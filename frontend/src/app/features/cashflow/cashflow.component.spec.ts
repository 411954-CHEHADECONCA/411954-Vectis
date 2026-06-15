import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { CashflowComponent } from './cashflow.component';
import { CashflowResponse } from '../../core/models/cashflow.models';

const MOCK_DATA: CashflowResponse = {
  year:        2026,
  month:       6,
  periodLabel: 'Junio 2026',
  monthShort:  'jun',
  status:      'curso',
  isProjection: false,
  openingBalance: { total: '8900000.0000', accounts: [
    { accountId: 'a1', name: 'Galicia', ccy: 'ARS', balance: '8900000.0000' },
  ]},
  income:  { total: '1831420.0000', byCategory: [
    { categoryId: 'c1', name: 'Sueldo', icon: 'briefcase', color: '#52eacd', amount: '1831420.0000', pctOfTotal: '100.00', budgeted: null, pctOfBudget: null },
  ]},
  expenses: { total: '1677400.0000', byCategory: [
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

  function flushCashflow(): void {
    const req = httpMock.expectOne(r => r.url === '/api/cashflow');
    req.flush(MOCK_DATA);
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
    const req = httpMock.expectOne(r => r.url === '/api/cashflow');
    req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
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
      const req = httpMock.expectOne(r => r.url === '/api/cashflow');
      req.flush(MOCK_DATA);
      fixture.detectChanges();

      const expectedMonth = initialMonth === 1 ? 12 : initialMonth - 1;
      expect(component.month()).toBe(expectedMonth);
    });

    it('nextMonth increments month and calls loadData', () => {
      const initialMonth = component.month();
      component.nextMonth();
      const req = httpMock.expectOne(r => r.url === '/api/cashflow');
      req.flush(MOCK_DATA);
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
    const req = httpMock.expectOne(r => r.url === '/api/cashflow');
    req.flush({ ...MOCK_DATA, isProjection: true, status: 'proyectado' });
    fixture.detectChanges();
    expect(component.isProjection()).toBeTrue();
  });

  it('should render error state when error signal is set', fakeAsync(() => {
    fixture.detectChanges();
    const req = httpMock.expectOne(r => r.url === '/api/cashflow');
    req.flush('Error', { status: 503, statusText: 'Service Unavailable' });
    tick();
    fixture.detectChanges();
    const errorEl = fixture.debugElement.query(By.css('.error-state'));
    expect(errorEl).not.toBeNull();
  }));
});
