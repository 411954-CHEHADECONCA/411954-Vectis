import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { InvestmentService } from './investment.service';
import {
  FciFundOption,
  InstrumentOption,
  InvestmentMovementRequest,
  InvestmentRequest,
  InvestmentResponse,
  InvestmentValuationRequest,
} from '../models/investment.models';
import { environment } from '../../../environments/environment';

const MOCK_INVESTMENT: InvestmentResponse = {
  id:           'inv-1',
  name:         'FCI Ahorro',
  type:         'FCI',
  currency:     'ARS',
  principal:    1200000,
  purchaseDate: '2026-01-10',
  maturityDate: null,
  tna:          65.5,
  accountId:    null,
  accountName:  null,
  autoTrack:    false,
  externalId:   null,
  createdAt:    '2026-06-01T00:00:00Z',
  updatedAt:    '2026-06-01T00:00:00Z',
  movements:    [],
  valuations:   [],
};

const MOCK_REQUEST: InvestmentRequest = {
  name:         'FCI Ahorro',
  type:         'FCI',
  currency:     'ARS',
  principal:    1200000,
  purchaseDate: '2026-01-10',
  maturityDate: null,
  tna:          65.5,
  accountId:    null,
  autoTrack:    false,
  externalId:   null,
};

describe('InvestmentService', () => {
  let service: InvestmentService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/investments`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [InvestmentService],
    });
    service  = TestBed.inject(InvestmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('se crea correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('getInvestments() hace GET a /api/investments', () => {
    service.getInvestments().subscribe(list => {
      expect(list.length).toBe(1);
      expect(list[0].name).toBe('FCI Ahorro');
    });

    const req = httpMock.expectOne(r => r.url === baseUrl && r.method === 'GET');
    req.flush([MOCK_INVESTMENT]);
  });

  it('createInvestment() hace POST a /api/investments con el body correcto', () => {
    service.createInvestment(MOCK_REQUEST).subscribe(res => {
      expect(res.id).toBe('inv-1');
      expect(res.name).toBe('FCI Ahorro');
    });

    const req = httpMock.expectOne(r => r.url === baseUrl && r.method === 'POST');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_INVESTMENT);
  });

  it('updateInvestment() hace PUT a /api/investments/{id} con el body correcto', () => {
    const id = 'inv-1';

    service.updateInvestment(id, MOCK_REQUEST).subscribe(res => {
      expect(res.name).toBe('FCI Ahorro');
    });

    const req = httpMock.expectOne(r => r.url === `${baseUrl}/${id}` && r.method === 'PUT');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_INVESTMENT);
  });

  it('deleteInvestment() hace DELETE a /api/investments/{id}', () => {
    const id = 'inv-1';

    service.deleteInvestment(id).subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpMock.expectOne(r => r.url === `${baseUrl}/${id}` && r.method === 'DELETE');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('addMovement() hace POST a /api/investments/{id}/movements con el body correcto', () => {
    const investmentId = 'inv-1';
    const movRequest: InvestmentMovementRequest = {
      movementDate: '2026-06-10',
      type:         'SUSCRIPCION',
      amount:       500000,
    };
    const expectedResponse: InvestmentResponse = {
      ...MOCK_INVESTMENT,
      movements: [{
        id:           'mov-1',
        movementDate: '2026-06-10',
        type:         'SUSCRIPCION',
        amount:       500000,
        units:        null,
        createdAt:    '2026-06-10T00:00:00Z',
      }],
    };

    service.addMovement(investmentId, movRequest).subscribe(res => {
      expect(res.movements.length).toBe(1);
      expect(res.movements[0].type).toBe('SUSCRIPCION');
      expect(res.movements[0].amount).toBe(500000);
    });

    const req = httpMock.expectOne(
      r => r.url === `${baseUrl}/${investmentId}/movements` && r.method === 'POST',
    );
    expect(req.request.body).toEqual(movRequest);
    req.flush(expectedResponse);
  });

  it('deleteMovement() hace DELETE a /api/investments/{id}/movements/{movId}', () => {
    const investmentId = 'inv-1';
    const movId        = 'mov-1';

    service.deleteMovement(investmentId, movId).subscribe(res => {
      expect(res.movements.length).toBe(0);
    });

    const req = httpMock.expectOne(
      r => r.url === `${baseUrl}/${investmentId}/movements/${movId}` && r.method === 'DELETE',
    );
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...MOCK_INVESTMENT, movements: [] });
  });

  it('updateMovement() hace PUT a /api/investments/{id}/movements/{movId} con el body', () => {
    const investmentId = 'inv-1';
    const movId        = 'mov-1';
    const body         = { interestOverride: 12500 };

    service.updateMovement(investmentId, movId, body).subscribe(res => {
      expect(res).toBeTruthy();
    });

    const req = httpMock.expectOne(
      r => r.url === `${baseUrl}/${investmentId}/movements/${movId}` && r.method === 'PUT',
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush(MOCK_INVESTMENT);
  });

  it('addValuation() envía POST al endpoint correcto', () => {
    const req: InvestmentValuationRequest = { valuationDate: '2026-06-01', pricePerUnit: 1500 };
    service.addValuation('inv-1', req).subscribe();
    const r = httpMock.expectOne(`${environment.apiUrl}/investments/inv-1/valuations`);
    expect(r.request.method).toBe('POST');
    r.flush({});
  });

  it('updateValuation() envía PUT al endpoint correcto', () => {
    const req: InvestmentValuationRequest = { valuationDate: '2026-06-15', pricePerUnit: 1600 };
    service.updateValuation('inv-1', 'val-1', req).subscribe();
    const r = httpMock.expectOne(`${environment.apiUrl}/investments/inv-1/valuations/val-1`);
    expect(r.request.method).toBe('PUT');
    r.flush({});
  });

  it('deleteValuation() envía DELETE al endpoint correcto', () => {
    service.deleteValuation('inv-1', 'val-1').subscribe();
    const r = httpMock.expectOne(`${environment.apiUrl}/investments/inv-1/valuations/val-1`);
    expect(r.request.method).toBe('DELETE');
    r.flush({});
  });

  it('getFciFunds() hace GET a /market/fci-funds con el parámetro categoria correcto', () => {
    const mockFunds: FciFundOption[] = [
      { fondo: 'FCI Galileo Growth', categoria: 'mercadoDinero', vcp: 1523.456, fecha: '2026-06-24' },
    ];

    service.getFciFunds('mercadoDinero').subscribe(funds => {
      expect(funds.length).toBe(1);
      expect(funds[0].fondo).toBe('FCI Galileo Growth');
      expect(funds[0].categoria).toBe('mercadoDinero');
    });

    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/market/fci-funds` &&
      r.method === 'GET' &&
      r.params.get('categoria') === 'mercadoDinero',
    );
    req.flush(mockFunds);
  });

  it('getFciFunds() soporta la categoría rentaMixta', () => {
    const mockFunds: FciFundOption[] = [
      { fondo: 'Pionero Multiestrategia Mix - Clase B', categoria: 'rentaMixta', vcp: 1694.285, fecha: '2026-06-26' },
    ];

    service.getFciFunds('rentaMixta').subscribe(funds => {
      expect(funds.length).toBe(1);
      expect(funds[0].categoria).toBe('rentaMixta');
    });

    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/market/fci-funds` &&
      r.method === 'GET' &&
      r.params.get('categoria') === 'rentaMixta',
    );
    req.flush(mockFunds);
  });

  it('getInstruments() hace GET a /market/instruments con el parámetro tipo correcto', () => {
    const mockInstruments: InstrumentOption[] = [
      { ticker: 'S31G5', nombre: 'LECAP 31/08/2025', tipo: 'LETRA', lastPrice: 1020.5, priceDate: '2026-06-24', maturityDate: '2025-08-31' },
    ];

    service.getInstruments('LETRA').subscribe(instruments => {
      expect(instruments.length).toBe(1);
      expect(instruments[0].ticker).toBe('S31G5');
      expect(instruments[0].tipo).toBe('LETRA');
    });

    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/market/instruments` &&
      r.method === 'GET' &&
      r.params.get('tipo') === 'LETRA',
    );
    req.flush(mockInstruments);
  });

  // ── getFciVcp ────────────────────────────────────────────────────────────────

  it('getFciVcp() hace GET a /market/fci-vcp con los params correctos', () => {
    const mockVcp = { fondo: 'FCI Galileo Growth', vcp: 1523.456, fecha: '2026-06-26' };

    service.getFciVcp('FCI Galileo Growth', '2026-06-26').subscribe(res => {
      expect(res).not.toBeNull();
      expect(res?.fondo).toBe('FCI Galileo Growth');
      expect(res?.vcp).toBe(1523.456);
    });

    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/market/fci-vcp` &&
      r.method === 'GET' &&
      r.params.get('fondo') === 'FCI Galileo Growth' &&
      r.params.get('fecha') === '2026-06-26',
    );
    req.flush(mockVcp);
  });

  it('getFciVcp() retorna null cuando el servidor devuelve 404', () => {
    service.getFciVcp('FONDO INEXISTENTE', '2026-06-26').subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/market/fci-vcp` && r.method === 'GET',
    );
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });

  // ── getInstrumentPrice ───────────────────────────────────────────────────────

  it('getInstrumentPrice() hace GET a /market/instrument-price con los params correctos', () => {
    const mockPrice = { ticker: 'S31G5', type: 'LETRA', fecha: '2026-06-26', pricePerUnit: 1020.5 };

    service.getInstrumentPrice('S31G5', 'LETRA', '2026-06-26').subscribe(res => {
      expect(res).not.toBeNull();
      expect(res?.pricePerUnit).toBe(1020.5);
      expect(res?.fecha).toBe('2026-06-26'); // fecha del cierre real devuelta por el backend
    });

    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/market/instrument-price` &&
      r.method === 'GET' &&
      r.params.get('ticker') === 'S31G5' &&
      r.params.get('type') === 'LETRA' &&
      r.params.get('fecha') === '2026-06-26',
    );
    req.flush(mockPrice);
  });

  it('getInstrumentPrice() retorna null cuando el servidor devuelve 404', () => {
    service.getInstrumentPrice('TICKER_INVALIDO', 'BONO', '2026-06-26').subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpMock.expectOne(r =>
      r.url === `${baseUrl}/market/instrument-price` && r.method === 'GET',
    );
    req.flush({ message: 'Not found' }, { status: 404, statusText: 'Not Found' });
  });
});
