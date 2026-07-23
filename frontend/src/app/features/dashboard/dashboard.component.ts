import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { catchError, filter, map, of, startWith } from 'rxjs';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { CurrencyService } from '../../core/services/currency.service';
import { InvestmentService } from '../../core/services/investment.service';

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
export class DashboardComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly investmentService = inject(InvestmentService);
  private readonly destroyRef = inject(DestroyRef);
  readonly currencyService = inject(CurrencyService);

  readonly oficialRate = this.currencyService.oficialRate;

  readonly currentUser$ = this.authService.currentUser$;

  /**
   * Refresh on-demand (sin forzar) de los datos de mercado al ingresar a la app —
   * login fresco o reingreso con token válido. Fire-and-forget: no bloquea el
   * render del shell ni muestra error al usuario si falla.
   */
  ngOnInit(): void {
    this.investmentService.refreshMarketData(false)
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

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

