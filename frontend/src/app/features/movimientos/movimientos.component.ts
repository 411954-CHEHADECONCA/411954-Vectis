import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import {
  LucidePlus,
  LucidePencil,
  LucideTrash2,
  LucideX,
  LucideSearch,
  LucideChevronLeft,
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
import { MovementService } from '../../core/services/movement.service';
import { CategoryService } from '../../core/services/category.service';
import { AccountService } from '../../core/services/account.service';
import { CreditCardService } from '../../core/services/credit-card.service';
import { CardProjectionService } from '../../core/services/card-projection.service';
import { CurrencyService } from '../../core/services/currency.service';
import { TransferService } from '../../core/services/transfer.service';
import {
  GroupMovementUpdateRequest,
  MovementResponse,
  MovementRequest,
  MovementSummary,
  MovementType,
  MovementCcy,
  MovementFilters,
} from '../../core/models/movement.models';
import { TransferRequest } from '../../core/models/transfer.models';
import { PageResponse } from '../../core/models/pagination.models';
import { CategoryResponse } from '../../core/models/category.models';
import { AccountResponse } from '../../core/models/account.models';
import { CardResponse } from '../../core/models/card.models';
import { CardFace } from '../../core/models/card-projection.models';

type TypeFilter = 'Todos' | 'INCOME' | 'EXPENSE';

interface MovementModalState {
  mode: 'create' | 'edit' | 'delete';
  id?: string;
  label?: string;
  isInstallment?: boolean;
  installmentGroupId?: string;
  transferGroupId?: string;
}

@Component({
  selector: 'app-movimientos',
  standalone: true,
  templateUrl: './movimientos.component.html',
  styleUrl: './movimientos.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    LucidePlus, LucidePencil, LucideTrash2, LucideX,
    LucideSearch, LucideChevronLeft, LucideChevronRight,
    LucideRefreshCw,
    LucideUtensils, LucideCar, LucideZap, LucideRepeat, LucideMusic,
    LucideHeart, LucideBook, LucideShirt, LucideHome, LucideBriefcase,
    LucideMonitor, LucideTrendingUp, LucideArrowRightLeft, LucideDumbbell, LucideCircle,
  ],
})
export class MovimientosComponent implements OnInit {
  private readonly movementService       = inject(MovementService);
  private readonly categoryService       = inject(CategoryService);
  private readonly accountService        = inject(AccountService);
  private readonly creditCardService     = inject(CreditCardService);
  private readonly cardProjectionService = inject(CardProjectionService);
  private readonly transferService       = inject(TransferService);
  private readonly route                 = inject(ActivatedRoute);
  readonly currencyService               = inject(CurrencyService);

  /** Fecha a pre-llenar en el modal de creación. Se sobreescribe si vienen query params desde cashflow proyectado. */
  private defaultCreateDate = signal(todayISO());

  // ── Data ────────────────────────────────────────────────────────────────
  page       = signal<PageResponse<MovementResponse> | null>(null);
  summary    = signal<MovementSummary | null>(null);
  loading    = signal(false);
  error      = signal<string | null>(null);

  categories = signal<CategoryResponse[]>([]);
  accounts   = signal<AccountResponse[]>([]);
  cards      = signal<CardResponse[]>([]);
  cardFaces  = signal<CardFace[]>([]);

  // ── Filters ─────────────────────────────────────────────────────────────
  currentMonthYear = signal(currentYearMonth());

  readonly fFrom = computed(() => {
    const { year, month } = this.currentMonthYear();
    return toISO(new Date(year, month, 1));
  });

  readonly fTo = computed(() => {
    const { year, month } = this.currentMonthYear();
    return toISO(new Date(year, month + 1, 0));
  });

  readonly monthLabel = computed(() => {
    const { year, month } = this.currentMonthYear();
    const mo = new Date(year, month, 1).toLocaleDateString('es-AR', { month: 'long' });
    return `${mo} de ${year}`;
  });

  fType       = signal<TypeFilter>('Todos');
  fCategoryId = signal('');
  fQ          = signal('');
  pageIndex   = signal(0);
  readonly pageSize = 20;

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  // ── Modal ───────────────────────────────────────────────────────────────
  modal      = signal<MovementModalState | null>(null);
  submitting = signal(false);
  formError  = signal<string | null>(null);

  movementForm = new FormGroup({
    description:     new FormControl('',  { nonNullable: true, validators: [Validators.maxLength(200)] }),
    type:           new FormControl<MovementType>('EXPENSE', { nonNullable: true }),
    // Sin Validators.required: las transferencias (type === 'TRANSFER') no usan categoría y dejan
    // este control en null a propósito — la obligatoriedad para INCOME/EXPENSE la da la preselección
    // automática del default (ver defaultCategoryFor) más la ausencia de la opción "Sin categoría" en
    // el select, no un validador (que bloquearía el submit de transferencias).
    categoryId:     new FormControl<string | null>(null),
    paymentSource:  new FormControl('',   { nonNullable: true, validators: [MovimientosComponent.paymentSourceValid] }),
    ccy:            new FormControl<MovementCcy>('ARS', { nonNullable: true }),
    amount:         new FormControl<number>(0, { nonNullable: true, validators: [Validators.required, Validators.min(0.01)] }),
    transactionDate: new FormControl(todayISO(), { nonNullable: true, validators: [Validators.required] }),
    installments:   new FormControl<number>(1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(360)] }),
    destAccountId:  new FormControl<string | null>(null),
    destAmount:     new FormControl<number | null>(null),
  });

  private readonly formValues = toSignal(this.movementForm.valueChanges, {
    initialValue: this.movementForm.getRawValue(),
  });

  formType   = computed<MovementType>(() => this.formValues().type ?? 'EXPENSE');
  formSource = computed(() => this.formValues().paymentSource ?? '');
  /** Las cuotas solo aplican a egresos pagados con tarjeta. */
  showInstallments = computed(() => this.formType() === 'EXPENSE' && this.formSource().startsWith('card:'));

  isTransfer = computed(() => this.formType() === 'TRANSFER');

  /** Cuentas destino disponibles (excluye la cuenta origen para evitar transferirse a sí mismo). */
  destAccounts = computed(() => {
    const src = this.formSource();
    const srcId = src.startsWith('acc:') ? src.slice(4) : null;
    return this.accounts().filter(a => a.id !== srcId);
  });

  /** Muestra el campo "Monto destino" solo cuando origen y destino tienen distinta moneda. */
  showDestAmount = computed(() => {
    if (!this.isTransfer()) return false;
    const src = this.formSource();
    const srcId = src.startsWith('acc:') ? src.slice(4) : null;
    const destId = this.formValues().destAccountId;
    if (!srcId || !destId) return false;
    const srcAcc  = this.accounts().find(a => a.id === srcId);
    const destAcc = this.accounts().find(a => a.id === destId);
    return !!srcAcc && !!destAcc && srcAcc.ccy !== destAcc.ccy;
  });

  /** Face de la tarjeta seleccionada en el formulario (con consumido y límite). */
  selectedCardFace = computed(() => {
    const src = this.formSource();
    if (!src.startsWith('card:')) return null;
    return this.cardFaces().find(f => f.id === src.slice(5)) ?? null;
  });

  /** Crédito disponible de la tarjeta seleccionada (null si no hay tarjeta). */
  availableCredit = computed(() => {
    const face = this.selectedCardFace();
    return face ? face.creditLimit - face.consumido : null;
  });

  static paymentSourceValid(ctrl: AbstractControl): ValidationErrors | null {
    const v = ctrl.value as string;
    return v.startsWith('acc:') || v.startsWith('card:') ? null : { paymentSourceRequired: true };
  }

  readonly selectedAccount = computed(() => {
    const src = this.formSource();
    if (!src.startsWith('acc:')) return null;
    return this.accounts().find(a => a.id === src.slice(4)) ?? null;
  });

  readonly accountBalanceWarning = computed(() => {
    if (this.formType() !== 'EXPENSE') return null;
    const acc = this.selectedAccount();
    if (!acc || acc.computedBalance === null) return null;
    const amount = Number(this.formValues().amount) || 0;
    if (amount <= 0) return null;
    const formCcy = (this.formValues().ccy ?? 'ARS') as MovementCcy;
    if (formCcy !== acc.ccy) return null; // conversión bimoneda pendiente
    return amount > acc.computedBalance
      ? `Saldo insuficiente (disponible: ${this.fmtAmount(acc.computedBalance, formCcy)}). El balance quedará negativo.`
      : null;
  });

  /** Mensaje de error si el monto supera el saldo disponible (null si todo OK). */
  creditLimitError = computed(() => {
    if (this.formType() !== 'EXPENSE') return null;
    const avail = this.availableCredit();
    if (avail === null) return null;
    const amount = Number(this.formValues().amount) || 0;
    if (amount <= 0) return null;
    const cardCcy = this.selectedCardFace()?.ccy ?? 'ARS';
    const formCcy = (this.formValues().ccy ?? 'ARS') as MovementCcy;
    if (formCcy !== cardCcy) return null; // conversión bimoneda pendiente (AF411954-12)
    if (amount > avail) {
      return `El monto supera el saldo disponible (${this.fmtAmount(avail, formCcy)} disponibles)`;
    }
    return null;
  });

  readonly paymentSourceError = computed(() => {
    const v = this.formValues().paymentSource ?? '';
    return v.startsWith('acc:') || v.startsWith('card:') ? null : 'Seleccioná un medio de pago';
  });

  /** Categorías válidas para el tipo seleccionado (incluye BOTH). */
  formCategories = computed(() => {
    const t = this.formType();
    return this.categories().filter(c => c.type === t || c.type === 'BOTH');
  });

  /** Categoría "Otros ingresos"/"Otros egresos" que se preselecciona cuando no se elige ninguna. */
  private defaultCategoryFor(type: 'INCOME' | 'EXPENSE'): string | null {
    return this.categories().find(c => c.isUncategorizedDefault && c.type === type)?.id ?? null;
  }

  /** Categorías disponibles en el filtro, acordes al filtro de tipo. */
  filterCategories = computed(() => {
    const t = this.fType();
    if (t === 'Todos') return this.categories();
    return this.categories().filter(c => c.type === t || c.type === 'BOTH');
  });

  // ── Lifecycle ─────────────────────────────────────────────────────────────
  ngOnInit(): void {
    const qp = this.route.snapshot.queryParamMap;
    const y = parseInt(qp.get('year') ?? '', 10);
    const m = parseInt(qp.get('month') ?? '', 10);
    if (!isNaN(y) && !isNaN(m) && m >= 1 && m <= 12) {
      this.defaultCreateDate.set(toISO(new Date(y, m - 1, 1)));
    }

    this.loadCategories();
    this.loadAccounts();
    this.loadCards();
    this.loadCardFaces();
    this.reload();
  }

  private loadCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: cats => this.categories.set(cats),
      error: () => {},
    });
  }

  private loadAccounts(): void {
    this.accountService.getAccounts().subscribe({
      next: list => this.accounts.set(list),
      error: () => {},
    });
  }

  private loadCards(): void {
    this.creditCardService.getCards().subscribe({
      next: list => this.cards.set(list),
      error: () => {},
    });
  }

  private loadCardFaces(): void {
    this.cardProjectionService.getOverview().subscribe({
      next: ov => this.cardFaces.set(ov.cards),
      error: () => {},
    });
  }

  // ── Load / filters ────────────────────────────────────────────────────────
  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    const typeFilter = this.fType();
    const filters: MovementFilters = {
      from: this.fFrom(),
      to:   this.fTo(),
      type: typeFilter === 'Todos' ? undefined : typeFilter,
      categoryId: this.fCategoryId() || undefined,
      q: this.fQ() || undefined,
      page: this.pageIndex(),
      size: this.pageSize,
    };
    this.movementService.search(filters).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => { this.error.set('No se pudieron cargar los movimientos'); this.loading.set(false); },
    });
    this.movementService.summary(filters).subscribe({
      next: s => this.summary.set(s),
      error: () => {},
    });
  }

  private reloadFromFirstPage(): void {
    this.pageIndex.set(0);
    this.reload();
  }

  prevMonth(): void {
    this.currentMonthYear.update(({ year, month }) => {
      const d = new Date(year, month - 1, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });
    this.reloadFromFirstPage();
  }

  nextMonth(): void {
    this.currentMonthYear.update(({ year, month }) => {
      const d = new Date(year, month + 1, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });
    this.reloadFromFirstPage();
  }

  onTypeFilter(t: TypeFilter): void {
    this.fType.set(t);
    this.fCategoryId.set(''); // la categoría puede ya no aplicar al nuevo tipo
    this.reloadFromFirstPage();
  }

  onCategoryFilter(id: string): void { this.fCategoryId.set(id); this.reloadFromFirstPage(); }

  onSearchInput(value: string): void {
    this.fQ.set(value);
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.reloadFromFirstPage(), 300);
  }

  clearFilters(): void {
    this.currentMonthYear.set(currentYearMonth());
    this.fType.set('Todos');
    this.fCategoryId.set('');
    this.fQ.set('');
    this.reloadFromFirstPage();
  }

  // ── Pagination ──────────────────────────────────────────────────────────
  prevPage(): void {
    if (this.pageIndex() > 0) { this.pageIndex.update(i => i - 1); this.reload(); }
  }

  nextPage(): void {
    if (this.page()?.hasNext) { this.pageIndex.update(i => i + 1); this.reload(); }
  }

  // ── Modal: create / edit ──────────────────────────────────────────────────
  openCreate(): void {
    this.movementForm.reset({
      description: '', type: 'EXPENSE', categoryId: this.defaultCategoryFor('EXPENSE'), paymentSource: '',
      ccy: 'ARS', amount: 0, transactionDate: this.defaultCreateDate(), installments: 1,
      destAccountId: null, destAmount: null,
    });
    this.formError.set(null);
    this.modal.set({ mode: 'create' });
  }

  openEdit(m: MovementResponse): void {
    if (m.installment) {
      // Cuota: solo edita descripción base y categoría del grupo completo.
      const baseDesc = m.description.replace(/ — cuota \d+\/\d+$/, '');
      const installmentSource = m.accountId ? `acc:${m.accountId}`
                              : m.cardId    ? `card:${m.cardId}`
                              : '';
      this.movementForm.reset({
        description: baseDesc,
        type: m.type,
        categoryId: m.categoryId ?? this.defaultCategoryFor(m.type as 'INCOME' | 'EXPENSE'),
        paymentSource: installmentSource,
        ccy: m.ccy,
        amount: m.amount,
        transactionDate: m.transactionDate,
        installments: 1,
      });
      this.formError.set(null);
      this.modal.set({ mode: 'edit', id: m.id, isInstallment: true, installmentGroupId: m.installmentGroupId ?? undefined });
      return;
    }
    const paymentSource = m.accountId ? `acc:${m.accountId}`
                        : m.cardId    ? `card:${m.cardId}`
                        : '';
    this.movementForm.reset({
      description: m.description,
      type: m.type,
      categoryId: m.categoryId ?? this.defaultCategoryFor(m.type as 'INCOME' | 'EXPENSE'),
      paymentSource,
      ccy: m.ccy,
      amount: m.amount,
      transactionDate: m.transactionDate,
      installments: 1,
    });
    this.formError.set(null);
    this.modal.set({ mode: 'edit', id: m.id });
  }

  openDelete(m: MovementResponse): void {
    this.modal.set({
      mode: 'delete', id: m.id, label: m.description, isInstallment: m.installment,
      transferGroupId: m.transferGroupId ?? undefined,
    });
  }

  closeModal(): void {
    this.modal.set(null);
    this.submitting.set(false);
    this.formError.set(null);
  }

  /** Al cambiar tipo o medio de pago, limpia tarjeta/cuotas/categoría incompatibles. */
  onTypeOrSourceChange(): void {
    const type = this.movementForm.controls.type.value;
    const source = this.movementForm.controls.paymentSource.value;

    if (type === 'INCOME' && source.startsWith('card:')) {
      this.movementForm.patchValue({ paymentSource: '' });
    }
    if (!(type === 'EXPENSE' && source.startsWith('card:'))) {
      this.movementForm.patchValue({ installments: 1 });
    }
    if (type !== 'TRANSFER') {
      this.movementForm.patchValue({ destAccountId: null, destAmount: null });
    }
    const catId = this.movementForm.controls.categoryId.value;
    if (catId) {
      const cat = this.categories().find(c => c.id === catId);
      if (cat && cat.type !== type && cat.type !== 'BOTH') {
        // La categoría ya no aplica al nuevo tipo: reasignar el default en vez de dejarla en null
        // (INCOME/EXPENSE siempre deben quedar con una categoría). Las transferencias no usan categoría.
        this.movementForm.patchValue({
          categoryId: type === 'TRANSFER' ? null : this.defaultCategoryFor(type as 'INCOME' | 'EXPENSE'),
        });
      }
    }
    this.formError.set(null);
  }

  submit(): void {
    if (this.movementForm.invalid || this.submitting()) return;
    if (this.creditLimitError()) return;
    this.submitting.set(true);
    this.formError.set(null);
    const v = this.movementForm.getRawValue();
    const ps = v.paymentSource;
    const m = this.modal();
    if (!m) return;

    // Transferencia entre cuentas propias
    if (v.type === 'TRANSFER') {
      const srcId = ps.startsWith('acc:') ? ps.slice(4) : null;
      if (!srcId || !v.destAccountId) {
        this.formError.set('Seleccioná las cuentas de origen y destino');
        this.submitting.set(false);
        return;
      }
      const transferReq: TransferRequest = {
        sourceAccountId: srcId,
        destAccountId:   v.destAccountId,
        sourceAmount:    v.amount,
        destAmount:      this.showDestAmount() ? (v.destAmount ?? undefined) : undefined,
        transactionDate: v.transactionDate,
        description:     v.description || undefined,
      };
      this.transferService.create(transferReq).subscribe({
        next: () => { this.submitting.set(false); this.closeModal(); this.reload(); },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.formError.set(err.error?.message ?? 'Ocurrió un error al crear la transferencia');
        },
      });
      return;
    }

    const installments = this.showInstallments() ? v.installments : 1;
    const req: MovementRequest = {
      description: v.description,
      amount: v.amount,
      ccy: v.ccy,
      type: v.type,
      categoryId: v.categoryId || null,
      accountId: ps.startsWith('acc:')  ? ps.slice(4) : null,
      cardId:    ps.startsWith('card:') ? ps.slice(5) : null,
      transactionDate: v.transactionDate,
      installments,
    };

    // Edición de plan de cuotas: solo descripción base + categoría
    if (m.isInstallment && m.installmentGroupId) {
      const groupReq: GroupMovementUpdateRequest = {
        description: v.description,
        categoryId: v.categoryId || null,
      };
      this.movementService.updateGroup(m.installmentGroupId, groupReq).subscribe({
        next: () => { this.submitting.set(false); this.closeModal(); this.reload(); },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.formError.set(err.error?.message ?? 'Ocurrió un error al guardar');
        },
      });
      return;
    }

    const op$ = m.id
      ? this.movementService.update(m.id, req).pipe(map(() => void 0))
      : this.movementService.create(req).pipe(map(() => void 0));

    op$.subscribe({
      next: () => { this.submitting.set(false); this.closeModal(); this.reload(); },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(err.error?.message ?? 'Ocurrió un error al guardar');
      },
    });
  }

  confirmDelete(): void {
    const m = this.modal();
    if (!m?.id || this.submitting()) return;
    this.submitting.set(true);

    // Elimina ambas legs de una transferencia de forma atómica
    const op$ = m.transferGroupId
      ? this.transferService.delete(m.transferGroupId)
      : this.movementService.delete(m.id);

    op$.subscribe({
      next: () => { this.submitting.set(false); this.closeModal(); this.reload(); },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(err.error?.message ?? 'No se pudo eliminar el movimiento');
        this.closeModal();
      },
    });
  }

  // ── Preview (modal) ───────────────────────────────────────────────────────
  previewInstallmentAmount = computed(() => {
    const v = this.formValues();
    const amount = Number(v.amount) || 0;
    const n = this.showInstallments() ? (v.installments ?? 1) : 1;
    return n > 1 ? amount / n : amount;
  });

  /** Vista previa del movimiento que se está cargando (espejo del diseño). */
  preview = computed(() => {
    const v = this.formValues();
    const cat = this.categories().find(c => c.id === v.categoryId) ?? null;
    const ps = v.paymentSource ?? '';
    let paymentName = '';
    if (ps.startsWith('acc:')) {
      paymentName = this.accounts().find(a => `acc:${a.id}` === ps)?.name ?? '';
    } else if (ps.startsWith('card:')) {
      const c = this.cards().find(c => `card:${c.id}` === ps);
      paymentName = c ? `${c.bank} ····${c.last4}` : '';
    }
    const amount = Number(v.amount) || 0;
    const type = (v.type ?? 'EXPENSE') as MovementType;
    const ccy = (v.ccy ?? 'ARS') as MovementCcy;
    return {
      desc: (v.description ?? '').trim() || 'Nuevo movimiento',
      catName: cat?.name ?? 'Sin categoría',
      catIcon: cat?.icon ?? 'circle',
      catColor: cat?.color ?? '#9ed1c5',
      paymentName,
      dateLabel: v.transactionDate ? this.fmtDay(v.transactionDate) : '',
      type,
      ccy,
      amountLabel: (type === 'EXPENSE' ? '- ' : '+ ') + this.fmtAmount(amount, ccy),
    };
  });

  // ── Helpers (template) ────────────────────────────────────────────────────
  /** Tinte translúcido del color de categoría para el fondo del chip. */
  tint(color: string): string {
    return `color-mix(in srgb, ${color} 15%, transparent)`;
  }

  /** Sub-línea de la fila: "Categoría · Medio de pago". */
  rowSub(m: MovementResponse): string {
    const parts = [m.categoryName ?? 'Sin categoría'];
    const payment = m.cardName ?? m.accountName;
    if (payment) parts.push(payment);
    return parts.join(' · ');
  }

  fmtDay(iso: string): string {
    const [y, mo, d] = iso.split('-').map(Number);
    return new Date(y, mo - 1, d)
      .toLocaleDateString('es-AR', { day: '2-digit', month: 'short' })
      .replace('.', '');
  }

  fmtAmount(amount: number, ccy: MovementCcy, rateAtTime?: string | null): string {
    const converted = this.currencyService.convertHistorical(amount, ccy, rateAtTime ?? null);
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

  typeBadgeLabel(m: MovementResponse): string {
    if (m.type === 'INCOME') return 'Ingreso';
    if (m.type === 'CARD_PAYMENT') return 'Pago Tarjeta';
    return 'Egreso';
  }

  signedAmount(m: MovementResponse): string {
    if (m.transferGroupId) return this.fmtAmount(m.amount, m.ccy, m.exchangeRateAtTime);
    const prefix = (m.type === 'EXPENSE' || m.type === 'CARD_PAYMENT') ? '- ' : '+ ';
    return prefix + this.fmtAmount(m.amount, m.ccy, m.exchangeRateAtTime);
  }
}

// ── Date helpers ──────────────────────────────────────────────────────────────

function toISO(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function todayISO(): string { return toISO(new Date()); }
function currentYearMonth(): { year: number; month: number } {
  const n = new Date();
  return { year: n.getFullYear(), month: n.getMonth() };
}
