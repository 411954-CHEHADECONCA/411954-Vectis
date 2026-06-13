import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router } from '@angular/router';
import {
  LucidePlus,
  LucideClock,
  LucideCreditCard,
  LucideCalendar,
  LucideChevronDown,
  LucideChevronRight,
  LucideRefreshCw,
  LucideUtensils,
  LucideCar,
  LucideZap,
  LucideRepeat,
  LucideMusic,
  LucideHeart,
  LucideBook,
  LucideShirt,
  LucideHome,
  LucideBriefcase,
  LucideMonitor,
  LucideTrendingUp,
  LucideArrowRightLeft,
  LucideDumbbell,
  LucideCircle,
} from '@lucide/angular';
import { CardProjectionService } from '../../core/services/card-projection.service';
import { CurrencyService } from '../../core/services/currency.service';
import { CardMatrix, CardOverview } from '../../core/models/card-projection.models';

type CardTab = 'cuotas' | 'venc' | 'matriz';

@Component({
  selector: 'app-tarjetas',
  standalone: true,
  templateUrl: './tarjetas.component.html',
  styleUrl: './tarjetas.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet,
    LucidePlus, LucideClock, LucideCreditCard, LucideCalendar,
    LucideChevronDown, LucideChevronRight, LucideRefreshCw,
    LucideUtensils, LucideCar, LucideZap, LucideRepeat, LucideMusic,
    LucideHeart, LucideBook, LucideShirt, LucideHome, LucideBriefcase,
    LucideMonitor, LucideTrendingUp, LucideArrowRightLeft, LucideDumbbell, LucideCircle,
  ],
})
export class TarjetasComponent implements OnInit {
  private readonly cardService = inject(CardProjectionService);
  private readonly router      = inject(Router);
  readonly currencyService     = inject(CurrencyService);

  overview = signal<CardOverview | null>(null);
  matrix   = signal<CardMatrix | null>(null);
  loading  = signal(false);
  error    = signal<string | null>(null);

  activeTab = signal<CardTab>('cuotas');
  months    = signal(6);
  expanded  = signal<Set<string>>(new Set());

  readonly monthOptions = [6, 12, 18];

  /** Suma de todos los vencimientos que caen en el mismo mes que el próximo vencimiento. */
  nextMonthTotal = computed(() => {
    const ov = this.overview();
    if (!ov?.nextDueDate) return 0;
    const targetMonth = ov.nextDueDate.substring(0, 7);
    return ov.vencimientos
      .filter(v => v.dueDate.substring(0, 7) === targetMonth)
      .reduce((sum, v) => sum + v.amount, 0);
  });

  ngOnInit(): void {
    this.loadOverview();
    this.loadMatrix();
  }

  private loadOverview(): void {
    this.loading.set(true);
    this.error.set(null);
    this.cardService.getOverview().subscribe({
      next: ov => {
        this.overview.set(ov);
        this.loading.set(false);
      },
      error: () => { this.error.set('No se pudieron cargar las tarjetas'); this.loading.set(false); },
    });
  }

  private loadMatrix(): void {
    this.cardService.getMatrix(this.months()).subscribe({
      next: mx => {
        this.matrix.set(mx);
        // Expandir todas las tarjetas por defecto.
        this.expanded.set(new Set(mx.cards.map(c => c.cardId)));
      },
      error: () => {},
    });
  }

  reload(): void {
    this.loadOverview();
    this.loadMatrix();
  }

  setTab(tab: CardTab): void { this.activeTab.set(tab); }

  goToAddCard(): void {
    this.router.navigate(['/config'], { queryParams: { tab: 'tarjetas', modal: 'create-card' } });
  }

  setMonths(n: number): void {
    if (this.months() === n) return;
    this.months.set(n);
    this.loadMatrix();
  }

  toggleCard(cardId: string): void {
    this.expanded.update(set => {
      const next = new Set(set);
      next.has(cardId) ? next.delete(cardId) : next.add(cardId);
      return next;
    });
  }

  isExpanded(cardId: string): boolean {
    return this.expanded().has(cardId);
  }

  // ── Formatting helpers ────────────────────────────────────────────────────
  fmtAmount(amount: number, ccy: 'ARS' | 'USD' = 'ARS'): string {
    if (ccy === 'USD') {
      return `US$ ${amount.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    return `$ ${Math.round(amount).toLocaleString('es-AR')}`;
  }

  /** Celda de la matriz: monto, o guion si está vacía (null o 0; los montos reales son > 0). */
  fmtCell(value: number | null): string {
    return value == null || value === 0 ? '—' : this.fmtAmount(value);
  }

  fmtDay(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d)
      .toLocaleDateString('es-AR', { day: '2-digit', month: 'short' })
      .replace('.', '');
  }

  /** "2026-06" → "jun 26". */
  fmtMonth(key: string): string {
    const [y, m] = key.split('-').map(Number);
    const mon = new Date(y, m - 1, 1).toLocaleDateString('es-AR', { month: 'short' }).replace('.', '');
    return `${mon} ${String(y).slice(2)}`;
  }

  pct(used: number, limit: number): number {
    if (!limit) return 0;
    return Math.min(100, (used / limit) * 100);
  }

  tint(color: string | null): string {
    return color ? `color-mix(in srgb, ${color} 15%, transparent)` : 'var(--color-surface-container-high)';
  }

  cardGradient(accent: string): string {
    return `radial-gradient(circle at 100% 0%, color-mix(in srgb, ${accent} 22%, transparent) 0%, var(--color-surface-container-high) 55%)`;
  }

  /** Días hasta una fecha ISO (para "en N días"). */
  daysUntil(iso: string): number {
    const [y, m, d] = iso.split('-').map(Number);
    const target = new Date(y, m - 1, d);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((target.getTime() - today.getTime()) / 86400000);
  }
}
