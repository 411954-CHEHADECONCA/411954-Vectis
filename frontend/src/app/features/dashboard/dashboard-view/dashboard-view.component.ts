import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of } from 'rxjs';
import { CurrencyService } from '../../../core/services/currency.service';
import { AccountService } from '../../../core/services/account.service';
import { CardProjectionService } from '../../../core/services/card-projection.service';
import { CashflowService } from '../../../core/services/cashflow.service';
import { InvestmentService } from '../../../core/services/investment.service';
import { PortfolioSummaryService } from '../../../core/services/portfolio-summary.service';
import { AccountResponse } from '../../../core/models/account.models';
import { CardOverview } from '../../../core/models/card-projection.models';
import { CashflowResponse } from '../../../core/models/cashflow.models';
import { ASSET_TYPE_LABELS, InvestmentResponse } from '../../../core/models/investment.models';

type Ccy = 'ARS' | 'USD';
type Tone = 'good' | 'warning' | 'bad';
type Horizonte = 3 | 6 | 12;

interface CarteraRow {
  id:            string;
  name:          string;
  tipo:          string;
  valorAnterior: number;   // valuación anterior, en la moneda seleccionada
  valor:         number;   // valuación actual, en la moneda seleccionada
  pesoPct:       number;
  varPct:        number;
  varAmount:     number;   // diferencia de valuación, en la moneda seleccionada
}

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
  private readonly accountService    = inject(AccountService);
  private readonly cardService       = inject(CardProjectionService);
  private readonly cashflowService   = inject(CashflowService);
  private readonly investmentService = inject(InvestmentService);
  private readonly portfolio         = inject(PortfolioSummaryService);

  // ── Estado crudo ────────────────────────────────────────────────────────────
  private readonly accounts    = signal<AccountResponse[]>([]);
  private readonly overview    = signal<CardOverview | null>(null);
  private readonly investments = signal<InvestmentResponse[]>([]);
  private readonly cashflow    = signal<CashflowResponse | null>(null);
  readonly loading   = signal(true);
  readonly loadError = signal(false);

  /** Horizonte del Índice de Ingreso Comprometido (toggle 3/6/12). */
  readonly horizonteMeses = signal<Horizonte>(6);
  readonly horizonteOpciones: Horizonte[] = [3, 6, 12];

  constructor() {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;

    forkJoin({
      accounts:    this.accountService.getAccounts().pipe(catchError(() => of([] as AccountResponse[]))),
      overview:    this.cardService.getOverview().pipe(catchError(() => of(null))),
      investments: this.investmentService.getInvestments().pipe(catchError(() => of([] as InvestmentResponse[]))),
      cashflow:    this.cashflowService.getCashflow(year, month).pipe(catchError(() => of(null))),
    })
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (r) => {
          this.accounts.set(r.accounts);
          this.overview.set(r.overview);
          this.investments.set(r.investments);
          this.cashflow.set(r.cashflow);
          this.loading.set(false);
        },
        error: () => {
          this.loadError.set(true);
          this.loading.set(false);
        },
      });
  }

  setHorizonte(n: Horizonte): void {
    this.horizonteMeses.set(n);
  }

  // ── Cuentas / liquidez ──────────────────────────────────────────────────────
  readonly accountRows = computed(() => this.accounts());
  readonly hasAccounts = computed(() => this.accounts().length > 0);

  private readonly liquidezByCcy = computed<Record<Ccy, number>>(() =>
    this.accounts().reduce((acc, a) => {
      const bal = a.computedBalance ?? a.balance;
      if (a.ccy === 'ARS') acc.ARS += bal; else acc.USD += bal;
      return acc;
    }, { ARS: 0, USD: 0 }),
  );

  readonly liquidez = computed(() => this.toSelected(this.liquidezByCcy().ARS, this.liquidezByCcy().USD));

  // ── Cartera / inversiones ───────────────────────────────────────────────────
  private readonly carteraByCcy = computed(() => this.portfolio.totalsByCurrency(this.investments()));
  readonly valorCartera = computed(() => this.toSelected(this.carteraByCcy().ars, this.carteraByCcy().usd));

  private readonly varDiariaByCcy = computed(() => this.portfolio.dailyChangeByCurrency(this.investments()));
  readonly variacionDiariaTotal = computed(() => this.toSelected(this.varDiariaByCcy().ars, this.varDiariaByCcy().usd));

  /** % de variación diaria total = monto del día / (valor de cartera − monto del día). */
  readonly variacionDiariaTotalPct = computed<number | null>(() => {
    const amount = this.variacionDiariaTotal();
    const base = this.valorCartera() - amount;
    return base > 0 ? (amount / base) * 100 : null;
  });

  readonly filasCartera = computed<CarteraRow[]>(() => {
    const activos = this.investments().filter(a => this.portfolio.isActive(a));
    const rows = activos.map(a => {
      const valor = this.convertFrom(this.portfolio.currentValue(a), a.currency);
      const change = this.portfolio.dailyChange(a);
      const varAmount = this.convertFrom(change.amount, a.currency);
      return {
        id: a.id,
        name: a.name,
        tipo: ASSET_TYPE_LABELS[a.type],
        valorAnterior: valor - varAmount,
        valor,
        pesoPct: 0,
        varPct: change.pct * 100,
        varAmount,
      };
    }).sort((x, y) => y.valor - x.valor);
    const total = rows.reduce((s, r) => s + r.valor, 0);
    return rows.map(r => ({ ...r, pesoPct: total > 0 ? (r.valor / total) * 100 : 0 }));
  });

  readonly hasCartera = computed(() => this.filasCartera().length > 0);

  // ── Tarjetas / deuda ────────────────────────────────────────────────────────
  private readonly deudaByCcy = computed<Record<Ccy, number>>(() =>
    (this.overview()?.cards ?? []).reduce((acc, c) => {
      if (c.ccy === 'ARS') acc.ARS += c.consumido; else acc.USD += c.consumido;
      return acc;
    }, { ARS: 0, USD: 0 }),
  );

  readonly deudaTarjetas = computed(() => this.toSelected(this.deudaByCcy().ARS, this.deudaByCcy().USD));

  readonly proximoVencimiento = computed(() => {
    const ov = this.overview();
    if (!ov || !ov.nextDueDate) return null;
    return { name: ov.nextDueCardName, date: ov.nextDueDate };
  });

  /** Monto del próximo vencimiento, convertido desde la moneda de su tarjeta. */
  private readonly proximoVencimientoMonto = computed<number | null>(() => {
    const ov = this.overview();
    if (!ov || !ov.vencimientos.length) return null;
    const next = ov.vencimientos[0];
    const card = ov.cards.find(c => c.id === next.cardId);
    const ccy: Ccy = card?.ccy ?? 'ARS';
    return this.convertFrom(next.amount, ccy);
  });

  // ── Patrimonio neto ─────────────────────────────────────────────────────────
  readonly patrimonioNeto = computed(() =>
    this.liquidez() + this.valorCartera() - this.deudaTarjetas());

  private readonly patrimonioDeltaAbs = computed(() =>
    this.convertArs(parseFloat(this.cashflow()?.preInvestmentBalance.operativeResult ?? '0')));

  readonly patrimonioDeltaPct = computed<number | null>(() => {
    const d = this.patrimonioDeltaAbs();
    const base = this.patrimonioNeto() - d;
    return base > 0 ? (d / base) * 100 : null;
  });

  // ── Resultado del mes (cashflow) ────────────────────────────────────────────
  readonly hasCashflow = computed(() => this.cashflow() !== null);
  readonly periodoLabel = computed(() => this.cashflow()?.periodLabel ?? '');

  readonly ingresosMes  = computed(() => this.convertArs(this.cf('income')));
  readonly egresosMes   = computed(() => this.convertArs(this.cf('expenses')));
  readonly resultadoMes = computed(() =>
    this.convertArs(parseFloat(this.cashflow()?.preInvestmentBalance.operativeResult ?? '0')));
  readonly tasaAhorroPct = computed(() =>
    parseFloat(this.cashflow()?.preInvestmentBalance.savingRatePct ?? '0'));

  readonly ingresosPctPpto = computed(() => this.pctPresupuesto('income'));
  readonly egresosPctPpto  = computed(() => this.pctPresupuesto('expenses'));

  private cf(section: 'income' | 'expenses'): number {
    return parseFloat(this.cashflow()?.[section].total ?? '0');
  }

  private pctPresupuesto(section: 'income' | 'expenses'): number | null {
    const cf = this.cashflow();
    if (!cf) return null;
    const budget = parseFloat(cf[section].totalBudgeted);
    if (!isFinite(budget) || budget === 0) return null;
    return (parseFloat(cf[section].total) / budget) * 100;
  }

  // ── Ratio de Cobertura Inmediata ──────────────────────────────────────────────
  readonly tieneVencimiento = computed(() => this.proximoVencimientoMonto() !== null);

  readonly ratioCobertura = computed<number | null>(() => {
    const venc = this.proximoVencimientoMonto();
    if (venc === null || venc === 0) return null;
    return (this.liquidez() / venc) * 100;
  });

  readonly ratioCoberturaTone = computed<Tone>(() => {
    const r = this.ratioCobertura();
    if (r === null) return 'good';
    return r >= 100 ? 'good' : 'bad';
  });

  // ── Índice de Ingreso Comprometido Futuro ─────────────────────────────────────
  private readonly comprometidoEnHorizonte = computed(() => {
    const n = this.horizonteMeses();
    return (this.overview()?.cuotasActivas ?? []).reduce((sum, plan) => {
      const remaining = Math.max(0, plan.totalInstallments - plan.currentNumber);
      const meses = Math.min(remaining, n);
      return sum + this.convertFrom(plan.monthlyAmount, plan.ccy) * meses;
    }, 0);
  });

  private readonly ingresoMes = computed(() =>
    this.convertArs(parseFloat(this.cashflow()?.income.total ?? '0')));

  readonly ingresoComprometidoPct = computed<number | null>(() => {
    const ingreso = this.ingresoMes();
    const n = this.horizonteMeses();
    if (ingreso <= 0) return null;
    return (this.comprometidoEnHorizonte() / (ingreso * n)) * 100;
  });

  readonly ingresoComprometidoTone = computed<Tone>(() => {
    const v = this.ingresoComprometidoPct();
    if (v === null) return 'good';
    if (v < 30) return 'good';
    if (v <= 60) return 'warning';
    return 'bad';
  });

  // ── Helpers de conversión ─────────────────────────────────────────────────────
  private toSelected(ars: number, usd: number): number {
    if (this.currencyService.selected() === 'ARS') {
      return ars + (this.currencyService.convert(usd, 'USD') ?? 0);
    }
    return (this.currencyService.convert(ars, 'ARS') ?? 0) + usd;
  }

  private convertFrom(amount: number, ccy: Ccy): number {
    return this.currencyService.convert(amount, ccy) ?? 0;
  }

  private convertArs(amountArs: number): number {
    return this.currencyService.convert(amountArs, 'ARS') ?? amountArs;
  }

  // ── Formato ─────────────────────────────────────────────────────────────────
  private formatNumber(v: number): string {
    return this.currencyService.selected() === 'USD'
      ? v.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : Math.round(v).toLocaleString('es-AR');
  }

  fmt(valueInSelected: number): string {
    return this.formatNumber(valueInSelected);
  }

  fmtNativo(amount: number, ccy: Ccy): string {
    return ccy === 'USD'
      ? amount.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : Math.round(amount).toLocaleString('es-AR');
  }

  symbolOf(ccy: Ccy): string {
    return ccy === 'USD' ? 'US$' : '$';
  }

  /** Porcentaje con signo y coma decimal, o null si no aplica. */
  fmtPct(v: number | null, decimals = 1): string | null {
    if (v === null || !isFinite(v)) return null;
    const abs = Math.abs(v).toLocaleString('es-AR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
    return `${v < 0 ? '−' : '+'}${abs}%`;
  }

  /** Porcentaje sin signo (para ratios y pesos). */
  fmtPctPlano(v: number | null, decimals = 0): string {
    if (v === null || !isFinite(v)) return '—';
    return `${v.toLocaleString('es-AR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}%`;
  }

  fmtFecha(iso: string | null): string {
    if (!iso) return '';
    const [, m, d] = iso.split('-');
    return `${d}/${m}`;
  }

  deltaUp(v: number | null): boolean {
    return (v ?? 0) >= 0;
  }

  deltaTone(v: number | null, goodWhenUp = true): 'good' | 'bad' {
    return this.deltaUp(v) === goodWhenUp ? 'good' : 'bad';
  }
}
