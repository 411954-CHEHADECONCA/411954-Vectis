import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CreditCardService } from './credit-card.service';
import { CardRequest, CardResponse } from '../models/card.models';

const MOCK_CARD: CardResponse = {
  id:          'card-123',
  bank:        'Galicia',
  network:     'Visa',
  last4:       '1234',
  ccy:         'ARS',
  creditLimit: 500000,
  closingDay:  15,
  dueDay:      5,
  accent:      '#52eacd',
  createdAt:   '2026-06-10T00:00:00Z',
  updatedAt:   '2026-06-10T00:00:00Z',
};

const MOCK_REQUEST: CardRequest = {
  bank:        'Galicia',
  network:     'Visa',
  last4:       '1234',
  ccy:         'ARS',
  creditLimit: 500000,
  closingDay:  15,
  dueDay:      5,
  accent:      '#52eacd',
};

describe('CreditCardService', () => {
  let service: CreditCardService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CreditCardService],
    });
    service  = TestBed.inject(CreditCardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('se crea correctamente', () => {
    expect(service).toBeTruthy();
  });

  it('getCards() hace GET a /api/cards', () => {
    service.getCards().subscribe(cards => {
      expect(cards.length).toBe(1);
      expect(cards[0].bank).toBe('Galicia');
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/cards') && r.method === 'GET');
    req.flush([MOCK_CARD]);
  });

  it('createCard() hace POST a /api/cards con el body correcto', () => {
    service.createCard(MOCK_REQUEST).subscribe(res => {
      expect(res.id).toBe('card-123');
      expect(res.closingDay).toBe(15);
    });

    const req = httpMock.expectOne(r => r.url.includes('/api/cards') && r.method === 'POST');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_CARD);
  });

  it('updateCard() hace PUT a /api/cards/{id} con el body correcto', () => {
    const id = 'card-456';

    service.updateCard(id, MOCK_REQUEST).subscribe(res => {
      expect(res.bank).toBe('Galicia');
    });

    const req = httpMock.expectOne(r => r.url.includes(`/api/cards/${id}`) && r.method === 'PUT');
    expect(req.request.body).toEqual(MOCK_REQUEST);
    req.flush(MOCK_CARD);
  });

  it('deleteCard() hace DELETE a /api/cards/{id}', () => {
    const id = 'card-789';

    service.deleteCard(id).subscribe(res => {
      expect(res).toBeNull();
    });

    const req = httpMock.expectOne(r => r.url.includes(`/api/cards/${id}`) && r.method === 'DELETE');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
