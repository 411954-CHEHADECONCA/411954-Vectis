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
import { EMPTY, Subject, catchError, switchMap } from 'rxjs';
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
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [`
    :host { display: block; }

    /* ── Ledger spine ──────────────────────────── */
    .ledger { position: relative; }
    .ledger-spine {
      position: absolute;
      left: 19px;
      top: 28px;
      bottom: 28px;
      width: 2px;
      background: var(--color-outline-variant);
    }
    .ledger-spine--dashed {
      background: repeating-linear-gradient(
        to bottom,
        var(--color-outline-variant) 0px,
        var(--color-outline-variant) 6px,
        transparent 6px,
        transparent 12px
      );
    }

    /* ── Step ──────────────────────────────────── */
    .step { display: flex; gap: 16px; padding: 20px 0; }
    .step:not(:last-child) { border-bottom: 1px solid var(--color-outline-variant); }

    .step-dot {
      position: relative;
      z-index: 1;
      flex-shrink: 0;
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 18px;
      font-weight: 700;
      line-height: 1;
    }
    .step-dot--neutral {
      background: var(--color-surface-container-high);
      border: 2px solid var(--color-outline-variant);
      color: var(--color-on-surface-variant);
    }
    .step-dot--income {
      background: color-mix(in srgb, var(--color-primary) 18%, transparent);
      border: 2px solid var(--color-primary);
      color: var(--color-primary);
    }
    .step-dot--expense {
      background: color-mix(in srgb, var(--color-error) 18%, transparent);
      border: 2px solid var(--color-error);
      color: var(--color-error);
    }
    .step-dot--balance {
      background: color-mix(in srgb, var(--color-primary) 12%, transparent);
      border: 2px solid var(--color-primary);
      color: var(--color-primary);
      font-size: 14px;
    }
    .step-dot--investment {
      background: color-mix(in srgb, var(--color-secondary) 18%, transparent);
      border: 2px solid var(--color-secondary);
      color: var(--color-secondary);
    }
    .step-dot--closing {
      background: var(--color-primary-container);
      border: 2px solid var(--color-primary);
      color: var(--color-on-primary-container);
    }

    /* ── Step body ─────────────────────────────── */
    .step-body { flex: 1; min-width: 0; }
    .step-label {
      font-size: var(--text-caption);
      font-weight: var(--weight-semibold);
      color: var(--color-text-muted);
      margin-bottom: 4px;
    }
    .step-total {
      font-size: var(--text-metric);
      font-weight: var(--weight-bold);
      font-variant-numeric: tabular-nums;
      letter-spacing: var(--tracking-tight);
      line-height: 1;
      margin-bottom: 12px;
    }
    .step-total--income  { color: var(--color-primary); }
    .step-total--expense { color: var(--color-error); }
    .step-total--neutral { color: var(--color-on-surface); }

    /* ── Sub-panel ─────────────────────────────── */
    .sub-panel {
      background: var(--color-surface-container-high);
      border: 1px solid var(--color-outline-variant);
      border-radius: var(--radius-md);
      padding: 16px;
      margin-bottom: 4px;
    }
    .sub-panel--tinted {
      background: color-mix(in srgb, var(--color-primary) 8%, var(--color-surface-container));
      border-color: color-mix(in srgb, var(--color-primary) 25%, transparent);
    }
    .sub-panel--dim {
      background: var(--color-surface-container);
      border-color: var(--color-outline-variant);
    }
    .sub-panel__metric {
      font-size: var(--text-metric-lg);
      font-weight: var(--weight-bold);
      font-variant-numeric: tabular-nums;
      letter-spacing: var(--tracking-tight);
      line-height: 1;
      color: var(--color-primary);
    }
    .sub-panel__metric--dim { color: var(--color-on-surface-variant); }
    .sub-panel__caption {
      font-size: var(--text-caption);
      color: var(--color-text-muted);
      margin-top: 6px;
    }

    /* ── Account grid ──────────────────────────── */
    .acc-grid { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; }
    .acc-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 12px;
      background: var(--color-surface-container-high);
      border-radius: var(--radius-sm);
    }
    .acc-name { flex: 1; font-size: var(--text-sm); color: var(--color-on-surface); }
    .acc-ccy-badge {
      font-size: var(--text-overline);
      font-weight: var(--weight-semibold);
      letter-spacing: var(--tracking-overline);
      text-transform: uppercase;
      padding: 2px 7px;
      border-radius: var(--radius-full);
    }
    .acc-ccy-badge--usd {
      background: color-mix(in srgb, var(--color-primary) 16%, transparent);
      color: var(--color-primary);
    }
    .acc-ccy-badge--ars {
      background: var(--color-surface-container-highest);
      color: var(--color-on-surface-variant);
    }
    .acc-balance {
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
      font-variant-numeric: tabular-nums;
      color: var(--color-on-surface);
      text-align: right;
    }

    /* ── Category rows ─────────────────────────── */
    .cat-rows { display: flex; flex-direction: column; gap: 0; margin-top: 4px; }
    .cat-row { display: flex; align-items: center; gap: 12px; padding: 10px 0; }
    .cat-row:not(:last-child) { border-bottom: 1px solid var(--color-outline-variant); }
    .cat-icon-chip {
      flex-shrink: 0;
      width: 34px;
      height: 34px;
      border-radius: var(--radius-sm);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
    }
    .cat-info { flex: 1; min-width: 0; }
    .cat-name-row {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      margin-bottom: 5px;
    }
    .cat-name {
      font-size: var(--text-sm);
      color: var(--color-on-surface);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      min-width: 0;
    }
    .cat-amount {
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
      font-variant-numeric: tabular-nums;
      color: var(--color-on-surface);
      white-space: nowrap;
      flex-shrink: 0;
      margin-left: 8px;
    }
    .cat-bar-row { display: flex; align-items: center; gap: 8px; }
    .cat-bar-track { flex: 1; height: 5px; background: var(--color-outline-variant); border-radius: 99px; overflow: hidden; }
    .cat-bar-fill { height: 100%; border-radius: 99px; transition: width 0.3s var(--ease-out); }
    .cat-pct {
      font-size: var(--text-overline);
      color: var(--color-text-muted);
      font-variant-numeric: tabular-nums;
      min-width: 52px;
      flex-shrink: 0;
      white-space: nowrap;
      text-align: right;
    }
    .cat-budget-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 4px;
    }
    .cat-budget-caption { font-size: var(--text-overline); color: var(--color-text-muted); }
    .variance-badge {
      font-size: var(--text-overline);
      font-weight: var(--weight-semibold);
      padding: 2px 6px;
      border-radius: var(--radius-full);
    }
    .variance-badge--over {
      background: color-mix(in srgb, var(--color-error) 14%, transparent);
      color: var(--color-error);
    }
    .variance-badge--under {
      background: color-mix(in srgb, var(--color-primary) 14%, transparent);
      color: var(--color-primary);
    }

    /* ── Budget execution bar ──────────────────── */
    .budget-exec { margin-bottom: 12px; }
    .budget-exec__label {
      display: flex;
      justify-content: space-between;
      font-size: var(--text-caption);
      color: var(--color-text-muted);
      margin-bottom: 6px;
    }
    .budget-exec__track {
      height: 6px;
      background: var(--color-outline-variant);
      border-radius: 99px;
      overflow: hidden;
    }
    .budget-exec__fill {
      height: 100%;
      border-radius: 99px;
      transition: width 0.4s var(--ease-out);
    }

    /* ── Month bar ─────────────────────────────── */
    .month-bar {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 14px 20px;
      background: var(--color-surface-container);
      border: 1px solid var(--color-outline-variant);
      border-radius: var(--radius-lg);
      margin-bottom: 4px;
    }
    .month-nav-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: var(--radius-sm);
      border: 1px solid var(--color-outline-variant);
      background: var(--color-surface-container-high);
      color: var(--color-on-surface);
      cursor: pointer;
      transition: background var(--dur-base) var(--ease-standard);
      flex-shrink: 0;
    }
    .month-nav-btn:hover:not(:disabled) { background: var(--color-surface-container-highest); }
    .month-nav-btn:disabled { opacity: 0.35; cursor: not-allowed; }
    .month-center { flex: 1; display: flex; align-items: center; gap: 10px; }
    .month-name {
      font-size: var(--text-h3);
      font-weight: var(--weight-semibold);
      color: var(--color-on-surface);
    }
    .status-badge {
      font-size: var(--text-overline);
      font-weight: var(--weight-semibold);
      padding: 3px 8px;
      border-radius: var(--radius-full);
    }
    .status-badge--cerrado {
      background: var(--color-surface-container-highest);
      color: var(--color-on-surface-variant);
    }
    .status-badge--curso {
      background: color-mix(in srgb, var(--color-primary) 16%, transparent);
      color: var(--color-primary);
    }
    .status-badge--proyectado {
      background: color-mix(in srgb, var(--color-warning, #e8c37a) 16%, transparent);
      color: var(--color-warning, #e8c37a);
    }
    .month-right {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 2px;
    }
    .month-saving {
      font-size: var(--text-caption);
      color: var(--color-text-muted);
    }
    .month-closing {
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
      font-variant-numeric: tabular-nums;
      color: var(--color-on-surface);
    }

    /* ── Loading skeleton ──────────────────────── */
    .skeleton-panel {
      background: var(--color-surface-container);
      border: 1px solid var(--color-outline-variant);
      border-radius: var(--radius-lg);
      padding: 24px;
      animation: pulse 1.4s ease-in-out infinite;
    }
    .skeleton-line {
      background: var(--color-surface-container-high);
      border-radius: var(--radius-sm);
      margin-bottom: 12px;
    }
    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50%       { opacity: 0.5; }
    }

    /* ── Error state ───────────────────────────── */
    .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 48px 24px;
      color: var(--color-error);
      text-align: center;
    }
    .error-state p { font-size: var(--text-sm); color: var(--color-on-surface-variant); }

    /* ── Page layout ───────────────────────────── */
    .page { display: flex; flex-direction: column; gap: 20px; max-width: 900px; }
    .page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
    .page-head__text h1 {
      font-size: var(--text-h1);
      font-weight: var(--weight-bold);
      letter-spacing: var(--tracking-tight);
      color: var(--color-on-surface);
      margin: 0 0 6px;
    }
    .page-head__lead { font-size: var(--text-sm); color: var(--color-on-surface-variant); margin: 0; }
    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 9px 18px;
      background: var(--color-primary);
      color: var(--color-on-primary);
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
      border-radius: var(--radius);
      border: none;
      cursor: pointer;
      white-space: nowrap;
      transition: opacity var(--dur-base) var(--ease-standard);
    }
    .btn-primary:disabled { opacity: 0.45; cursor: not-allowed; }
    .btn-primary:not(:disabled):hover { opacity: 0.88; }
    .btn-retry {
      padding: 8px 16px;
      background: var(--color-surface-container-high);
      color: var(--color-on-surface);
      font-size: var(--text-sm);
      border: 1px solid var(--color-outline-variant);
      border-radius: var(--radius);
      cursor: pointer;
    }
    .panel {
      background: var(--color-surface-container);
      border: 1px solid var(--color-outline-variant);
      border-radius: var(--radius-lg);
      padding: 24px;
    }
    .projection-banner {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      background: color-mix(in srgb, var(--color-warning, #e8c37a) 10%, var(--color-surface-container));
      border: 1px solid color-mix(in srgb, var(--color-warning, #e8c37a) 30%, transparent);
      border-radius: var(--radius-md);
      font-size: var(--text-sm);
      color: var(--color-warning, #e8c37a);
    }
    .pre-balance-details {
      display: flex;
      gap: 16px;
      margin-top: 8px;
      flex-wrap: wrap;
    }
    .pre-balance-detail {
      font-size: var(--text-caption);
      color: var(--color-text-muted);
    }
    .pre-balance-detail strong {
      font-variant-numeric: tabular-nums;
      color: var(--color-on-surface-variant);
    }
  `],
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
  readonly data    = signal<CashflowResponse | null>(null);
  readonly loading = signal(false);
  readonly error   = signal<string | null>(null);

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

  // ── Setup ─────────────────────────────────────────────────────────────────
  constructor() {
    this.loadTrigger$.pipe(
      switchMap(() => {
        this.loading.set(true);
        this.error.set(null);
        return this.cashflowService.getCashflow(this.year(), this.month()).pipe(
          catchError(() => {
            this.error.set('No se pudo cargar el cash flow. Intentá de nuevo.');
            this.loading.set(false);
            return EMPTY;
          }),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(res => {
      this.data.set(res);
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

  barWidth(pct: string): string {
    return `${Math.min(parseFloat(pct), 100)}%`;
  }

  varianceLabel(pctOfBudget: string): string {
    return parseFloat(pctOfBudget) > 100
      ? `▲ ${fmtPct(pctOfBudget)}%`
      : `▼ ${fmtPct(pctOfBudget)}%`;
  }

  isOverBudget(pctOfBudget: string): boolean {
    return parseFloat(pctOfBudget) > 100;
  }

  trackByCategoryId(_i: number, row: CashflowCategoryRow): string | null { return row.categoryId; }
  trackByAccountId(_i: number, acc: CashflowAccountBalance): string      { return acc.accountId; }
}
