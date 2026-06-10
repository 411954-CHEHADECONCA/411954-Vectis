import { Injectable, signal, computed } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CurrencyService {
  readonly selected = signal<'ARS' | 'USD'>('ARS');
  readonly symbol = computed(() => (this.selected() === 'ARS' ? '$' : 'US$'));

  toggle(): void {
    this.selected.update(c => (c === 'ARS' ? 'USD' : 'ARS'));
  }
}
