import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { filter, map, startWith, catchError, of } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { CurrencyService } from '../../core/services/currency.service';
import { MacroService } from '../../core/services/macro.service';
import { ExchangeRateResponse } from '../../core/models/macro.models';

const PAGE_TITLES: Record<string, string> = {
  '/dashboard':          'Resumen Ejecutivo',
  '/movimientos':        'Movimientos',
  '/cashflow':           'Cash Flow',
  '/tarjetas':           'Tarjetas',
  '/config':             'Configuración',
  '/config/categories':  'Categorías',
  '/settings/security':  'Seguridad',
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, AsyncPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly currencyService = inject(CurrencyService);
  private readonly macroService = inject(MacroService);

  readonly oficialRate = toSignal<ExchangeRateResponse | null>(
    this.macroService.getLatestOficialRate().pipe(catchError(() => of(null))),
    { initialValue: null },
  );

  readonly currentUser$ = this.authService.currentUser$;

  readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map(e => PAGE_TITLES[(e as NavigationEnd).urlAfterRedirects] ?? 'Vectis'),
      startWith(PAGE_TITLES[this.router.url] ?? 'Vectis'),
    ),
    { initialValue: PAGE_TITLES[this.router.url] ?? 'Vectis' },
  );

  formatArs(value: number | string): string {
    return '$ ' + new Intl.NumberFormat('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 0 }).format(Number(value));
  }

  logout(): void {
    this.authService.logout();
  }
}

