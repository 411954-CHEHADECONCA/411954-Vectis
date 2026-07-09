import { PortfolioSummaryService } from './portfolio-summary.service';
import { InvestmentMovement, InvestmentResponse, InvestmentValuation } from '../models/investment.models';

function mov(partial: Partial<InvestmentMovement>): InvestmentMovement {
  return { id: 'm', movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 0, units: null, createdAt: '', ...partial };
}

function val(partial: Partial<InvestmentValuation>): InvestmentValuation {
  return { id: 'v', valuationDate: '2026-01-01', pricePerUnit: 1, source: 'MANUAL', createdAt: '', ...partial };
}

function inv(partial: Partial<InvestmentResponse>): InvestmentResponse {
  return {
    id: 'i', name: 'Activo', type: 'PLAZO_FIJO', currency: 'ARS', principal: 0,
    purchaseDate: '2026-01-01', maturityDate: null, tna: 0, accountId: null,
    accountName: null, autoTrack: false, externalId: null, includeInCashflow: true,
    createdAt: '', updatedAt: '', movements: [], valuations: [], ...partial,
  };
}

describe('PortfolioSummaryService', () => {
  let service: PortfolioSummaryService;

  beforeEach(() => {
    service = new PortfolioSummaryService();
  });

  describe('saldoFci', () => {
    it('suma suscripciones y revalúos, resta rescates', () => {
      const asset = inv({
        type: 'FCI',
        movements: [
          mov({ type: 'SUSCRIPCION', amount: 1000 }),
          mov({ type: 'REVALUO', amount: 50 }),
          mov({ type: 'RESCATE', amount: 200 }),
        ],
      });
      expect(service.saldoFci(asset)).toBe(850);
    });

    it('es 0 sin movimientos', () => {
      expect(service.saldoFci(inv({ type: 'FCI' }))).toBe(0);
    });
  });

  describe('cuotapartesHeld', () => {
    const asset = inv({
      type: 'FCI_CUOTAPARTES',
      movements: [
        mov({ movementDate: '2026-01-01', type: 'SUSCRIPCION', amount: 100, units: 100 }),
        mov({ movementDate: '2026-02-01', type: 'SUSCRIPCION', amount: 50, units: 50 }),
        mov({ movementDate: '2026-03-01', type: 'RESCATE', amount: 30, units: 30 }),
      ],
    });

    it('suma/resta nominales según tipo', () => {
      expect(service.cuotapartesHeld(asset)).toBe(120);
    });

    it('respeta el corte upTo (inclusive)', () => {
      expect(service.cuotapartesHeld(asset, '2026-02-01')).toBe(150);
    });
  });

  describe('latestValuacion', () => {
    it('devuelve la de fecha máxima', () => {
      const asset = inv({ valuations: [val({ valuationDate: '2026-01-01', pricePerUnit: 1 }), val({ valuationDate: '2026-03-01', pricePerUnit: 3 })] });
      expect(service.latestValuacion(asset)?.pricePerUnit).toBe(3);
    });

    it('null si no hay valuaciones', () => {
      expect(service.latestValuacion(inv({}))).toBeNull();
    });
  });

  describe('valorActualCP', () => {
    it('usa nominales × último precio', () => {
      const asset = inv({
        type: 'BONO',
        movements: [mov({ type: 'SUSCRIPCION', amount: 100, units: 100 })],
        valuations: [val({ valuationDate: '2026-07-01', pricePerUnit: 1.25 })],
      });
      expect(service.valorActualCP(asset)).toBe(125);
    });

    it('LETRA sin valuación: valor de rescate = nominales × 1', () => {
      const asset = inv({ type: 'LETRA', movements: [mov({ type: 'SUSCRIPCION', amount: 100, units: 90 })] });
      expect(service.valorActualCP(asset)).toBe(90);
    });

    it('otro tipo sin valuación cae al principal', () => {
      const asset = inv({ type: 'BONO', principal: 500 });
      expect(service.valorActualCP(asset)).toBe(500);
    });
  });

  describe('currentValue', () => {
    it('PLAZO_FIJO devuelve el principal', () => {
      expect(service.currentValue(inv({ type: 'PLAZO_FIJO', principal: 300 }))).toBe(300);
    });

    it('FCI devuelve el saldo', () => {
      const asset = inv({ type: 'FCI', movements: [mov({ type: 'SUSCRIPCION', amount: 400 })] });
      expect(service.currentValue(asset)).toBe(400);
    });
  });

  describe('unrealizedGain', () => {
    it('valor actual − principal', () => {
      const asset = inv({
        type: 'BONO', principal: 100,
        movements: [mov({ type: 'SUSCRIPCION', amount: 100, units: 100 })],
        valuations: [val({ valuationDate: '2026-07-01', pricePerUnit: 1.2 })],
      });
      expect(service.unrealizedGain(asset)).toBeCloseTo(20, 6);
    });
  });

  describe('agregaciones', () => {
    const assets: InvestmentResponse[] = [
      inv({ id: 'pf', type: 'PLAZO_FIJO', currency: 'ARS', principal: 200_000 }),
      inv({
        id: 'bono', type: 'BONO', currency: 'USD', principal: 100,
        movements: [mov({ type: 'SUSCRIPCION', amount: 100, units: 100 })],
        valuations: [val({ valuationDate: '2026-07-01', pricePerUnit: 1.2 })],
      }),
      inv({ id: 'cobrada', type: 'PLAZO_FIJO', currency: 'ARS', principal: 999, status: 'COBRADA' }),
    ];

    it('totalsByCurrency ignora los activos COBRADA', () => {
      const t = service.totalsByCurrency(assets);
      expect(t.ars).toBe(200_000);
      expect(t.usd).toBe(120);
    });

    it('unrealizedGainByCurrency separa por moneda', () => {
      const g = service.unrealizedGainByCurrency(assets);
      expect(g.ars).toBe(0);
      expect(g.usd).toBeCloseTo(20, 6);
    });

    it('compositionByType agrupa por tipo con su label', () => {
      const comp = service.compositionByType(assets);
      const pf = comp.find(c => c.type === 'PLAZO_FIJO');
      const bono = comp.find(c => c.type === 'BONO');
      expect(pf?.ars).toBe(200_000);
      expect(pf?.label).toBe('Plazo fijo');
      expect(bono?.usd).toBe(120);
      expect(bono?.label).toBe('Bono soberano');
    });
  });

  describe('dailyChange', () => {
    it('con ≥2 valuaciones compara las dos más recientes', () => {
      const asset = inv({
        type: 'BONO',
        movements: [mov({ type: 'SUSCRIPCION', amount: 100, units: 100 })],
        valuations: [
          val({ valuationDate: '2026-06-30', pricePerUnit: 1.0 }),
          val({ valuationDate: '2026-07-01', pricePerUnit: 1.2 }),
        ],
      });
      const change = service.dailyChange(asset);
      expect(change.pct).toBeCloseTo(0.2, 6);       // 1.2/1.0 − 1
      expect(change.amount).toBeCloseTo(20, 6);      // 100 × (1.2 − 1.0)
    });

    it('con <2 valuaciones usa el devengado diario por TNA', () => {
      const asset = inv({ type: 'PLAZO_FIJO', principal: 3650, tna: 10 });
      const change = service.dailyChange(asset);
      expect(change.pct).toBeCloseTo(0.1 / 365, 8);  // TNA/365
      expect(change.amount).toBeCloseTo(1, 6);        // 3650 × 0.1/365
    });

    it('dailyChangeByCurrency separa por moneda y omite COBRADA', () => {
      const assets: InvestmentResponse[] = [
        inv({ id: 'pf', type: 'PLAZO_FIJO', currency: 'ARS', principal: 3650, tna: 10 }),
        inv({
          id: 'bono', type: 'BONO', currency: 'USD',
          movements: [mov({ type: 'SUSCRIPCION', amount: 100, units: 100 })],
          valuations: [val({ valuationDate: '2026-06-30', pricePerUnit: 1.0 }), val({ valuationDate: '2026-07-01', pricePerUnit: 1.2 })],
        }),
        inv({ id: 'x', type: 'PLAZO_FIJO', currency: 'ARS', principal: 1000, tna: 10, status: 'COBRADA' }),
      ];
      const d = service.dailyChangeByCurrency(assets);
      expect(d.ars).toBeCloseTo(1, 6);
      expect(d.usd).toBeCloseTo(20, 6);
    });
  });
});
