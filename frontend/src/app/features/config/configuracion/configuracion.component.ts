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
import { CategoryBadgeComponent } from '../../../shared/components/category-badge/category-badge.component';
import {
  CategoryRequest,
  CategoryResponse,
  CategoryType,
} from '../../../core/models/category.models';

// ── Local types ───────────────────────────────────────────────────────────────

export type Tab = 'cuentas' | 'tarjetas' | 'categorias';

export interface AccountLocal {
  id: string;
  name: string;
  kind: 'Banco' | 'Billetera' | 'Efectivo';
  detail: string;
  ccy: 'ARS' | 'USD';
  balance: number;
}

export interface CardLocal {
  id: string;
  bank: string;
  network: 'Visa' | 'Mastercard' | 'Amex';
  last4: string;
  ccy: 'ARS' | 'USD';
  limit: number;
  closing: string;
  due: string;
  accent: string;
}

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
  private readonly categoryService = inject(CategoryService);

  // ── Exposed constants ─────────────────────────────────────────────────────
  readonly categoryIcons = CATEGORY_ICONS;
  readonly colorPalette   = COLOR_PALETTE;
  readonly cardPalette    = CARD_PALETTE;

  // ── Tab ───────────────────────────────────────────────────────────────────
  activeTab = signal<Tab>('cuentas');

  setTab(tab: Tab): void { this.activeTab.set(tab); }

  // ── Accounts (local state — backend not yet built) ────────────────────────
  accounts = signal<AccountLocal[]>([]);

  // ── Cards (local state — backend not yet built) ───────────────────────────
  cards = signal<CardLocal[]>([]);

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
    name:    new FormControl('',      { nonNullable: true, validators: [Validators.required] }),
    kind:    new FormControl<'Banco' | 'Billetera' | 'Efectivo'>('Banco', { nonNullable: true }),
    detail:  new FormControl('',      { nonNullable: true }),
    ccy:     new FormControl<'ARS' | 'USD'>('ARS', { nonNullable: true }),
    balance: new FormControl(0,       { nonNullable: true }),
  });

  // ── Card form ─────────────────────────────────────────────────────────────
  cardForm = new FormGroup({
    bank:    new FormControl('',      { nonNullable: true, validators: [Validators.required] }),
    network: new FormControl<'Visa' | 'Mastercard' | 'Amex'>('Visa', { nonNullable: true }),
    last4:   new FormControl('',      { nonNullable: true, validators: [Validators.required, Validators.minLength(4), Validators.maxLength(4), Validators.pattern(/^\d{4}$/)] }),
    ccy:     new FormControl<'ARS' | 'USD'>('ARS', { nonNullable: true }),
    limit:   new FormControl(0,       { nonNullable: true }),
    closing: new FormControl('',      { nonNullable: true }),
    due:     new FormControl('',      { nonNullable: true }),
    accent:  new FormControl('#52eacd', { nonNullable: true }),
  });

  // ── Category form ─────────────────────────────────────────────────────────
  categoryForm = new FormGroup({
    name:  new FormControl('',           { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    type:  new FormControl<CategoryType>('EXPENSE', { nonNullable: true }),
    icon:  new FormControl('circle',     { nonNullable: true }),
    color: new FormControl('#52eacd',    { nonNullable: true }),
  });

  private readonly catFormValues = toSignal(this.categoryForm.valueChanges, {
    initialValue: this.categoryForm.getRawValue(),
  });

  previewCategory = computed<CategoryResponse>(() => {
    const v = this.catFormValues();
    return {
      id:        'preview',
      name:      (v.name  as string)?.trim() || 'Nueva categoría',
      icon:      (v.icon  as string)         || 'circle',
      color:     (v.color as string)         || '#52eacd',
      type:      (v.type  as CategoryType)   || 'EXPENSE',
      isDefault: false,
    };
  });

  // ── Lifecycle ─────────────────────────────────────────────────────────────
  ngOnInit(): void { this.loadCategories(); }

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
    this.accountForm.reset({ name: '', kind: 'Banco', detail: '', ccy: 'ARS', balance: 0 });
    this.formError.set(null);
    this.modal.set({ kind: 'account', mode: 'create' });
  }

  openEditAccount(a: AccountLocal): void {
    this.accountForm.setValue({ name: a.name, kind: a.kind, detail: a.detail, ccy: a.ccy, balance: a.balance });
    this.formError.set(null);
    this.modal.set({ kind: 'account', mode: 'edit', id: a.id });
  }

  openDeleteAccount(a: AccountLocal): void {
    this.modal.set({ kind: 'account', mode: 'delete', id: a.id, label: a.name });
  }

  submitAccount(): void {
    if (this.accountForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    const v = this.accountForm.getRawValue();
    const m = this.modal();
    if (!m) return;

    if (m.mode === 'create') {
      const rec: AccountLocal = { id: crypto.randomUUID(), ...v };
      this.accounts.update(list => [...list, rec]);
    } else {
      this.accounts.update(list =>
        list.map(a => a.id === m.id ? { ...a, ...v } : a)
      );
    }
    this.submitting.set(false);
    this.closeModal();
  }

  confirmDeleteAccount(): void {
    const m = this.modal();
    if (!m?.id) return;
    this.accounts.update(list => list.filter(a => a.id !== m.id));
    this.closeModal();
  }

  // ── Card CRUD ─────────────────────────────────────────────────────────────
  openCreateCard(): void {
    this.cardForm.reset({ bank: '', network: 'Visa', last4: '', ccy: 'ARS', limit: 0, closing: '', due: '', accent: '#52eacd' });
    this.formError.set(null);
    this.modal.set({ kind: 'card', mode: 'create' });
  }

  openEditCard(c: CardLocal): void {
    this.cardForm.setValue({ bank: c.bank, network: c.network, last4: c.last4, ccy: c.ccy, limit: c.limit, closing: c.closing, due: c.due, accent: c.accent });
    this.formError.set(null);
    this.modal.set({ kind: 'card', mode: 'edit', id: c.id });
  }

  openDeleteCard(c: CardLocal): void {
    this.modal.set({ kind: 'card', mode: 'delete', id: c.id, label: `${c.bank} ····${c.last4}` });
  }

  submitCard(): void {
    if (this.cardForm.invalid || this.submitting()) return;
    this.submitting.set(true);
    const v = this.cardForm.getRawValue();
    const m = this.modal();
    if (!m) return;

    if (m.mode === 'create') {
      const rec: CardLocal = { id: crypto.randomUUID(), ...v };
      this.cards.update(list => [...list, rec]);
    } else {
      this.cards.update(list =>
        list.map(c => c.id === m.id ? { ...c, ...v } : c)
      );
    }
    this.submitting.set(false);
    this.closeModal();
  }

  confirmDeleteCard(): void {
    const m = this.modal();
    if (!m?.id) return;
    this.cards.update(list => list.filter(c => c.id !== m.id));
    this.closeModal();
  }

  // ── Category CRUD ─────────────────────────────────────────────────────────
  openCreateCategory(): void {
    this.categoryForm.reset({ name: '', type: 'EXPENSE', icon: 'circle', color: '#52eacd' });
    this.formError.set(null);
    this.modal.set({ kind: 'category', mode: 'create' });
  }

  openEditCategory(cat: CategoryResponse): void {
    this.categoryForm.setValue({ name: cat.name, type: cat.type, icon: cat.icon, color: cat.color });
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
