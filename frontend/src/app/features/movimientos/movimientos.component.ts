import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
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
import { CurrencyService } from '../../core/services/currency.service';
import {
  MovementResponse,
  MovementRequest,
  MovementSummary,
  MovementType,
  MovementCcy,
  MovementFilters,
} from '../../core/models/movement.models';
import { PageResponse } from '../../core/models/pagination.models';
import { CategoryResponse } from '../../core/models/category.models';
import { AccountResponse } from '../../core/models/account.models';
import { CardResponse } from '../../core/models/card.models';

type TypeFilter = 'Todos' | 'INCOME' | 'EXPENSE';

interface MovementModalState {
  mode: 'create' | 'edit' | 'delete';
  id?: string;
  label?: string;
  isInstallment?: boolean;
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
    LucideSearch, LucideChevronLeft, LucideChevronRight, LucideRefreshCw,
    LucideUtensils, LucideCar, LucideZap, LucideRepeat, LucideMusic,
    LucideHeart, LucideBook, LucideShirt, LucideHome, LucideBriefcase,
    LucideMonitor, LucideTrendingUp, LucideArrowRightLeft, LucideDumbbell, LucideCircle,
  ],
})
export class MovimientosComponent implements OnInit {
  private readonly movementService  = inject(MovementService);
  private readonly categoryService  = inject(CategoryService);
  private readonly accountService   = inject(AccountService);
  private readonly creditCardService = inject(CreditCardService);
  readonly currencyService          = inject(CurrencyService);

  // ── Data ────────────────────────────────────────────────────────────────
  page       = signal<PageResponse<MovementResponse> | null>(null);
  summary    = signal<MovementSummary | null>(null);
  loading    = signal(false);
  error      = signal<string | null>(null);

  categories = signal<CategoryResponse[]>([]);
  accounts   = signal<AccountResponse[]>([]);
  cards      = signal<CardResponse[]>([]);

  // ── Filters ─────────────────────────────────────────────────────────────
  fFrom       = signal(monthStartISO());
  fTo         = signal(monthEndISO());
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
    description:     new FormControl('',  { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    type:           new FormControl<MovementType>('EXPENSE', { nonNullable: true }),
    categoryId:     new FormControl<string | null>(null),
    paymentSource:  new FormControl('',   { nonNullable: true }),
    ccy:            new FormControl<MovementCcy>('ARS', { nonNullable: true }),
    amount:         new FormControl<number>(0, { nonNullable: true, validators: [Validators.required, Validators.min(0.01)] }),
    transactionDate: new FormControl(todayISO(), { nonNullable: true, validators: [Validators.required] }),
    installments:   new FormControl<number>(1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(360)] }),
  });

  private readonly formValues = toSignal(this.movementForm.valueChanges, {
    initialValue: this.movementForm.getRawValue(),
  });

  formType   = computed<MovementType>(() => this.formValues().type ?? 'EXPENSE');
  formSource = computed(() => this.formValues().paymentSource ?? '');
  /** Las cuotas solo aplican a egresos pagados con tarjeta. */
  showInstallments = computed(() => this.formType() === 'EXPENSE' && this.formSource().startsWith('card:'));

  /** Categorías válidas para el tipo seleccionado (incluye BOTH). */
  formCategories = computed(() => {
    const t = this.formType();
    return this.categories().filter(c => c.type === t || c.type === 'BOTH');
  });

  /** Categorías disponibles en el filtro, acordes al filtro de tipo. */
  filterCategories = computed(() => {
    const t = this.fType();
    if (t === 'Todos') return this.categories();
    return this.categories().filter(c => c.type === t || c.type === 'BOTH');
  });

  // ── Lifecycle ─────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.loadCategories();
    this.loadAccounts();
    this.loadCards();
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

  onFromChange(value: string): void { this.fFrom.set(value); this.reloadFromFirstPage(); }
  onToChange(value: string): void { this.fTo.set(value); this.reloadFromFirstPage(); }

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
    this.fFrom.set(monthStartISO());
    this.fTo.set(monthEndISO());
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
      description: '', type: 'EXPENSE', categoryId: null, paymentSource: '',
      ccy: 'ARS', amount: 0, transactionDate: todayISO(), installments: 1,
    });
    this.formError.set(null);
    this.modal.set({ mode: 'create' });
  }

  openEdit(m: MovementResponse): void {
    if (m.installment) return; // las cuotas no se editan individualmente
    const paymentSource = m.accountId ? `acc:${m.accountId}`
                        : m.cardId    ? `card:${m.cardId}`
                        : '';
    this.movementForm.reset({
      description: m.description,
      type: m.type,
      categoryId: m.categoryId,
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
    this.modal.set({ mode: 'delete', id: m.id, label: m.description, isInstallment: m.installment });
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
    if (!(this.movementForm.controls.type.value === 'EXPENSE'
          && this.movementForm.controls.paymentSource.value.startsWith('card:'))) {
      this.movementForm.patchValue({ installments: 1 });
    }
    const catId = this.movementForm.controls.categoryId.value;
    if (catId) {
      const cat = this.categories().find(c => c.id === catId);
      if (cat && cat.type !== type && cat.type !== 'BOTH') {
        this.movementForm.patchValue({ categoryId: null });
      }
    }
  }

  submit(): void {
    if (this.movementForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);
    const v = this.movementForm.getRawValue();
    const ps = v.paymentSource;
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
    const m = this.modal();
    if (!m) return;

    const op$ = m.id
      ? this.movementService.update(m.id, req).pipe(map(() => void 0))
      : this.movementService.create(req).pipe(map(() => void 0));

    op$.subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeModal();
        this.reload();
      },
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
    this.movementService.delete(m.id).subscribe({
      next: () => {
        this.submitting.set(false);
        this.closeModal();
        this.reload();
      },
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

  fmtAmount(amount: number, ccy: MovementCcy): string {
    if (ccy === 'USD') {
      return `US$ ${amount.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    return `$ ${Math.round(amount).toLocaleString('es-AR')}`;
  }

  signedAmount(m: MovementResponse): string {
    const prefix = m.type === 'EXPENSE' ? '- ' : '';
    return prefix + this.fmtAmount(m.amount, m.ccy);
  }
}

// ── Date helpers ──────────────────────────────────────────────────────────────

function toISO(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function todayISO(): string { return toISO(new Date()); }
function monthStartISO(): string {
  const n = new Date();
  return toISO(new Date(n.getFullYear(), n.getMonth(), 1));
}
function monthEndISO(): string {
  const n = new Date();
  return toISO(new Date(n.getFullYear(), n.getMonth() + 1, 0));
}
