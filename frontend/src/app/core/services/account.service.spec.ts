import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AccountService } from './account.service';
import { AccountRequest, AccountResponse } from '../models/account.models';

const MOCK_ACCOUNT: AccountResponse = {
  id:         'acc-123',
  name:       'Cuenta Galicia',
  kind:       'Banco',
  detail:     'Caja de Ahorro $',
  ccy:        'ARS',
  balance:    150000,
  remunerada: false,
  tna:        null,
  createdAt:  '2026-06-10T00:00:00Z',
  updatedAt:  '2026-06-10T00:00:00Z',
};

const MOCK_REQUEST: AccountRequest = {
  name:       'Cuenta Galicia',
  kind:       'Banco',
  detail:     'Caja de Ahorro $',
  ccy:        'ARS',
  balance:    150000,
  remunerada: false,
  tna:        null,
};

describe('AccountService', () => {
  let service: AccountService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AccountService],
    });
    service  = TestBed.inject(AccountService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('se crea correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('getAccounts() hace GET a /api/accounts', () => {
    service.getAccounts().subscribe(accounts => {
      expect(accounts.length).toBe(1);
      expect(accounts[0].name).toBe('Cuenta Galicia');
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/accounts') && r.method === 'GET');
    req.flush([MOCK_ACCOUNT]);
  });

  it('createAccount() hace POST a /api/accounts con el body correcto', () => {
    service.createAccount(MOCK_REQUEST).subscribe(res => {
      expect(res.id).toBe('acc-123');
      expect(res.name).toBe('Cuenta Galicia');
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/accounts') && r.method === 'POST');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_ACCOUNT);
  });

  it('updateAccount() hace PUT a /api/accounts/{id} con el body correcto', () => {
    const id = 'acc-456';

    service.updateAccount(id, MOCK_REQUEST).subscribe(res => {
      expect(res.name).toBe('Cuenta Galicia');
    });

    const req = httpMock.expectOne(r => r.url.includes(`/api/accounts/${id}`) && r.method === 'PUT');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_ACCOUNT);
  });

  it('deleteAccount() hace DELETE a /api/accounts/{id}', () => {
    const id = 'acc-789';

    service.deleteAccount(id).subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpMock.expectOne(r => r.url.includes(`/api/accounts/${id}`) && r.method === 'DELETE');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
