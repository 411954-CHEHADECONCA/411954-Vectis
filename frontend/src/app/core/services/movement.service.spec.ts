import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MovementService } from './movement.service';
import { environment } from '../../../environments/environment';
import { MovementRequest, MovementResponse } from '../models/movement.models';
import { PageResponse } from '../models/pagination.models';

const BASE = `${environment.apiUrl}/movements`;

const MOCK_RESPONSE: MovementResponse = {
  id: 'mv-1', type: 'EXPENSE', description: 'Coto', amount: 86400, ccy: 'ARS',
  categoryId: null, categoryName: null, categoryIcon: null, categoryColor: null,
  accountId: null, accountName: null, cardId: null, cardName: null,
  transactionDate: '2026-06-09', dueDate: '2026-06-09',
  installment: false, installmentNumber: null, totalInstallments: null,
  installmentGroupId: null, createdAt: '2026-06-09T00:00:00Z',
};

const MOCK_PAGE: PageResponse<MovementResponse> = {
  content: [MOCK_RESPONSE], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false,
};

const MOCK_REQUEST: MovementRequest = {
  description: 'Coto', amount: 86400, ccy: 'ARS', type: 'EXPENSE',
  categoryId: null, accountId: null, cardId: null,
  transactionDate: '2026-06-09', installments: 1,
};

describe('MovementService', () => {
  let service: MovementService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(MovementService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('search sends GET with from/to/page/size params', () => {
    service.search({ from: '2026-06-01', to: '2026-06-30', page: 0, size: 20 })
      .subscribe(res => expect(res).toEqual(MOCK_PAGE));

    const req = http.expectOne(r => r.url === BASE);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('from')).toBe('2026-06-01');
    expect(req.request.params.get('to')).toBe('2026-06-30');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush(MOCK_PAGE);
  });

  it('search includes optional type/categoryId/q when present', () => {
    service.search({ from: '2026-06-01', to: '2026-06-30', type: 'INCOME', categoryId: 'c-1', q: 'sueldo' })
      .subscribe();

    const req = http.expectOne(r => r.url === BASE);
    expect(req.request.params.get('type')).toBe('INCOME');
    expect(req.request.params.get('categoryId')).toBe('c-1');
    expect(req.request.params.get('q')).toBe('sueldo');
    req.flush(MOCK_PAGE);
  });

  it('summary sends GET to /summary without paging params', () => {
    service.summary({ from: '2026-06-01', to: '2026-06-30' }).subscribe();

    const req = http.expectOne(r => r.url === `${BASE}/summary`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBeNull();
    req.flush({ totalIncome: 0, totalExpense: 0, net: 0, count: 0 });
  });

  it('create sends POST to base URL', () => {
    service.create(MOCK_REQUEST).subscribe(res => expect(res).toEqual([MOCK_RESPONSE]));

    const req = http.expectOne(BASE);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush([MOCK_RESPONSE]);
  });

  it('update sends PUT to /{id}', () => {
    service.update('mv-1', MOCK_REQUEST).subscribe(res => expect(res).toEqual(MOCK_RESPONSE));

    const req = http.expectOne(`${BASE}/mv-1`);
    expect(req.request.method).toBe('PUT');
    req.flush(MOCK_RESPONSE);
  });

  it('delete sends DELETE to /{id}', () => {
    service.delete('mv-1').subscribe();

    const req = http.expectOne(`${BASE}/mv-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
