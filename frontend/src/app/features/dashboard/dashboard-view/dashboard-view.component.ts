import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CurrencyService } from '../../../core/services/currency.service';

@Component({
  selector: 'app-dashboard-view',
  standalone: true,
  imports: [],
  templateUrl: './dashboard-view.component.html',
  styleUrl: './dashboard-view.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardViewComponent {
  readonly currencyService = inject(CurrencyService);

  readonly accounts = [
    { name: 'Galicia · Caja de ahorro', currency: 'ARS', ars: 4120500 },
    { name: 'Brubank · USD',            currency: 'USD', ars: 9820428 },
    { name: 'Mercado Pago',             currency: 'ARS', ars: 318900  },
    { name: 'Efectivo USD',             currency: 'USD', ars: 2517896 },
  ];

  readonly investments = [
    { name: 'Plazo fijo UVA',   tea: 91.4 },
    { name: 'FCI Money Market', tea: 88.2 },
    { name: 'Letra LECAP',      tea: 79.6 },
  ];

  readonly flows = [
    { description: 'Sueldo — Acme S.A.',       type: 'Ingreso',     ars:  1240000, date: '02 jun' },
    { description: 'Alquiler',                  type: 'Egreso',      ars:  -480000, date: '05 jun' },
    { description: 'FCI Mercado Fondo',         type: 'Rendimiento', ars:    91420, date: '06 jun' },
    { description: 'Tarjeta Visa · cuota 3/6',  type: 'Egreso',     ars:  -612300, date: '07 jun' },
  ];

  readonly totalAssets    = this.accounts.reduce((sum, a) => sum + a.ars, 0);
  readonly monthlyIncome  = 1331420;
  readonly monthlyExpenses = 1092300;

  fmt(ars: number): string {
    const converted = this.currencyService.convert(ars, 'ARS');
    if (converted === null) return '–';
    if (this.currencyService.selected() === 'USD') {
      return converted.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    return Math.round(converted).toLocaleString('es-AR');
  }
}
