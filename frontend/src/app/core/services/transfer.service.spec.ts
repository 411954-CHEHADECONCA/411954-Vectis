import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TransferService } from './transfer.service';
import { TransferRequest, TransferResponse } from '../models/transfer.models';

const MOCK_REQUEST: TransferRequest = {
  sourceAccountId: 'acc-src',
  destAccountId:   'acc-dst',
  sourceAmount:    10000,
  transactionDate: '2026-06-21',
  description:     'Recarga',
};

const MOCK_RESPONSE: TransferResponse = {
  transferGroupId:     'grp-1',
  sourceTransactionId: 'tx-src',
  destTransactionId:   'tx-dst',
  sourceAccountId:     'acc-src',
  sourceAccountName:   'Galicia ARS',
  destAccountId:       'acc-dst',
  destAccountName:     'Mercado Pago',
  sourceAmount:        10000,
  sourceCcy:           'ARS',
  destAmount:          10000,
  destCcy:             'ARS',
  transactionDate:     '2026-06-21',
  createdAt:           '2026-06-21T00:00:00Z',
};

describe('TransferService', () => {
  let service: TransferService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(TransferService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('create() hace POST a /api/transfers con el body correcto', () => {
    service.create(MOCK_REQUEST).subscribe(res => {
      expect(res.transferGroupId).toBe('grp-1');
      expect(res.sourceCcy).toBe('ARS');
    });

    const req = http.expectOne('/api/transfers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_RESPONSE);
  });

  it('delete() hace DELETE a /api/transfers/{id}', () => {
    service.delete('grp-1').subscribe();

    const req = http.expectOne('/api/transfers/grp-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
