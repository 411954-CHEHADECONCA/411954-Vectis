import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CashflowService } from './cashflow.service';
import { CashflowResponse } from '../models/cashflow.models';

const MOCK_RESPONSE: CashflowResponse = {
  year:        2026,
  month:       6,
  periodLabel: 'Junio 2026',
  monthShort:  'jun',
  status:      'curso',
  isProjection: false,
  openingBalance: { total: '8900000.0000', accounts: [] },
  income:  { total: '1831420.0000', totalBudgeted: '0', byCategory: [] },
  expenses: { total: '1677400.0000', totalBudgeted: '0', byCategory: [] },
  preInvestmentBalance: { balance: '9054020.0000', operativeResult: '153620.0000', savingRatePct: '8.39' },
  investments: { total: '800000.0000', pctOfPreBalance: '8.84', instruments: [] },
  closingBalance: { total: '8254020.0000', accounts: [] },
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
});
