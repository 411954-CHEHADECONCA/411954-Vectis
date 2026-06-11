import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RecurringMovementService } from './recurring-movement.service';
import { environment } from '../../../environments/environment';
import { RecurringMovementRequest, RecurringMovementResponse } from '../models/recurring-movement.models';

const BASE = `${environment.apiUrl}/recurring-movements`;

const MOCK_RESPONSE: RecurringMovementResponse = {
  id: 'rm-1', description: 'Netflix', amount: 15000, ccy: 'ARS', type: 'EXPENSE',
  categoryId: null, categoryName: null, categoryIcon: null, categoryColor: null,
  accountId: null, accountName: null, dayOfMonth: 10, active: true,
  createdAt: '2026-06-10T00:00:00Z',
};

const MOCK_REQUEST: RecurringMovementRequest = {
  description: 'Netflix', amount: 15000, ccy: 'ARS', type: 'EXPENSE',
  categoryId: null, accountId: null, dayOfMonth: 10,
};

describe('RecurringMovementService', () => {
  let service: RecurringMovementService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(RecurringMovementService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getRecurringMovements sends GET to base URL', () => {
    service.getRecurringMovements().subscribe(res => expect(res).toEqual([MOCK_RESPONSE]));

    const req = http.expectOne(BASE);
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_RESPONSE]);
  });

  it('createRecurringMovement sends POST to base URL', () => {
    service.createRecurringMovement(MOCK_REQUEST).subscribe(res => expect(res).toEqual(MOCK_RESPONSE));

    const req = http.expectOne(BASE);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_RESPONSE);
  });

  it('updateRecurringMovement sends PUT to /{id}', () => {
    service.updateRecurringMovement('rm-1', MOCK_REQUEST).subscribe(res => expect(res).toEqual(MOCK_RESPONSE));

    const req = http.expectOne(`${BASE}/rm-1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_RESPONSE);
  });

  it('toggleActive sends PATCH to /{id}/toggle with empty body', () => {
    service.toggleActive('rm-1').subscribe(res => expect(res).toEqual(MOCK_RESPONSE));

    const req = http.expectOne(`${BASE}/rm-1/toggle`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({});
    req.flush(MOCK_RESPONSE);
  });

  it('deleteRecurringMovement sends DELETE to /{id}', () => {
    service.deleteRecurringMovement('rm-1').subscribe();

    const req = http.expectOne(`${BASE}/rm-1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
