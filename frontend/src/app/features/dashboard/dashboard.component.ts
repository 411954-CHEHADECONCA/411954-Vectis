import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet, NavigationEnd } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { CurrencyService } from '../../core/services/currency.service';

const PAGE_TITLES: Record<string, string> = {
  '/dashboard':          'Resumen Ejecutivo',
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

  readonly currentUser$ = this.authService.currentUser$;

  readonly pageTitle = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      map(e => PAGE_TITLES[(e as NavigationEnd).urlAfterRedirects] ?? 'Vectis'),
      startWith(PAGE_TITLES[this.router.url] ?? 'Vectis'),
    ),
    { initialValue: PAGE_TITLES[this.router.url] ?? 'Vectis' },
  );

  logout(): void {
    this.authService.logout();
  }
}
