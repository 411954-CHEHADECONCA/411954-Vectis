import { Injectable, signal, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import Big from 'big.js';
import { MacroService } from './macro.service';
import { ExchangeRateResponse } from '../models/macro.models';

@Injectable({ providedIn: 'root' })
export class CurrencyService {
  private readonly macroService = inject(MacroService);

  readonly selected = signal<'ARS' | 'USD'>('ARS');
  readonly symbol   = computed(() => this.selected() === 'ARS' ? '$' : 'US$');

  readonly oficialRate = toSignal<ExchangeRateResponse | null>(
    this.macroService.getLatestOficialRate().pipe(catchError(() => of(null))),
    { initialValue: null },
  );

  readonly currentRateARS = computed((): string | null => {
    const r = this.oficialRate();
    if (!r) return null;
    try {
      const b = new Big(r.sell);
      return b.gt(0) ? r.sell : null;
    } catch {
      return null;
    }
  });

  toggle(): void {
    this.selected.update(c => c === 'ARS' ? 'USD' : 'ARS');
  }

  /** Convierte con la tasa actual (saldos, tarjetas, dashboard).
   *  Retorna un numero de precision decimal (via big.js); solo para display. */
  convert(amount: number, fromCcy: 'ARS' | 'USD'): number | null {
    const sel = this.selected();
    if (sel === fromCcy) return amount;
    return this.#compute(String(amount), fromCcy, this.currentRateARS());
  }

  /** Convierte con tasa historica; cae a la actual si rateAtTime es null. */
  convertHistorical(amount: number, fromCcy: 'ARS' | 'USD', rateAtTime: string | null): number | null {
    const sel = this.selected();
    if (sel === fromCcy) return amount;
    const rate = (rateAtTime && this.#isValidRateString(rateAtTime))
      ? rateAtTime
      : this.currentRateARS();
    return this.#compute(String(amount), fromCcy, rate);
  }

  #isValidRateString(s: string): boolean {
    try { return new Big(s).gt(0); } catch { return false; }
  }

  #compute(amount: string, fromCcy: 'ARS' | 'USD', rate: string | null): number | null {
    if (rate === null) return null;
    try {
      const a = new Big(amount);
      const r = new Big(rate);
      return fromCcy === 'ARS' ? a.div(r).toNumber() : a.times(r).toNumber();
    } catch {
      return null;
    }
  }
}
