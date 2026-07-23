import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MacroService } from './macro.service';
import { environment } from '../../../environments/environment';
import { ExchangeRateResponse, InflationResponse } from '../models/macro.models';

const MOCK_OFICIAL: ExchangeRateResponse = {
  rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000',
  rateDate: '2026-06-22', source: 'dolarapi.com',
};

const MOCK_MEP: ExchangeRateResponse = {
  rateType: 'MEP', buy: '1200.0000', sell: '1202.0000',
  rateDate: '2026-06-22', source: 'argentinadatos.com',
};

const MOCK_INFLATION: InflationResponse = {
  monthlyRate: '2.4000', periodDate: '2026-05-31', source: 'argentinadatos.com',
};

describe('MacroService', () => {
  let service: MacroService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(MacroService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getLatestOficialRate envia GET a /api/exchange-rates/oficial/latest', () => {
    service.getLatestOficialRate().subscribe(res => expect(res).toEqual(MOCK_OFICIAL));

    const req = http.expectOne(`${environment.apiUrl}/exchange-rates/oficial/latest`);
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_OFICIAL);
  });

  it('getLatestMepRate envia GET a /api/exchange-rates/mep/latest', () => {
    service.getLatestMepRate().subscribe(res => expect(res).toEqual(MOCK_MEP));

    const req = http.expectOne(`${environment.apiUrl}/exchange-rates/mep/latest`);
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_MEP);
  });

  it('getLatestInflation envia GET a /api/inflation/latest', () => {
    service.getLatestInflation().subscribe(res => expect(res).toEqual(MOCK_INFLATION));

    const req = http.expectOne(`${environment.apiUrl}/inflation/latest`);
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_INFLATION);
  });
});
