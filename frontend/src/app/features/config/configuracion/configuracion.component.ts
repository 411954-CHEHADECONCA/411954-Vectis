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
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  LucidePlus,
  LucidePencil,
  LucideTrash2,
  LucideX,
  LucideAlertCircle,
  LucideRefreshCw,
  LucideLock,
  LucideBuilding2,
  LucideWallet,
  LucideBanknote,
  LucideCreditCard,
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
import { CategoryService } from '../../../core/services/category.service';
import { AccountService } from '../../../core/services/account.service';
import { CreditCardService } from '../../../core/services/credit-card.service';
import { CategoryBadgeComponent } from '../../../shared/components/category-badge/category-badge.component';
import {
  CategoryRequest,
  CategoryResponse,
  CategoryType,
} from '../../../core/models/category.models';
import {
  AccountRequest,
  AccountResponse,
  AccountKind,
  AccountCcy,
} from '../../../core/models/account.models';
import {
  CardRequest,
  CardResponse,
  CardNetwork,
  CardCcy,
} from '../../../core/models/card.models';

// ── Local types ───────────────────────────────────────────────────────────────

export type Tab = 'cuentas' | 'tarjetas' | 'categorias';


export type ModalKind = 'account' | 'card' | 'category';
export type ModalMode = 'create' | 'edit' | 'delete';

export interface ModalState {
  kind: ModalKind;
  mode: ModalMode;
  id?: string;
  label?: string;
}

// ── Constants ─────────────────────────────────────────────────────────────────

export const CATEGORY_ICONS: { value: string; label: string }[] = [
  { value: 'utensils',          label: 'Comida' },
  { value: 'car',               label: 'Transporte' },
  { value: 'zap',               label: 'Servicios' },
  { value: 'repeat',            label: 'Suscripción' },
  { value: 'music',             label: 'Ocio' },
  { value: 'heart',             label: 'Salud' },
  { value: 'book',              label: 'Educación' },
  { value: 'shirt',             label: 'Ropa' },
  { value: 'home',              label: 'Hogar' },
  { value: 'briefcase',         label: 'Trabajo' },
  { value: 'monitor',           label: 'Tecnología' },
  { value: 'trending-up',       label: 'Inversión' },
  { value: 'arrow-right-left',  label: 'Transferencia' },
  { value: 'dumbbell',          label: 'Deporte' },
  { value: 'circle',            label: 'Otro' },
];

export const COLOR_PALETTE = [
  '#52eacd', '#65fadd', '#41ddc1', '#9ed1c5',
  '#b2dbd2', '#7ec8e3', '#d7a0e8', '#e8c37a',
  '#ffb4ab', '#ff8a80',
];

export const CARD_PALETTE = [
  '#52eacd', '#9ed1c5', '#7ec8e3', '#d7a0e8',
  '#e8c37a', '#ffb4ab', '#ff8a80', '#65fadd',
];

// ── Component ─────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-configuracion',
  standalone: true,
  templateUrl: './configuracion.component.html',
  styleUrl: './configuracion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    CategoryBadgeComponent,
    LucidePlus, LucidePencil, LucideTrash2, LucideX,
    LucideAlertCircle, LucideRefreshCw, LucideLock,
    LucideBuilding2, LucideWallet, LucideBanknote, LucideCreditCard,
    LucideUtensils, LucideCar, LucideZap, LucideRepeat, LucideMusic,
    LucideHeart, LucideBook, LucideShirt, LucideHome, LucideBriefcase,
    LucideMonitor, LucideTrendingUp, LucideArrowRightLeft, LucideDumbbell, LucideCircle,
  ],
})
export class ConfiguracionComponent implements OnInit {
  private readonly categoryService  = inject(CategoryService);
  private readonly accountService   = inject(AccountService);
  private readonly creditCardService = inject(CreditCardService);

  // ── Exposed constants ─────────────────────────────────────────────────────
  readonly categoryIcons = CATEGORY_ICONS;
  readonly colorPalette   = COLOR_PALETTE;
  readonly cardPalette    = CARD_PALETTE;

  // ── Tab ───────────────────────────────────────────────────────────────────
  activeTab = signal<Tab>('cuentas');

  setTab(tab: Tab): void { this.activeTab.set(tab); }

  // ── Accounts ──────────────────────────────────────────────────────────────
  accounts   = signal<AccountResponse[]>([]);
  accLoading = signal(false);
  accError   = signal<string | null>(null);

  // ── Cards ─────────────────────────────────────────────────────────────────
  cards       = signal<CardResponse[]>([]);
  cardsLoading = signal(false);
  cardsError   = signal<string | null>(null);

  // ── Categories (real backend) ─────────────────────────────────────────────
  categories   = signal<CategoryResponse[]>([]);
  loading      = signal(false);
  catError     = signal<string | null>(null);

  ingresos = computed(() => this.categories().filter(c => c.type === 'INCOME'));
  egresos  = computed(() =>
    this.categories().filter(c => c.type === 'EXPENSE' || c.type === 'BOTH')
  );

  // ── Tab defs (for template loop) ──────────────────────────────────────────
  tabDefs = computed(() => [
    { id: 'cuentas'    as Tab, label: 'Cuentas bancarias',   count: this.accounts().length    },
    { id: 'tarjetas'   as Tab, label: 'Tarjetas de crédito', count: this.cards().length       },
    { id: 'categorias' as Tab, label: 'Categorías',          count: this.categories().length  },
  ]);

  // ── Modal ─────────────────────────────────────────────────────────────────
  modal      = signal<ModalState | null>(null);
  submitting = signal(false);
  formError  = signal<string | null>(null);

  // ── Account form ──────────────────────────────────────────────────────────
  accountForm = new FormGroup({
    name:       new FormControl('',    { nonNullable: true, validators: [Validators.required] }),
    kind:       new FormControl<AccountKind>('Banco', { nonNullable: true }),
    detail:     new FormControl('',    { nonNullable: true }),
    ccy:        new FormControl<AccountCcy>('ARS', { nonNullable: true }),
    balance:    new FormControl(0,     { nonNullable: true }),
    remunerada: new FormControl(false, { nonNullable: true }),
    tna:        new FormControl<number | null>(null),
  });

  // ── Card form ─────────────────────────────────────────────────────────────
  cardForm = new FormGroup({
    bank:        new FormControl('',       { nonNullable: true, validators: [Validators.required] }),
    network:     new FormControl<CardNetwork>('Visa', { nonNullable: true }),
    last4:       new FormControl('',       { nonNullable: true, validators: [Validators.required, Validators.minLength(4), Validators.maxLength(4), Validators.pattern(/^\d{4}$/)] }),
    ccy:         new FormControl<CardCcy>('ARS', { nonNullable: true }),
    creditLimit: new FormControl(0,        { nonNullable: true }),
    closingDay:  new FormControl<number>(1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(31)] }),
    dueDay:      new FormControl<number>(1, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(31)] }),
    accent:      new FormControl('#52eacd', { nonNullable: true }),
  });

  // ── Category form ─────────────────────────────────────────────────────────
  categoryForm = new FormGroup({
    name:            new FormControl('',           { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    type:            new FormControl<CategoryType>('EXPENSE', { nonNullable: true }),
    icon:            new FormControl('circle',     { nonNullable: true }),
    color:           new FormControl('#52eacd',    { nonNullable: true }),
    estimatedAmount: new FormControl<number | null>(null),
  });

  private readonly catFormValues = toSignal(this.categoryForm.valueChanges, {
    initialValue: this.categoryForm.getRawValue(),
  });

  previewCategory = computed<CategoryResponse>(() => {
    const v = this.catFormValues();
    return {
      id:              'preview',
      name:            (v.name  as string)?.trim() || 'Nueva categoría',
      icon:            (v.icon  as string)         || 'circle',
      color:           (v.color as string)         || '#52eacd',
      type:            (v.type  as CategoryType)   || 'EXPENSE',
      isDefault:       false,
      estimatedAmount: null,
    };
  });

  // ── Lifecycle ─────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.loadAccounts();
    this.loadCards();
    this.loadCategories();
  }

  loadAccounts(): void {
    this.accLoading.set(true);
    this.accError.set(null);
    this.accountService.getAccounts().subscribe({
      next:  list => { this.accounts.set(list); this.accLoading.set(false); },
      error: ()   => { this.accError.set('No se pudieron cargar las cuentas'); this.accLoading.set(false); },
    });
  }

  loadCards(): void {
    this.cardsLoading.set(true);
    this.cardsError.set(null);
    this.creditCardService.getCards().subscribe({
      next:  list => { this.cards.set(list); this.cardsLoading.set(false); },
      error: ()   => { this.cardsError.set('No se pudieron cargar las tarjetas'); this.cardsLoading.set(false); },
    });
  }

  loadCategories(): void {
    this.loading.set(true);
    this.catError.set(null);
    this.categoryService.getCategories().subscribe({
      next:  cats => { this.categories.set(cats); this.loading.set(false); },
      error: ()   => { this.catError.set('No se pudieron cargar las categorías'); this.loading.set(false); },
    });
  }

  // ── Modal helpers ─────────────────────────────────────────────────────────
  closeModal(): void {
    this.modal.set(null);
    this.submitting.set(false);
    this.formError.set(null);
  }

  // ── Account CRUD ──────────────────────────────────────────────────────────
  openCreateAccount(): void {
    this.accountForm.reset({ name: '', kind: 'Banco', detail: '', ccy: 'ARS', balance: 0, remunerada: false, tna: null });
    this.formError.set(null);
    this.modal.set({ kind: 'account', mode: 'create' });
  }

  openEditAccount(a: AccountResponse): void {
    this.accountForm.setValue({
      name: a.name, kind: a.kind, detail: a.detail ?? '',
      ccy: a.ccy, balance: a.balance, remunerada: a.remunerada, tna: a.tna,
    });
    this.formError.set(null);
    this.modal.set({ kind: 'account', mode: 'edit', id: a.id });
  }

  openDeleteAccount(a: AccountResponse): void {
    this.modal.set({ kind: 'account', mode: 'delete', id: a.id, label: a.name });
  }

  submitAccount(): void {
    if (this.accountForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);
    const v = this.accountForm.getRawValue();
    const req: AccountRequest = { ...v, detail: v.detail || null };
    const m = this.modal();
    if (!m) return;

    const op = m.id
      ? this.accountService.updateAccount(m.id, req)
      : this.accountService.createAccount(req);

    op.subscribe({
      next: saved => {
        if (m.id) {
          this.accounts.update(list => list.map(a => a.id === m.id ? saved : a));
        } else {
          this.accounts.update(list => [...list, saved]);
        }
        this.submitting.set(false);
        this.closeModal();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(err.error?.message ?? 'Ocurrió un error al guardar');
      },
    });
  }

  confirmDeleteAccount(): void {
    const m = this.modal();
    if (!m?.id || this.submitting()) return;
    this.submitting.set(true);
    this.accountService.deleteAccount(m.id).subscribe({
      next: () => {
        this.accounts.update(list => list.filter(a => a.id !== m.id));
        this.submitting.set(false);
        this.closeModal();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.accError.set(err.error?.message ?? 'No se pudo eliminar la cuenta');
        this.closeModal();
      },
    });
  }

  // ── Card CRUD ─────────────────────────────────────────────────────────────
  openCreateCard(): void {
    this.cardForm.reset({ bank: '', network: 'Visa', last4: '', ccy: 'ARS', creditLimit: 0, closingDay: 1, dueDay: 1, accent: '#52eacd' });
    this.formError.set(null);
    this.modal.set({ kind: 'card', mode: 'create' });
  }

  openEditCard(c: CardResponse): void {
    this.cardForm.setValue({ bank: c.bank, network: c.network, last4: c.last4, ccy: c.ccy, creditLimit: c.creditLimit, closingDay: c.closingDay, dueDay: c.dueDay, accent: c.accent });
    this.formError.set(null);
    this.modal.set({ kind: 'card', mode: 'edit', id: c.id });
  }

  openDeleteCard(c: CardResponse): void {
    this.modal.set({ kind: 'card', mode: 'delete', id: c.id, label: `${c.bank} ····${c.last4}` });
  }

  submitCard(): void {
    if (this.cardForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);
    const v   = this.cardForm.getRawValue();
    const req: CardRequest = { ...v };
    const m   = this.modal();
    if (!m) return;

    const op = m.id
      ? this.creditCardService.updateCard(m.id, req)
      : this.creditCardService.createCard(req);

    op.subscribe({
      next: saved => {
        if (m.id) {
          this.cards.update(list => list.map(c => c.id === m.id ? saved : c));
        } else {
          this.cards.update(list => [...list, saved]);
        }
        this.submitting.set(false);
        this.closeModal();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(err.error?.message ?? 'Ocurrió un error al guardar');
      },
    });
  }

  confirmDeleteCard(): void {
    const m = this.modal();
    if (!m?.id || this.submitting()) return;
    this.submitting.set(true);
    this.creditCardService.deleteCard(m.id).subscribe({
      next: () => {
        this.cards.update(list => list.filter(c => c.id !== m.id));
        this.submitting.set(false);
        this.closeModal();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.cardsError.set(err.error?.message ?? 'No se pudo eliminar la tarjeta');
        this.closeModal();
      },
    });
  }

  // ── Category CRUD ─────────────────────────────────────────────────────────
  openCreateCategory(defaultType: CategoryType = 'EXPENSE'): void {
    this.categoryForm.reset({ name: '', type: defaultType, icon: 'circle', color: '#52eacd', estimatedAmount: null });
    this.formError.set(null);
    this.modal.set({ kind: 'category', mode: 'create' });
  }

  openEditCategory(cat: CategoryResponse): void {
    this.categoryForm.setValue({ name: cat.name, type: cat.type, icon: cat.icon, color: cat.color, estimatedAmount: cat.estimatedAmount ?? null });
    this.formError.set(null);
    this.modal.set({ kind: 'category', mode: 'edit', id: cat.id });
  }

  openDeleteCategory(cat: CategoryResponse): void {
    this.modal.set({ kind: 'category', mode: 'delete', id: cat.id, label: cat.name });
  }

  submitCategory(): void {
    if (this.categoryForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);

    const req = this.categoryForm.getRawValue() as CategoryRequest;
    const m   = this.modal();
    if (!m) return;

    const op = m.id
      ? this.categoryService.updateCategory(m.id, req)
      : this.categoryService.createCategory(req);

    op.subscribe({
      next: saved => {
        if (m.id) {
          this.categories.update(list => list.map(c => c.id === m.id ? saved : c));
        } else {
          this.categories.update(list => [...list, saved]);
        }
        this.submitting.set(false);
        this.closeModal();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(err.error?.message ?? 'Ocurrió un error al guardar');
      },
    });
  }

  confirmDeleteCategory(): void {
    const m = this.modal();
    if (!m?.id || this.submitting()) return;
    this.submitting.set(true);
    this.categoryService.deleteCategory(m.id).subscribe({
      next: () => {
        this.categories.update(list => list.filter(c => c.id !== m.id));
        this.submitting.set(false);
        this.closeModal();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.catError.set(err.error?.message ?? 'No se pudo eliminar la categoría');
        this.closeModal();
      },
    });
  }

  // ── Unified delete confirm ────────────────────────────────────────────────
  confirmDelete(): void {
    const m = this.modal();
    if (!m) return;
    if (m.kind === 'account')  this.confirmDeleteAccount();
    if (m.kind === 'card')     this.confirmDeleteCard();
    if (m.kind === 'category') this.confirmDeleteCategory();
  }

  // ── Formatting ────────────────────────────────────────────────────────────
  fmtAmount(balance: number, ccy: 'ARS' | 'USD'): string {
    if (ccy === 'USD') {
      return `US$ ${balance.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    return `$ ${Math.round(balance).toLocaleString('es-AR')}`;
  }

  cardGradient(accent: string): string {
    return `linear-gradient(135deg, ${accent} 0%, color-mix(in srgb, ${accent} 45%, #000) 100%)`;
  }
}
