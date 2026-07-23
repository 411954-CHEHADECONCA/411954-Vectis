import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ReactiveFormsModule, FormGroup, FormArray, FormControl, Validators } from '@angular/forms';
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
  LucideX,
  LucideTrash2,
} from '@lucide/angular';
import { CardProjectionService } from '../../core/services/card-projection.service';
import { CreditCardService } from '../../core/services/credit-card.service';
import { AccountService } from '../../core/services/account.service';
import { CategoryService } from '../../core/services/category.service';
import { CurrencyService } from '../../core/services/currency.service';
import { MovementService } from '../../core/services/movement.service';
import { CardDue, CardMatrix, CardOverview } from '../../core/models/card-projection.models';
import { AccountResponse } from '../../core/models/account.models';
import { CategoryResponse } from '../../core/models/category.models';
import { CardPaymentHistory, CardPaymentRequest, ExtraChargeItem } from '../../core/models/card-payment.models';

type CardTab = 'cuotas' | 'venc' | 'matriz' | 'pagos';

interface PayModalState {
  cardId: string;
  cardName: string;
  periodYear: number;
  periodMonth: number;
  minDate: string;
}

@Component({
  selector: 'app-tarjetas',
  standalone: true,
  templateUrl: './tarjetas.component.html',
  styleUrl: './tarjetas.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    LucidePlus, LucideClock, LucideCreditCard, LucideCalendar,
    LucideChevronDown, LucideChevronRight, LucideRefreshCw,
    LucideUtensils, LucideCar, LucideZap, LucideRepeat, LucideMusic,
    LucideHeart, LucideBook, LucideShirt, LucideHome, LucideBriefcase,
    LucideMonitor, LucideTrendingUp, LucideArrowRightLeft, LucideDumbbell, LucideCircle,
    LucideX, LucideTrash2,
  ],
})
export class TarjetasComponent implements OnInit {
  private readonly cardService        = inject(CardProjectionService);
  private readonly creditCardService  = inject(CreditCardService);
  private readonly accountService     = inject(AccountService);
  private readonly categoryService    = inject(CategoryService);
  private readonly movementService    = inject(MovementService);
  private readonly router             = inject(Router);
  readonly currencyService            = inject(CurrencyService);

  overview = signal<CardOverview | null>(null);
  matrix   = signal<CardMatrix | null>(null);
  loading  = signal(false);
  error    = signal<string | null>(null);

  activeTab        = signal<CardTab>('cuotas');
  months           = signal(6);
  expanded         = signal<Set<string>>(new Set());
  payments         = signal<CardPaymentHistory[]>([]);
  expandedPayments = signal<Set<string>>(new Set());
  paymentsLoading  = signal(false);

  // ── Payment modal ────────────────────────────────────────────────────────
  payModal      = signal<PayModalState | null>(null);
  paySubmitting = signal(false);
  payError      = signal<string | null>(null);
  accounts      = signal<AccountResponse[]>([]);
  categories    = signal<CategoryResponse[]>([]);

  // ── Delete payment modal ─────────────────────────────────────────────────
  paymentToDelete          = signal<CardPaymentHistory | null>(null);
  deletePaymentSubmitting  = signal(false);

  payForm = new FormGroup({
    accountId:         new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    paidDate:          new FormControl(this.todayISO(), { nonNullable: true, validators: [Validators.required] }),
    paidAmount:        new FormControl<number>(0, { nonNullable: true, validators: [Validators.required, Validators.min(0.01)] }),
    paymentCategoryId: new FormControl<string | null>(null),
    extraCharges:      new FormArray<FormGroup>([]),
  });

  get extraChargesArray(): FormArray<FormGroup> {
    return this.payForm.get('extraCharges') as FormArray<FormGroup>;
  }

  readonly monthOptions = [6, 12, 18];

  /** Índice del primer vencimiento no vencido (daysUntil >= 0); -1 si todos son pasados. */
  firstUpcomingIdx = computed(() =>
    (this.overview()?.vencimientos ?? []).findIndex(v => this.daysUntil(v.dueDate) >= 0)
  );

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
    this.payments.set([]); // fuerza recarga lazy cuando se vuelva a la pestaña
  }

  setTab(tab: CardTab): void {
    this.activeTab.set(tab);
    if (tab === 'pagos' && this.payments().length === 0) {
      this.loadPayments();
    }
  }

  private loadPayments(): void {
    this.paymentsLoading.set(true);
    this.creditCardService.getCardPayments().subscribe({
      next: p => { this.payments.set(p); this.paymentsLoading.set(false); },
      error: () => { this.paymentsLoading.set(false); },
    });
  }

  togglePayment(id: string): void {
    this.expandedPayments.update(set => {
      const next = new Set(set);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  isPaymentExpanded(id: string): boolean {
    return this.expandedPayments().has(id);
  }

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
    const converted = this.currencyService.convert(amount, ccy);
    if (converted === null) {
      if (ccy === 'USD') return `US$ ${amount.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
      return `$ ${Math.round(amount).toLocaleString('es-AR')}`;
    }
    const sel = this.currencyService.selected();
    if (sel === 'USD') {
      return `US$ ${converted.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    return `$ ${Math.round(converted).toLocaleString('es-AR')}`;
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

  fmtDaysUntil(iso: string): string {
    const d = this.daysUntil(iso);
    if (d < 0)  return `hace ${-d} día${-d === 1 ? '' : 's'}`;
    if (d === 0) return 'hoy';
    return `en ${d} día${d === 1 ? '' : 's'}`;
  }

  /** Días hasta una fecha ISO (para "en N días"). */
  daysUntil(iso: string): number {
    const [y, m, d] = iso.split('-').map(Number);
    const target = new Date(y, m - 1, d);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((target.getTime() - today.getTime()) / 86400000);
  }

  // ── Payment modal ────────────────────────────────────────────────────────

  openPayModal(due: CardDue): void {
    const [y, m] = due.dueDate.split('-').map(Number);
    const minDate = due.dueDate;
    const today   = this.todayISO();
    this.payModal.set({ cardId: due.cardId, cardName: due.cardName, periodYear: y, periodMonth: m, minDate });
    while (this.extraChargesArray.length) { this.extraChargesArray.removeAt(0); }
    this.payForm.patchValue({
      paidDate: today >= minDate ? today : minDate,
      paidAmount: due.amount,
      accountId: '',
      paymentCategoryId: null,
    });
    this.payError.set(null);
    if (!this.accounts().length) {
      this.accountService.getAccounts().subscribe({ next: accs => this.accounts.set(accs) });
    }
    if (!this.categories().length) {
      this.categoryService.getCategories().subscribe({ next: cats => this.categories.set(cats) });
    }
  }

  closePayModal(): void {
    this.payModal.set(null);
    this.payError.set(null);
    while (this.extraChargesArray.length) { this.extraChargesArray.removeAt(0); }
    this.payForm.reset();
  }

  addExtraCharge(): void {
    this.extraChargesArray.push(new FormGroup({
      description: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      amount:      new FormControl<number>(0, { nonNullable: true, validators: [Validators.required, Validators.min(0.01)] }),
      categoryId:  new FormControl<string | null>(null, [Validators.required]),
    }));
  }

  removeExtraCharge(i: number): void {
    this.extraChargesArray.removeAt(i);
  }

  submitPayment(): void {
    const modal = this.payModal();
    if (!modal || this.payForm.invalid) return;

    const extras: ExtraChargeItem[] = this.extraChargesArray.controls.map(ctrl => ({
      description: ctrl.get('description')!.value as string,
      amount:      ctrl.get('amount')!.value as number,
      categoryId:  (ctrl.get('categoryId')!.value as string | null) ?? null,
    }));

    const req: CardPaymentRequest = {
      accountId:         this.payForm.controls.accountId.value,
      paidDate:          this.payForm.controls.paidDate.value,
      paidAmount:        this.payForm.controls.paidAmount.value,
      periodYear:        modal.periodYear,
      periodMonth:       modal.periodMonth,
      paymentCategoryId: this.payForm.controls.paymentCategoryId.value,
      extraCharges:      extras,
    };

    this.paySubmitting.set(true);
    this.payError.set(null);

    this.creditCardService.payCard(modal.cardId, req).subscribe({
      next: () => {
        this.paySubmitting.set(false);
        this.closePayModal();
        this.reload();
      },
      error: (err) => {
        this.paySubmitting.set(false);
        this.payError.set(err?.error?.message ?? 'No se pudo registrar el pago. Intentá de nuevo.');
      },
    });
  }

  fmtPeriod(year: number, month: number): string {
    const mon = new Date(year, month - 1, 1).toLocaleDateString('es-AR', { month: 'long' });
    return `${mon.charAt(0).toUpperCase()}${mon.slice(1)} ${year}`;
  }

  totalPayment(p: CardPaymentHistory): number {
    const cuotasSum = p.cuotas.reduce((s, c) => s + c.amount, 0);
    const extrasSum = p.extraCharges.reduce((s, e) => s + e.amount, 0);
    return cuotasSum + extrasSum;
  }

  // ── Delete payment handlers ───────────────────────────────────────────────

  openDeletePayment(p: CardPaymentHistory): void {
    this.paymentToDelete.set(p);
  }

  closeDeletePayment(): void {
    this.paymentToDelete.set(null);
  }

  confirmDeletePayment(): void {
    const p = this.paymentToDelete();
    if (!p) return;
    this.deletePaymentSubmitting.set(true);
    this.movementService.delete(p.paymentTransactionId).subscribe({
      next: () => {
        this.deletePaymentSubmitting.set(false);
        this.closeDeletePayment();
        this.reload();
        this.loadPayments();
      },
      error: () => {
        this.deletePaymentSubmitting.set(false);
      },
    });
  }

  private todayISO(): string {
    return new Date().toISOString().split('T')[0];
  }
}
