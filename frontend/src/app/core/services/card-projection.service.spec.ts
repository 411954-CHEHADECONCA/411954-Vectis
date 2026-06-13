import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CardProjectionService } from './card-projection.service';
import { environment } from '../../../environments/environment';
import { CardMatrix, CardOverview } from '../models/card-projection.models';

const BASE = `${environment.apiUrl}/cards`;

const MOCK_OVERVIEW: CardOverview = {
  cards: [], totalDue: 0, nextDueCardName: null, nextDueDate: null, nextDueAmount: null,
  cuotasActivas: [], vencimientos: [],
};

const MOCK_MATRIX: CardMatrix = { months: ['2026-06'], cards: [], totalsByMonth: [0] };

describe('CardProjectionService', () => {
  let service: CardProjectionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(CardProjectionService);
    http    = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getOverview sends GET to /overview', () => {
    service.getOverview().subscribe(res => expect(res).toEqual(MOCK_OVERVIEW));
    const req = http.expectOne(`${BASE}/overview`);
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_OVERVIEW);
  });

  it('getMatrix sends GET to /matrix with months param', () => {
    service.getMatrix(6).subscribe(res => expect(res).toEqual(MOCK_MATRIX));
    const req = http.expectOne(r => r.url === `${BASE}/matrix`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('months')).toBe('6');
    req.flush(MOCK_MATRIX);
  });
});
