import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CurrencyService } from './currency.service';
import { environment } from '../../../environments/environment';

const RATE_URL = `${environment.apiUrl}/exchange-rates/oficial/latest`;
const MOCK_RATE = { rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000', rateDate: '2026-06-22', source: 'test' };

describe('CurrencyService', () => {
  let service: CurrencyService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service  = TestBed.inject(CurrencyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushRate(sell = '1062.5000'): void {
    httpMock.expectOne(RATE_URL).flush({ ...MOCK_RATE, sell });
  }

  it('selected inicia en ARS', () => {
    flushRate();
    expect(service.selected()).toBe('ARS');
  });

  it('toggle alterna entre ARS y USD', () => {
    flushRate();
    service.toggle();
    expect(service.selected()).toBe('USD');
    service.toggle();
    expect(service.selected()).toBe('ARS');
  });

  it('symbol es $ cuando selected es ARS y US$ cuando es USD', () => {
    flushRate();
    expect(service.symbol()).toBe('$');
    service.selected.set('USD');
    expect(service.symbol()).toBe('US$');
  });

  it('currentRateARS devuelve el sell string de la cotizacion cargada', () => {
    flushRate('1062.5000');
    expect(service.currentRateARS()).toBe('1062.5000');
  });

  it('currentRateARS devuelve null antes de que cargue la cotizacion', () => {
    // Sin flush — la peticion HTTP todavía está pendiente
    expect(service.currentRateARS()).toBeNull();
    flushRate(); // evitar verify() failure
  });

  it('convert devuelve el monto sin cambio cuando selected === fromCcy', () => {
    flushRate();
    expect(service.convert(100000, 'ARS')).toBe(100000);
    service.selected.set('USD');
    expect(service.convert(500, 'USD')).toBe(500);
  });

  it('convert devuelve null cuando la cotizacion no esta cargada', () => {
    // Sin flush — rate no disponible
    service.selected.set('USD');
    expect(service.convert(100000, 'ARS')).toBeNull();
    flushRate();
  });

  it('convert de ARS a USD divide por la tasa', () => {
    flushRate('1062.5000');
    service.selected.set('USD');
    const result = service.convert(1062500, 'ARS');
    expect(result).toBeCloseTo(1000, 0);
  });

  it('convert de USD a ARS multiplica por la tasa', () => {
    flushRate('1062.5000');
    // selected = ARS (default)
    const result = service.convert(1000, 'USD');
    expect(result).toBeCloseTo(1062500, 0);
  });

  it('convertHistorical usa rateAtTime cuando esta presente', () => {
    flushRate('1062.5000');
    service.selected.set('USD');
    // Tasa historica diferente a la actual
    const result = service.convertHistorical(212500, 'ARS', '850.0000');
    expect(result).toBeCloseTo(250, 0);
  });

  it('convertHistorical cae a la tasa actual cuando rateAtTime es null', () => {
    flushRate('1062.5000');
    service.selected.set('USD');
    const result = service.convertHistorical(1062500, 'ARS', null);
    expect(result).toBeCloseTo(1000, 0);
  });

  it('convertHistorical devuelve null si no hay tasa ni rateAtTime', () => {
    // Sin flush — rate no disponible, rateAtTime null
    service.selected.set('USD');
    expect(service.convertHistorical(100000, 'ARS', null)).toBeNull();
    flushRate();
  });

  it('oficialRate es null hasta que la peticion HTTP se resuelve', () => {
    expect(service.oficialRate()).toBeNull();
    flushRate();
    expect(service.oficialRate()).toBeTruthy();
  });
});
