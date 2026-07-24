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
import { finalize } from 'rxjs/operators';
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
import { CurrencyService } from '../../core/services/currency.service';
import {
  CashflowAccountBalance,
  CashflowCategoryRow,
  CashflowResponse,
  MoneyByCcy,
} from '../../core/models/cashflow.models';

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Formato AR: punto de miles, coma decimal, con signo opcional. */
function fmtARS(value: number, sign?: '+' | '-'): string {
  const abs = Math.abs(value);
  const formatted = abs.toLocaleString('es-AR', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
  const prefix = sign ? sign : '';
  return `${prefix}$ ${formatted}`;
}

function fmtUSD(value: number): string {
  return `US$ ${Math.abs(value).toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function fmtBalance(balance: string, ccy: string): string {
  const n = parseFloat(balance);
  const abs = Math.abs(n);
  const sign = n < 0 ? '- ' : '';
  if (ccy === 'USD') {
    return `${sign}US$ ${abs.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
  return `${sign}$ ${abs.toLocaleString('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
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
  private readonly cashflowService  = inject(CashflowService);
  private readonly currencyService  = inject(CurrencyService);
  private readonly destroyRef       = inject(DestroyRef);
  private readonly router           = inject(Router);

  readonly currencySymbol = this.currencyService.symbol;

  // Subject que dispara cargas; switchMap cancela el request anterior si llega uno nuevo.
  private readonly loadTrigger$ = new Subject<void>();

  // ── Signals ───────────────────────────────────────────────────────────────
  readonly year    = signal(new Date().getFullYear());
  readonly month   = signal(new Date().getMonth() + 1);
  readonly data     = signal<CashflowResponse | null>(null);
  readonly prevData = signal<CashflowResponse | null>(null);
  // Piso de navegación hacia atrás (mes más antiguo con movimientos, o el mes anterior).
  // Absoluto: lo fija el backend en cada respuesta. Null hasta la primera carga.
  readonly earliestNavigable = signal<{ year: number; month: number } | null>(null);
  readonly loading            = signal(false);
  readonly error              = signal<string | null>(null);
  readonly periodActionLoading = signal(false);

  // ── Computed ──────────────────────────────────────────────────────────────

  private readonly monthsDiffFromNow = computed(() => {
    const now = new Date();
    return (this.year() - now.getFullYear()) * 12 + (this.month() - (now.getMonth() + 1));
  });

  // Sólo se puede retroceder mientras el mes visto sea posterior al piso (dirigido por datos).
  readonly canGoBack = computed(() => {
    const floor = this.earliestNavigable();
    if (!floor) return false;
    const diff = (this.year() - floor.year) * 12 + (this.month() - floor.month);
    return diff > 0;
  });
  readonly canGoForward = computed(() => this.monthsDiffFromNow() < 3);

  readonly isProjection      = computed(() => this.data()?.isProjection ?? false);
  readonly needsConfirmation = computed(() => this.data()?.needsConfirmation ?? false);

  readonly leadText = computed(() => {
    const d = this.data();
    if (!d) return '';
    switch (d.status) {
      case 'cerrado':    return `Mes cerrado. Así se movió tu disponible en ${d.periodLabel}.`;
      case 'curso':      return 'Mes en curso. Ingresos contabilizados y gastos al día de hoy.';
      case 'abierto':    return `Mes abierto. Podés registrar movimientos en ${d.periodLabel}.`;
      case 'proyectado': return `Proyección estimada para ${d.periodLabel}. Podés registrar movimientos anticipados.`;
    }
  });

  readonly hasBudgetsValue = computed(() => this.moneyIsPositive(this.data()?.expenses.totalBudgeted));
  readonly budgetExecPctValue = computed(() => {
    const d = this.data();
    if (!d) return 0;
    const budgeted = this.toSelected(d.expenses.totalBudgeted) ?? this.rawSum(d.expenses.totalBudgeted);
    if (budgeted === 0) return 0;
    const total = this.toSelected(d.expenses.total) ?? this.rawSum(d.expenses.total);
    return (total / budgeted) * 100;
  });
  readonly budgetExecBarWidth = computed(() => `${Math.min(this.budgetExecPctValue(), 100)}%`);
  readonly budgetExecIsOver   = computed(() => this.budgetExecPctValue() > 100);

  readonly hasBudgetsIncome = computed(() => this.moneyIsPositive(this.data()?.income.totalBudgeted));
  readonly budgetExecIncomePctValue = computed(() => {
    const d = this.data();
    if (!d) return 0;
    const budgeted = this.toSelected(d.income.totalBudgeted) ?? this.rawSum(d.income.totalBudgeted);
    if (budgeted === 0) return 0;
    const total = this.toSelected(d.income.total) ?? this.rawSum(d.income.total);
    return (total / budgeted) * 100;
  });
  readonly budgetExecIncomeBarWidth = computed(() => `${Math.min(this.budgetExecIncomePctValue(), 100)}%`);
  readonly budgetExecIncomeIsOver   = computed(() => this.budgetExecIncomePctValue() >= 100);
  readonly isOperativeResultPositive = computed(() => {
    const d = this.data();
    if (!d) return true;
    return this.signedTotal(d.preInvestmentBalance.operativeResult) >= 0;
  });

  readonly isOpeningNegative = computed(() => {
    const d = this.data();
    return d ? this.signedTotal(d.openingBalance.total) < 0 : false;
  });
  readonly isClosingNegative = computed(() => {
    const d = this.data();
    return d ? this.signedTotal(d.closingBalance.total) < 0 : false;
  });
  readonly isPreInvNegative = computed(() => {
    const d = this.data();
    return d ? this.signedTotal(d.preInvestmentBalance.balance) < 0 : false;
  });
  readonly isBalanceAfterIncomeNeg = computed(() => {
    const m = this.balanceAfterIncomeMoney();
    return m ? this.signedTotal(m) < 0 : false;
  });
  readonly closingBalanceLabel = computed(() =>
    this.isClosingNegative() ? 'Necesidad de liquidez' : 'Saldo final disponible'
  );

  /** Diferencia de saldo de cierre vs. el mes anterior, ya convertida a la moneda seleccionada
   *  (cada período usa su propia cotización histórica). Null si no hay cotización disponible. */
  readonly closingDelta = computed(() => {
    const curr = this.data();
    const prev = this.prevData();
    if (!curr || !prev) return null;
    const currVal = this.toSelected(curr.closingBalance.total, curr.oficialRateAtPeriod);
    const prevVal = this.toSelected(prev.closingBalance.total, prev.oficialRateAtPeriod);
    if (currVal === null || prevVal === null) return null;
    return currVal - prevVal;
  });
  readonly closingDeltaPositive = computed(() => (this.closingDelta() ?? 0) >= 0);
  readonly prevMonthShort       = computed(() => this.prevData()?.monthShort ?? '');

  /** Saldo inicial + ingresos, sumado bucket a bucket (misma moneda con misma moneda). */
  readonly balanceAfterIncomeMoney = computed<MoneyByCcy | null>(() => {
    const d = this.data();
    if (!d) return null;
    return {
      ars: d.openingBalance.total.ars + d.income.total.ars,
      usd: d.openingBalance.total.usd + d.income.total.usd,
    };
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
      this.earliestNavigable.set({
        year: current.earliestNavigableYear,
        month: current.earliestNavigableMonth,
      });
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
    if (this.isProjection()) {
      this.router.navigate(['/movimientos'], {
        queryParams: { year: this.year(), month: this.month() },
      });
    } else {
      this.router.navigate(['/movimientos']);
    }
  }

  // ── Conversión bimonetaria ────────────────────────────────────────────────
  // El backend ya no devuelve totales colapsados a un único número: cada agregado viene
  // desglosado por moneda (MoneyByCcy). Convertir y sumar ambos buckets a la moneda
  // seleccionada es responsabilidad del consumidor (mismo patrón que `toSelected` en
  // dashboard-view.component.ts, vía CurrencyService.convertHistorical).

  private periodRate(): string | null { return this.data()?.oficialRateAtPeriod ?? null; }

  /** Convierte y suma ambos buckets a la moneda seleccionada. `rate` por defecto es la cotización
   *  del período activo; se puede pasar explícitamente la de otro período (ej. mes anterior).
   *  Null si hace falta convertir y no hay cotización disponible. */
  private toSelected(money: MoneyByCcy, rate: string | null = this.periodRate()): number | null {
    const ars = this.currencyService.convertHistorical(money.ars, 'ARS', rate);
    const usd = this.currencyService.convertHistorical(money.usd, 'USD', rate);
    if (ars === null || usd === null) return null;
    return ars + usd;
  }

  /** Suma cruda de ambos buckets, sin convertir — sólo como último recurso cuando no hay
   *  cotización (mismo criterio de gracia que usaba el código anterior a este cambio). */
  private rawSum(money: MoneyByCcy): number {
    return money.ars + money.usd;
  }

  /** Signo del total, tolerante a falta de cotización (para checks de negativo/positivo). */
  private signedTotal(money: MoneyByCcy): number {
    return this.toSelected(money) ?? this.rawSum(money);
  }

  private moneyIsPositive(money: MoneyByCcy | undefined): boolean {
    return !!money && this.rawSum(money) > 0;
  }

  // ── Formatting helpers (exposed to template) ──────────────────────────────

  /** Monto bimonetario formateado con símbolo, en la moneda seleccionada. */
  fmtMoney(money: MoneyByCcy): string {
    const total = this.toSelected(money);
    if (total === null) {
      // Sin cotización disponible: no se puede convertir, se muestra el bucket ARS tal cual.
      return fmtARS(money.ars);
    }
    return this.currencyService.selected() === 'USD' ? fmtUSD(total) : fmtARS(total);
  }

  /** Sólo el número formateado, sin símbolo (para stat-cards donde el símbolo va aparte). */
  fmtMoneyAmt(money: MoneyByCcy): string {
    const total = this.toSelected(money);
    const v = total !== null ? Math.abs(total) : Math.abs(money.ars);
    return v.toLocaleString('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  }

  fmtBalance(balance: string, ccy: string): string {
    const n = parseFloat(balance);
    const converted = this.currencyService.convertHistorical(n, ccy as 'ARS' | 'USD', this.periodRate());
    if (converted === null) return fmtBalance(balance, ccy);
    return fmtBalance(String(converted), this.currencyService.selected());
  }

  fmtPct(value: string): string  { return fmtPct(value); }
  parseFloat(value: string): number { return parseFloat(value); }

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
    return `${abs}%`;
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

  invPct(invAmount: MoneyByCcy): string {
    const d = this.data();
    if (!d) return '0';
    const total = this.toSelected(d.investments.total) ?? this.rawSum(d.investments.total);
    if (total === 0) return '0';
    const amt = this.toSelected(invAmount) ?? this.rawSum(invAmount);
    return ((amt / total) * 100).toFixed(2);
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

  // ── Period lifecycle actions ───────────────────────────────────────────────

  confirmProjection(): void {
    this.periodActionLoading.set(true);
    this.cashflowService.confirmProjection(this.year(), this.month())
      .pipe(finalize(() => this.periodActionLoading.set(false)))
      .subscribe({
        next: (updated) => this.data.set(updated),
        error: () => {},
      });
  }

  closePeriod(): void {
    const d = this.data();
    if (!d) return;
    const confirmed = confirm(
      `¿Cerrar el mes de ${d.periodLabel}? No podrás registrar nuevos movimientos en él hasta reabrirlo.`
    );
    if (!confirmed) return;
    this.periodActionLoading.set(true);
    this.cashflowService.closePeriod(d.year, d.month)
      .pipe(finalize(() => this.periodActionLoading.set(false)))
      .subscribe({
        next: (updated) => this.data.set(updated),
        error: () => { /* loading se limpia en finalize */ },
      });
  }

  openPeriod(): void {
    const d = this.data();
    if (!d) return;
    const msg = d.recurringMaterialized
      ? 'Este mes ya fue abierto anteriormente. Los movimientos recurrentes no se volverán a cargar. ¿Reabrir de todas formas?'
      : `Al abrir ${d.periodLabel} se cargarán tus movimientos recurrentes automáticamente. ¿Continuar?`;
    const confirmed = confirm(msg);
    if (!confirmed) return;
    this.periodActionLoading.set(true);
    this.cashflowService.openPeriod(d.year, d.month)
      .pipe(finalize(() => this.periodActionLoading.set(false)))
      .subscribe({
        next: (updated) => this.data.set(updated),
        error: () => { },
      });
  }
}
