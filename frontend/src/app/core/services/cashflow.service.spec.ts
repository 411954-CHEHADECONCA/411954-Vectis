import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CashflowService } from './cashflow.service';
import { CashflowResponse } from '../models/cashflow.models';

const MOCK_RESPONSE: CashflowResponse = {
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
  openingBalance: { total: { ars: 8900000, usd: 0 }, accounts: [] },
  income:  { total: { ars: 1831420, usd: 0 }, totalBudgeted: { ars: 0, usd: 0 }, byCategory: [] },
  expenses: { total: { ars: 1677400, usd: 0 }, totalBudgeted: { ars: 0, usd: 0 }, byCategory: [] },
  preInvestmentBalance: { balance: { ars: 9054020, usd: 0 }, operativeResult: { ars: 153620, usd: 0 }, savingRatePct: '8.39' },
  investments: { total: { ars: 800000, usd: 0 }, pctOfPreBalance: '8.84', instruments: [] },
  closingBalance: { total: { ars: 8254020, usd: 0 }, accounts: [] },
  oficialRateAtPeriod: null,
  earliestNavigableYear:  2026,
  earliestNavigableMonth: 6,
};

describe('CashflowService', () => {
  let service: CashflowService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CashflowService],
    });
    service  = TestBed.inject(CashflowService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getCashflow should hit /api/cashflow with year and month params', () => {
    service.getCashflow(2026, 6).subscribe(res => {
      expect(res.periodLabel).toBe('Junio 2026');
    });

    const req = httpMock.expectOne(r =>
      r.url === '/api/cashflow' &&
      r.params.get('year')  === '2026' &&
      r.params.get('month') === '6'
    );
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_RESPONSE);
  });

  it('getCashflow should use correct year and month in query params', () => {
    service.getCashflow(2025, 12).subscribe();

    const req = httpMock.expectOne(r =>
      r.params.get('year')  === '2025' &&
      r.params.get('month') === '12'
    );
    req.flush(MOCK_RESPONSE);
  });

  it('closePeriod should POST to /api/cashflow/{year}/{month}/close', () => {
    service.closePeriod(2026, 6).subscribe(res => {
      expect(res.status).toBe('cerrado');
    });

    const req = httpMock.expectOne('/api/cashflow/2026/6/close');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_RESPONSE, status: 'cerrado' });
  });

  it('openPeriod should POST to /api/cashflow/{year}/{month}/open', () => {
    service.openPeriod(2026, 6).subscribe(res => {
      expect(res.status).toBe('abierto');
    });

    const req = httpMock.expectOne('/api/cashflow/2026/6/open');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_RESPONSE, status: 'abierto', recurringMaterialized: true });
  });

  it('confirmProjection should POST to /api/cashflow/{year}/{month}/project', () => {
    service.confirmProjection(2026, 7).subscribe(res => {
      expect(res.needsConfirmation).toBeFalse();
    });

    const req = httpMock.expectOne('/api/cashflow/2026/7/project');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_RESPONSE, needsConfirmation: false });
  });
});
