import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, forkJoin, of, switchMap } from 'rxjs';
import {
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
import { CashflowService } from '../../core/services/cashflow.service';
import {
  CashflowAccountBalance,
  CashflowCategoryRow,
  CashflowResponse,
} from '../../core/models/cashflow.models';

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Formato AR: punto de miles, coma decimal, con signo opcional. */
function fmtARS(value: string | number, sign?: '+' | '-'): string {
  const n = typeof value === 'string' ? parseFloat(value) : value;
  const abs = Math.abs(n);
  const formatted = abs.toLocaleString('es-AR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
  const prefix = sign ? sign : '';
  return `${prefix}$ ${formatted}`;
}

function fmtUSD(value: string | number): string {
  const n = typeof value === 'string' ? parseFloat(value) : value;
  return `US$ ${Math.abs(n).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function fmtBalance(balance: string, ccy: string): string {
  return ccy === 'USD' ? fmtUSD(balance) : fmtARS(balance);
}

function fmtPct(value: string | number): string {
  const n = typeof value === 'string' ? parseFloat(value) : value;
  return n.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

// ── Component ─────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-cashflow',
  standalone: true,
  imports: [
    NgTemplateOutlet,
    LucideUtensils, LucideCar, LucideZap, LucideRepeat, LucideMusic,
    LucideHeart, LucideBook, LucideShirt, LucideHome, LucideBriefcase,
    LucideMonitor, LucideTrendingUp, LucideArrowRightLeft, LucideDumbbell, LucideCircle,
  ],
  templateUrl: './cashflow.component.html',
  styleUrl:    './cashflow.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CashflowComponent implements OnInit {
  private readonly cashflowService = inject(CashflowService);
  private readonly destroyRef      = inject(DestroyRef);
  private readonly router          = inject(Router);

  // Subject que dispara cargas; switchMap cancela el request anterior si llega uno nuevo.
  private readonly loadTrigger$ = new Subject<void>();

  // ── Signals ───────────────────────────────────────────────────────────────
  readonly year    = signal(new Date().getFullYear());
  readonly month   = signal(new Date().getMonth() + 1);
  readonly data     = signal<CashflowResponse | null>(null);
  readonly prevData = signal<CashflowResponse | null>(null);
  readonly loading  = signal(false);
  readonly error    = signal<string | null>(null);

  // ── Computed ──────────────────────────────────────────────────────────────

  private readonly monthsDiffFromNow = computed(() => {
    const now = new Date();
    return (this.year() - now.getFullYear()) * 12 + (this.month() - (now.getMonth() + 1));
  });

  readonly canGoBack    = computed(() => this.monthsDiffFromNow() > -12);
  readonly canGoForward = computed(() => this.monthsDiffFromNow() < 3);

  readonly isProjection = computed(() => this.data()?.isProjection ?? false);

  readonly leadText = computed(() => {
    const d = this.data();
    if (!d) return '';
    switch (d.status) {
      case 'cerrado':    return `Mes cerrado. Así se movió tu disponible en ${d.periodLabel}.`;
      case 'curso':      return 'Mes en curso. Ingresos contabilizados y gastos al día de hoy.';
      case 'proyectado': return 'Proyección estimada con el promedio de tus meses cerrados. Solo lectura.';
    }
  });

  readonly hasBudgetsValue = computed(() =>
    parseFloat(this.data()?.expenses.totalBudgeted ?? '0') > 0
  );
  readonly budgetExecPctValue = computed(() => {
    const d = this.data();
    if (!d) return 0;
    const budgeted = parseFloat(d.expenses.totalBudgeted);
    if (budgeted === 0) return 0;
    return (parseFloat(d.expenses.total) / budgeted) * 100;
  });
  readonly budgetExecBarWidth = computed(() => `${Math.min(this.budgetExecPctValue(), 100)}%`);
  readonly budgetExecIsOver   = computed(() => this.budgetExecPctValue() > 100);

  readonly hasBudgetsIncome = computed(() =>
    parseFloat(this.data()?.income.totalBudgeted ?? '0') > 0
  );
  readonly budgetExecIncomePctValue = computed(() => {
    const d = this.data();
    if (!d) return 0;
    const budgeted = parseFloat(d.income.totalBudgeted);
    if (budgeted === 0) return 0;
    return (parseFloat(d.income.total) / budgeted) * 100;
  });
  readonly budgetExecIncomeBarWidth = computed(() => `${Math.min(this.budgetExecIncomePctValue(), 100)}%`);
  readonly budgetExecIncomeIsOver   = computed(() => this.budgetExecIncomePctValue() >= 100);
  readonly isOperativeResultPositive = computed(() =>
    parseFloat(this.data()?.preInvestmentBalance.operativeResult ?? '0') >= 0
  );

  readonly closingDelta = computed(() => {
    const curr = this.data();
    const prev = this.prevData();
    if (!curr || !prev) return null;
    return parseFloat(curr.closingBalance.total) - parseFloat(prev.closingBalance.total);
  });
  readonly closingDeltaPositive = computed(() => (this.closingDelta() ?? 0) >= 0);
  readonly prevMonthShort       = computed(() => this.prevData()?.monthShort ?? '');
  readonly balanceAfterIncome = computed(() => {
    const d = this.data();
    if (!d) return '0.0000';
    return (parseFloat(d.openingBalance.total) + parseFloat(d.income.total)).toFixed(4);
  });

  // ── Setup ─────────────────────────────────────────────────────────────────
  constructor() {
    this.loadTrigger$.pipe(
      switchMap(() => {
        this.loading.set(true);
        this.error.set(null);
        const prevY = this.month() === 1 ? this.year() - 1 : this.year();
        const prevM = this.month() === 1 ? 12 : this.month() - 1;
        return forkJoin({
          current: this.cashflowService.getCashflow(this.year(), this.month()),
          prev:    this.cashflowService.getCashflow(prevY, prevM).pipe(catchError(() => of(null))),
        }).pipe(
          catchError(() => {
            this.error.set('No se pudo cargar el cash flow. Intentá de nuevo.');
            this.loading.set(false);
            return EMPTY;
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(({ current, prev }) => {
      this.data.set(current);
      this.prevData.set(prev);
      this.loading.set(false);
    });
  }

  ngOnInit(): void {
    this.loadData();
  }

  // ── Navigation ────────────────────────────────────────────────────────────
  prevMonth(): void {
    if (!this.canGoBack()) return;
    let y = this.year();
    let m = this.month() - 1;
    if (m < 1) { m = 12; y--; }
    this.year.set(y);
    this.month.set(m);
    this.loadData();
  }

  nextMonth(): void {
    if (!this.canGoForward()) return;
    let y = this.year();
    let m = this.month() + 1;
    if (m > 12) { m = 1; y++; }
    this.year.set(y);
    this.month.set(m);
    this.loadData();
  }

  loadData(): void {
    this.loadTrigger$.next();
  }

  openCreateMovimiento(): void {
    this.router.navigate(['/movimientos']);
  }

  // ── Formatting helpers (exposed to template) ──────────────────────────────
  fmtBalance(balance: string, ccy: string): string { return fmtBalance(balance, ccy); }
  fmtARS(value: string): string                    { return fmtARS(value); }
  fmtPct(value: string): string                    { return fmtPct(value); }

  /** Solo el número formateado, sin símbolo "$" (para stat-cards donde el $ va en span aparte). */
  fmtAmt(value: string): string {
    const n = Math.abs(parseFloat(value));
    return n.toLocaleString('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  barWidth(pct: string): string {
    return `${Math.min(parseFloat(pct), 100)}%`;
  }

  fmtDeltaAmt(): string {
    const d = this.closingDelta();
    if (d === null) return '';
    return Math.abs(d).toLocaleString('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
  }

  varianceLabel(pctOfBudget: string): string {
    const exec = parseFloat(pctOfBudget);
    const diff = exec - 100;
    const abs = Math.abs(diff).toLocaleString('es-AR', { maximumFractionDigits: 0 });
    const sign = diff > 0.5 ? '+' : diff < -0.5 ? '−' : '±';
    return `${sign}${abs}%`;
  }

  catBarFill(cat: CashflowCategoryRow): string {
    if (cat.pctOfBudget !== null && parseFloat(cat.pctOfBudget) > 100) {
      return 'var(--color-error)';
    }
    return cat.color;
  }

  private budgetBarMax(real: string, budget: string | null): number {
    if (!budget) return parseFloat(real);
    return Math.max(parseFloat(real), parseFloat(budget)) * 1.12;
  }

  budgetBarFillPct(real: string, budget: string | null): string {
    const max = this.budgetBarMax(real, budget);
    return `${max > 0 ? Math.min((parseFloat(real) / max) * 100, 100) : 0}%`;
  }

  budgetBarMarkerPct(budget: string | null, real: string): string {
    if (!budget) return '0%';
    const max = this.budgetBarMax(real, budget);
    return max > 0 ? `${Math.min((parseFloat(budget) / max) * 100, 100)}%` : '0%';
  }

  isOverBudget(pctOfBudget: string): boolean {
    return parseFloat(pctOfBudget) > 100;
  }

  trackByCategoryId(_i: number, row: CashflowCategoryRow): string | null { return row.categoryId; }
  trackByAccountId(_i: number, acc: CashflowAccountBalance): string      { return acc.accountId; }

  invPct(invAmount: string): string {
    const d = this.data();
    if (!d) return '0';
    const total = parseFloat(d.investments.total);
    if (total === 0) return '0';
    return ((parseFloat(invAmount) / total) * 100).toFixed(2);
  }

  budgetMarkerPct(pctOfTotal: string | null, pctOfBudget: string | null): string {
    if (!pctOfTotal || !pctOfBudget) return '0%';
    const pt = parseFloat(pctOfTotal);
    const pb = parseFloat(pctOfBudget);
    if (pb === 0) return '0%';
    return `${Math.min((pt * 100) / pb, 100)}%`;
  }

  catBudgetMarkerPctOfTotal(cat: CashflowCategoryRow): string {
    if (cat.budgeted === null || cat.pctOfBudget === null) return '0%';
    const pb = parseFloat(cat.pctOfBudget);
    if (pb === 0) return '0%';
    // budgeted/total*100 = pctOfTotal * 100 / pctOfBudget
    const markerPct = Math.min((parseFloat(cat.pctOfTotal) * 100) / pb, 100);
    return `${markerPct}%`;
  }
}
