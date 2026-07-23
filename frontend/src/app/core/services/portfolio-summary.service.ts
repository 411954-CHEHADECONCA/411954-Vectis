import { Injectable } from '@angular/core';
import {
  ASSET_TYPE_LABELS,
  InvestmentAssetType,
  InvestmentResponse,
  InvestmentValuation,
} from '../models/investment.models';

/** Monto separado por moneda de origen (sin convertir). El consumidor decide la conversión FX. */
export interface PortfolioByCurrency {
  ars: number;
  usd: number;
}

/** Un tramo de la composición de cartera: valor por tipo de instrumento, separado por moneda. */
export interface PortfolioCompositionSlice {
  type:  InvestmentAssetType;
  label: string;
  ars:   number;
  usd:   number;
}

/**
 * Fuente única de la valuación de activos de inversión. Extrae las primitivas de "valor actual"
 * que antes vivían embebidas en `InversionesComponent`, para que el dashboard y la vista de
 * Inversiones calculen exactamente los mismos números (DRY sobre montos financieros).
 *
 * Es un servicio puro: no depende de FX ni de fecha del sistema para el valor actual. La
 * conversión ARS↔USD la resuelve el consumidor con `CurrencyService`.
 */
@Injectable({ providedIn: 'root' })
export class PortfolioSummaryService {
  /** Saldo de una Cuenta Remunerada (FCI): suscripciones y revalúos suman, rescates restan. */
  saldoFci(asset: InvestmentResponse): number {
    return (asset.movements ?? []).reduce((acc, m) => {
      if (m.type === 'SUSCRIPCION' || m.type === 'REVALUO') return acc + +m.amount;
      if (m.type === 'RESCATE') return acc - +m.amount;
      return acc;
    }, 0);
  }

  /** Cuotapartes/nominales mantenidos (opcionalmente hasta `upTo` inclusive). */
  cuotapartesHeld(asset: InvestmentResponse, upTo?: string): number {
    const movs = (asset.movements ?? []).filter(m => m.units != null);
    const filtered = upTo ? movs.filter(m => m.movementDate <= upTo) : movs;
    return filtered.reduce((acc, m) =>
      acc + (m.type === 'SUSCRIPCION' ? +(m.units ?? 0) : -(m.units ?? 0)), 0);
  }

  /** Valuación más reciente por fecha (o null si no hay ninguna). */
  latestValuacion(asset: InvestmentResponse): InvestmentValuation | null {
    if (!asset.valuations?.length) return null;
    return [...asset.valuations].sort((a, b) => b.valuationDate.localeCompare(a.valuationDate))[0];
  }

  /** Valor actual de un instrumento cuotapartes/letra/bono/ON (nominales × último precio). */
  valorActualCP(asset: InvestmentResponse): number {
    const latest = this.latestValuacion(asset);
    if (!latest) {
      // LETRA capitalizable (zero-coupon): VN=1 por unidad → valor de rescate = nominales × 1
      if (asset.type === 'LETRA') return this.cuotapartesHeld(asset);
      return asset.principal;
    }
    return this.cuotapartesHeld(asset) * latest.pricePerUnit;
  }

  /** Valor actual de cualquier activo, despachando por tipo (idéntico a `saldoBase`). */
  currentValue(asset: InvestmentResponse): number {
    if (asset.type === 'FCI') return this.saldoFci(asset);
    if (asset.type === 'FCI_CUOTAPARTES' || asset.type === 'LETRA'
        || asset.type === 'BONO' || asset.type === 'ON') {
      return this.valorActualCP(asset);
    }
    return asset.principal; // PLAZO_FIJO
  }

  /** Ganancia no realizada desde la compra: valor actual − capital invertido. */
  unrealizedGain(asset: InvestmentResponse): number {
    return this.currentValue(asset) - asset.principal;
  }

  /** Un activo cuenta para los totales sólo si sigue ACTIVA (ausente = ACTIVA). */
  isActive(asset: InvestmentResponse): boolean {
    return (asset.status ?? 'ACTIVA') === 'ACTIVA';
  }

  /** Valor actual total de la cartera, separado por moneda (sólo activos ACTIVA). */
  totalsByCurrency(assets: InvestmentResponse[]): PortfolioByCurrency {
    return assets.filter(a => this.isActive(a)).reduce((acc, a) => {
      const v = this.currentValue(a);
      if (a.currency === 'ARS') acc.ars += v; else acc.usd += v;
      return acc;
    }, { ars: 0, usd: 0 });
  }

  /** Ganancia no realizada total, separada por moneda (sólo activos ACTIVA). */
  unrealizedGainByCurrency(assets: InvestmentResponse[]): PortfolioByCurrency {
    return assets.filter(a => this.isActive(a)).reduce((acc, a) => {
      const g = this.unrealizedGain(a);
      if (a.currency === 'ARS') acc.ars += g; else acc.usd += g;
      return acc;
    }, { ars: 0, usd: 0 });
  }

  /**
   * Variación del día del activo, en su moneda de origen.
   *  - Con ≥2 valuaciones: compara las dos más recientes (precio de mercado).
   *  - Con <2 valuaciones (FCI / PLAZO_FIJO / sin serie): devengado diario por TNA.
   */
  dailyChange(asset: InvestmentResponse): { amount: number; pct: number } {
    const vals = [...(asset.valuations ?? [])].sort((a, b) => b.valuationDate.localeCompare(a.valuationDate));
    if (vals.length >= 2) {
      const latest = vals[0].pricePerUnit;
      const prev = vals[1].pricePerUnit;
      const pct = prev !== 0 ? latest / prev - 1 : 0;
      const amount = this.cuotapartesHeld(asset) * (latest - prev);
      return { amount, pct };
    }
    // Sin serie de precios: devengado por TNA (1 día).
    const dailyRate = (asset.tna ?? 0) / 100 / 365;
    return { amount: this.currentValue(asset) * dailyRate, pct: dailyRate };
  }

  /** Suma de la variación diaria de la cartera, separada por moneda (sólo activos ACTIVA). */
  dailyChangeByCurrency(assets: InvestmentResponse[]): PortfolioByCurrency {
    return assets.filter(a => this.isActive(a)).reduce((acc, a) => {
      const { amount } = this.dailyChange(a);
      if (a.currency === 'ARS') acc.ars += amount; else acc.usd += amount;
      return acc;
    }, { ars: 0, usd: 0 });
  }

  /** Composición de cartera por tipo de instrumento, valuada a valor actual, separada por moneda. */
  compositionByType(assets: InvestmentResponse[]): PortfolioCompositionSlice[] {
    const map = new Map<InvestmentAssetType, PortfolioByCurrency>();
    for (const a of assets.filter(x => this.isActive(x))) {
      const v = this.currentValue(a);
      const cur = map.get(a.type) ?? { ars: 0, usd: 0 };
      if (a.currency === 'ARS') cur.ars += v; else cur.usd += v;
      map.set(a.type, cur);
    }
    return [...map.entries()].map(([type, { ars, usd }]) => ({
      type, label: ASSET_TYPE_LABELS[type], ars, usd,
    }));
  }
}
