import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
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
  LucidePlus,
  LucidePencil,
  LucideTrash2,
  LucideLock,
  LucideX,
  LucideAlertCircle,
  LucideRefreshCw,
} from '@lucide/angular';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryBadgeComponent } from '../../../shared/components/category-badge/category-badge.component';
import { CategoryRequest, CategoryResponse, CategoryType } from '../../../core/models/category.models';

export const AVAILABLE_ICONS: { value: string; label: string }[] = [
  { value: 'utensils',         label: 'Comida' },
  { value: 'car',              label: 'Transporte' },
  { value: 'zap',              label: 'Servicios' },
  { value: 'repeat',           label: 'Suscripción' },
  { value: 'music',            label: 'Ocio' },
  { value: 'heart',            label: 'Salud' },
  { value: 'book',             label: 'Educación' },
  { value: 'shirt',            label: 'Ropa' },
  { value: 'home',             label: 'Hogar' },
  { value: 'briefcase',        label: 'Trabajo' },
  { value: 'monitor',          label: 'Tecnología' },
  { value: 'trending-up',      label: 'Inversión' },
  { value: 'arrow-right-left', label: 'Transferencia' },
  { value: 'dumbbell',         label: 'Deporte' },
  { value: 'circle',           label: 'Otro' },
];

const COLOR_PRESETS = [
  '#26CDB2', '#52eacd', '#9ed1c5', '#345A53',
  '#3B82F6', '#8B5CF6', '#F59E0B', '#EF4444',
  '#06B6D4', '#EC4899', '#84CC16', '#6B7280',
];

const TYPE_OPTIONS: { value: CategoryType; label: string }[] = [
  { value: 'EXPENSE', label: 'Gasto' },
  { value: 'INCOME',  label: 'Ingreso' },
  { value: 'BOTH',    label: 'Ambos' },
];

@Component({
  selector: 'app-categories',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    CategoryBadgeComponent,
    LucideUtensils, LucideCar, LucideZap, LucideRepeat, LucideMusic,
    LucideHeart, LucideBook, LucideShirt, LucideHome, LucideBriefcase,
    LucideMonitor, LucideTrendingUp, LucideArrowRightLeft, LucideDumbbell, LucideCircle,
    LucidePlus, LucidePencil, LucideTrash2, LucideLock, LucideX,
    LucideAlertCircle, LucideRefreshCw,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- ── Icon renderer template (shared by picker and preview) ─────────── -->
    <ng-template #iconTpl let-name>
      @switch (name) {
        @case ('utensils')         { <svg lucideUtensils         [size]="18" [strokeWidth]="1.75" /> }
        @case ('car')              { <svg lucideCar              [size]="18" [strokeWidth]="1.75" /> }
        @case ('zap')              { <svg lucideZap              [size]="18" [strokeWidth]="1.75" /> }
        @case ('repeat')           { <svg lucideRepeat           [size]="18" [strokeWidth]="1.75" /> }
        @case ('music')            { <svg lucideMusic            [size]="18" [strokeWidth]="1.75" /> }
        @case ('heart')            { <svg lucideHeart            [size]="18" [strokeWidth]="1.75" /> }
        @case ('book')             { <svg lucideBook             [size]="18" [strokeWidth]="1.75" /> }
        @case ('shirt')            { <svg lucideShirt            [size]="18" [strokeWidth]="1.75" /> }
        @case ('home')             { <svg lucideHome             [size]="18" [strokeWidth]="1.75" /> }
        @case ('briefcase')        { <svg lucideBriefcase        [size]="18" [strokeWidth]="1.75" /> }
        @case ('monitor')          { <svg lucideMonitor          [size]="18" [strokeWidth]="1.75" /> }
        @case ('trending-up')      { <svg lucideTrendingUp       [size]="18" [strokeWidth]="1.75" /> }
        @case ('arrow-right-left') { <svg lucideArrowRightLeft   [size]="18" [strokeWidth]="1.75" /> }
        @case ('dumbbell')         { <svg lucideDumbbell         [size]="18" [strokeWidth]="1.75" /> }
        @default                   { <svg lucideCircle           [size]="18" [strokeWidth]="1.75" /> }
      }
    </ng-template>

    <!-- ── Page ───────────────────────────────────────────────────────────── -->
    <div class="page">

      <header class="page-header">
        <div>
          <h1 class="page-title">Categorías</h1>
          <p class="page-subtitle">Organizá tus ingresos y egresos con categorías personalizadas</p>
        </div>
        <button class="btn-add" (click)="openCreate()">
          <svg lucidePlus [size]="15" [strokeWidth]="2.5" />
          Nueva categoría
        </button>
      </header>

      @if (loading()) {
        <div class="skeleton-group">
          <div class="skeleton-label"></div>
          @for (i of [1, 2, 3, 4, 5]; track i) {
            <div class="skeleton-row" [style.animation-delay]="(i * 0.08) + 's'"></div>
          }
        </div>
        <div class="skeleton-group">
          <div class="skeleton-label"></div>
          @for (i of [1, 2, 3]; track i) {
            <div class="skeleton-row" [style.animation-delay]="(i * 0.08 + 0.5) + 's'"></div>
          }
        </div>
      } @else if (error()) {
        <div class="error-state">
          <svg lucideAlertCircle [size]="28" />
          <p>{{ error() }}</p>
          <button class="btn-retry" (click)="loadCategories()">
            <svg lucideRefreshCw [size]="13" /> Reintentar
          </button>
        </div>
      } @else {
        @for (group of groups(); track group.type) {
          <section class="group">
            <div class="group-header">
              <span class="group-label">{{ group.label }}</span>
              <span class="group-count">{{ group.items.length }}</span>
            </div>
            <div class="category-list">
              @for (cat of group.items; track cat.id) {
                <div class="category-row" [class.is-default]="cat.isDefault">
                  <app-category-badge [category]="cat" />
                  @if (cat.isDefault) {
                    <span class="system-tag">
                      <svg lucideLock [size]="10" [strokeWidth]="2" /> Sistema
                    </span>
                  } @else {
                    <div class="row-actions">
                      <button class="action-btn" title="Editar" (click)="openEdit(cat)">
                        <svg lucidePencil [size]="13" [strokeWidth]="2" />
                      </button>
                      <button class="action-btn action-btn--danger" title="Eliminar" (click)="requestDelete(cat)">
                        <svg lucideTrash2 [size]="13" [strokeWidth]="2" />
                      </button>
                    </div>
                  }
                </div>
              }
            </div>
          </section>
        }
      }
    </div>

    <!-- ── Create / Edit modal ────────────────────────────────────────────── -->
    @if (modalOpen()) {
      <div class="backdrop" (click)="closeModal()">
        <div class="modal" (click)="$event.stopPropagation()">

          <div class="modal-header">
            <h2>{{ editingId() ? 'Editar categoría' : 'Nueva categoría' }}</h2>
            <button class="close-btn" (click)="closeModal()">
              <svg lucideX [size]="16" />
            </button>
          </div>

          <!-- Live preview -->
          <div class="preview-strip">
            <span class="preview-label">Vista previa</span>
            <app-category-badge [category]="previewCategory()" />
          </div>

          <!-- Form body -->
          <div class="modal-body">
            <form [formGroup]="form" (ngSubmit)="submit()">

              <!-- Type -->
              <div class="field">
                <span class="field-label">Tipo de movimiento</span>
                <div class="type-seg">
                  @for (t of typeOptions; track t.value) {
                    <button
                      type="button"
                      class="type-seg-btn"
                      [class.active]="form.controls.type.value === t.value"
                      (click)="form.controls.type.setValue(t.value)"
                    >{{ t.label }}</button>
                  }
                </div>
              </div>

              <!-- Name -->
              <div class="field">
                <label class="field-label" for="cat-name">Nombre</label>
                <input
                  id="cat-name"
                  class="input-line"
                  type="text"
                  formControlName="name"
                  placeholder="Ej: Alimentación"
                  maxlength="100"
                  autocomplete="off"
                />
                @if (form.controls.name.invalid && form.controls.name.touched) {
                  <span class="field-error">El nombre es requerido</span>
                }
              </div>

              <!-- Icon grid -->
              <div class="field">
                <span class="field-label">Ícono</span>
                <div class="icon-grid">
                  @for (icon of availableIcons; track icon.value) {
                    <button
                      type="button"
                      class="icon-btn"
                      [class.active]="form.controls.icon.value === icon.value"
                      (click)="form.controls.icon.setValue(icon.value)"
                      [title]="icon.label"
                    >
                      <ng-container [ngTemplateOutlet]="iconTpl" [ngTemplateOutletContext]="{ $implicit: icon.value }" />
                    </button>
                  }
                </div>
              </div>

              <!-- Color -->
              <div class="field">
                <span class="field-label">Color distintivo</span>
                <div class="color-row">
                  @for (c of colorPresets; track c) {
                    <button
                      type="button"
                      class="color-dot"
                      [style.background-color]="c"
                      [class.active]="form.controls.color.value === c"
                      (click)="form.controls.color.setValue(c)"
                      [title]="c"
                    ></button>
                  }
                </div>
              </div>

              @if (formError()) {
                <p class="form-error">{{ formError() }}</p>
              }

            </form>
          </div>

          <!-- Footer -->
          <div class="modal-footer">
            <button type="button" class="btn-cancel" (click)="closeModal()">Cancelar</button>
            <button
              type="button"
              class="btn-confirm"
              [disabled]="form.invalid || submitting()"
              (click)="submit()"
            >
              {{ submitting() ? 'Guardando...' : (editingId() ? 'Guardar cambios' : 'Crear categoría') }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- ── Delete confirmation ────────────────────────────────────────────── -->
    @if (deleteTarget()) {
      <div class="backdrop" (click)="cancelDelete()">
        <div class="modal modal-sm" (click)="$event.stopPropagation()">
          <div class="delete-icon-wrap">
            <svg lucideTrash2 [size]="18" [strokeWidth]="2" />
          </div>
          <h2 class="delete-title">Eliminar categoría</h2>
          <p class="delete-body">¿Eliminás <strong>{{ deleteTarget()!.name }}</strong>? Esta acción no se puede deshacer.</p>
          <div class="modal-footer modal-footer--sm">
            <button class="btn-cancel" (click)="cancelDelete()">Cancelar</button>
            <button class="btn-danger" [disabled]="submitting()" (click)="confirmDelete()">
              {{ submitting() ? 'Eliminando...' : 'Eliminar' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    /* ── Page ──────────────────────────────────────────────────────────────── */
    .page {
      max-width: 680px;
      margin: 0 auto;
      padding: 32px 24px;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 16px;
      margin-bottom: 36px;
    }

    .page-title {
      font-size: 20px;
      font-weight: 700;
      color: var(--color-on-surface);
      margin: 0 0 4px;
      letter-spacing: -0.01em;
    }

    .page-subtitle {
      font-size: 13px;
      color: var(--color-text-muted);
      margin: 0;
    }

    .btn-add {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--color-primary);
      color: var(--color-on-primary);
      border: none;
      border-radius: var(--radius);
      padding: 8px 14px;
      font-weight: 600;
      font-size: 13px;
      font-family: var(--font-sans);
      cursor: pointer;
      white-space: nowrap;
      flex-shrink: 0;
      transition: filter 150ms ease;
    }
    .btn-add:hover { filter: brightness(1.1); }

    /* ── Groups ─────────────────────────────────────────────────────────────── */
    .group { margin-bottom: 28px; }

    .group-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 10px;
      padding-bottom: 8px;
      border-bottom: 1px solid var(--color-outline-variant);
    }

    .group-label {
      font-size: 13px;
      font-weight: 600;
      color: var(--color-on-surface-variant);
    }

    .group-count {
      font-size: 11px;
      font-weight: 600;
      background: var(--color-surface-container-high);
      color: var(--color-text-muted);
      padding: 1px 7px;
      border-radius: var(--radius-full);
    }

    /* ── Category rows ─────────────────────────────────────────────────────── */
    .category-list { display: flex; flex-direction: column; gap: 6px; }

    .category-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 14px;
      border-radius: var(--radius-md);
      background: var(--color-surface-container);
      border: 1px solid var(--color-outline-variant);
      transition: border-color 150ms ease;
    }
    .category-row:hover { border-color: var(--color-outline); }
    .category-row.is-default { opacity: 0.7; }

    .system-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 11px;
      font-weight: 500;
      color: var(--color-text-muted);
      flex-shrink: 0;
    }

    .row-actions { display: flex; gap: 4px; flex-shrink: 0; }

    .action-btn {
      background: transparent;
      border: 1px solid transparent;
      color: var(--color-text-muted);
      border-radius: var(--radius);
      width: 28px;
      height: 28px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 150ms ease, color 150ms ease, border-color 150ms ease;
    }
    .action-btn:hover {
      background: var(--color-surface-container-high);
      color: var(--color-on-surface);
      border-color: var(--color-outline-variant);
    }
    .action-btn--danger:hover {
      background: rgba(255, 180, 171, 0.08);
      color: var(--color-error);
      border-color: rgba(255, 180, 171, 0.25);
    }

    /* ── States ─────────────────────────────────────────────────────────────── */
    .skeleton-group { margin-bottom: 28px; }
    .skeleton-label {
      height: 13px; width: 72px;
      background: var(--color-surface-container-high);
      border-radius: 4px; margin-bottom: 10px;
      animation: pulse 1.6s ease-in-out infinite;
    }
    .skeleton-row {
      height: 46px;
      background: var(--color-surface-container);
      border: 1px solid var(--color-outline-variant);
      border-radius: var(--radius-md);
      margin-bottom: 6px;
      animation: pulse 1.6s ease-in-out infinite;
    }
    @keyframes pulse { 0%, 100% { opacity: 0.55; } 50% { opacity: 0.25; } }

    .error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 56px 0;
      color: var(--color-error);
      text-align: center;
    }
    .error-state p { margin: 0; font-size: 14px; color: var(--color-text-muted); }

    .btn-retry {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: transparent;
      border: 1px solid var(--color-outline-variant);
      color: var(--color-on-surface-variant);
      border-radius: var(--radius);
      padding: 6px 14px;
      font-size: 13px;
      font-family: var(--font-sans);
      cursor: pointer;
      transition: background 150ms;
    }
    .btn-retry:hover { background: var(--color-surface-container-high); }

    /* ── Backdrop ────────────────────────────────────────────────────────────── */
    .backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.6);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 200;
      backdrop-filter: blur(3px);
      padding: 16px;
    }

    /* ── Modal shell ─────────────────────────────────────────────────────────── */
    .modal {
      background: rgba(11, 51, 44, 0.92);
      backdrop-filter: blur(16px);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: var(--radius-lg);
      width: 100%;
      max-width: 480px;
      max-height: 90dvh;
      overflow-y: auto;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
    }

    .modal-sm { max-width: 360px; }

    .modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 20px 24px 16px;
      border-bottom: 1px solid rgba(60, 74, 70, 0.4);
    }

    .modal-header h2 {
      font-size: 16px;
      font-weight: 700;
      color: var(--color-on-surface);
      margin: 0;
      letter-spacing: -0.01em;
    }

    .close-btn {
      background: transparent;
      border: none;
      color: var(--color-text-muted);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 28px;
      height: 28px;
      border-radius: var(--radius);
      transition: background 150ms, color 150ms;
      flex-shrink: 0;
    }
    .close-btn:hover {
      background: rgba(255, 255, 255, 0.06);
      color: var(--color-on-surface);
    }

    /* ── Preview strip ───────────────────────────────────────────────────────── */
    .preview-strip {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 24px;
      background: rgba(23, 34, 33, 0.6);
      border-bottom: 1px solid rgba(60, 74, 70, 0.3);
    }

    .preview-label {
      font-size: 11px;
      font-weight: 600;
      color: var(--color-text-muted);
      flex-shrink: 0;
    }

    /* ── Modal body ──────────────────────────────────────────────────────────── */
    .modal-body { padding: 20px 24px 0; }

    /* ── Field ───────────────────────────────────────────────────────────────── */
    .field { margin-bottom: 20px; }

    .field-label {
      display: block;
      font-size: 11px;
      font-weight: 600;
      color: var(--color-text-muted);
      text-transform: uppercase;
      letter-spacing: 0.07em;
      margin-bottom: 10px;
    }

    .field-error { font-size: 11px; color: var(--color-error); margin-top: 4px; display: block; }

    /* ── Underline input ─────────────────────────────────────────────────────── */
    .input-line {
      width: 100%;
      box-sizing: border-box;
      background: var(--color-surface-container-lowest);
      border: none;
      border-bottom: 2px solid var(--color-outline-variant);
      border-radius: var(--radius) var(--radius) 0 0;
      color: var(--color-on-surface);
      padding: 11px 14px;
      font-size: 14px;
      font-family: var(--font-sans);
      outline: none;
      transition: border-color 200ms ease;
    }
    .input-line:focus { border-bottom-color: var(--color-primary); }
    .input-line::placeholder { color: var(--color-outline); }

    /* ── Type segmented control ──────────────────────────────────────────────── */
    .type-seg {
      display: flex;
      gap: 3px;
      padding: 3px;
      background: var(--color-surface-container-lowest);
      border: 1px solid rgba(60, 74, 70, 0.3);
      border-radius: var(--radius);
    }

    .type-seg-btn {
      flex: 1;
      padding: 8px 10px;
      border-radius: calc(var(--radius) - 2px);
      border: none;
      font-size: 13px;
      font-weight: 600;
      font-family: var(--font-sans);
      cursor: pointer;
      transition: background 180ms ease, color 180ms ease;
      color: var(--color-on-surface-variant);
      background: transparent;
    }
    .type-seg-btn.active {
      background: var(--color-primary-container);
      color: var(--color-on-primary);
    }

    /* ── Icon grid ───────────────────────────────────────────────────────────── */
    .icon-grid {
      display: grid;
      grid-template-columns: repeat(5, 1fr);
      gap: 8px;
    }

    .icon-btn {
      aspect-ratio: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--color-surface-container);
      border: 1px solid rgba(60, 74, 70, 0.5);
      border-radius: var(--radius);
      color: var(--color-on-surface-variant);
      cursor: pointer;
      transition: border-color 150ms, color 150ms, background 150ms;
    }
    .icon-btn:hover {
      border-color: rgba(82, 234, 205, 0.35);
      color: var(--color-on-surface);
    }
    .icon-btn.active {
      border: 2px solid var(--color-primary);
      color: var(--color-primary);
      background: rgba(82, 234, 205, 0.07);
    }

    /* ── Color row ───────────────────────────────────────────────────────────── */
    .color-row { display: flex; gap: 10px; flex-wrap: wrap; }

    .color-dot {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      border: 2px solid transparent;
      cursor: pointer;
      padding: 0;
      outline: 2px solid transparent;
      outline-offset: 2px;
      transition: outline-color 150ms, transform 150ms;
    }
    .color-dot:hover { transform: scale(1.15); }
    .color-dot.active { outline-color: var(--color-primary); }

    /* ── Form error ──────────────────────────────────────────────────────────── */
    .form-error {
      font-size: 13px;
      color: var(--color-error);
      margin: 0 0 4px;
      padding: 10px 12px;
      background: rgba(147, 0, 10, 0.12);
      border: 1px solid var(--color-error-container);
      border-radius: var(--radius);
    }

    /* ── Modal footer ────────────────────────────────────────────────────────── */
    .modal-footer {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 24px;
      background: rgba(44, 55, 54, 0.18);
      border-top: 1px solid rgba(60, 74, 70, 0.4);
      margin-top: 20px;
    }
    .modal-footer--sm { margin-top: 0; justify-content: center; }

    .btn-cancel {
      flex: 1;
      padding: 10px 20px;
      background: transparent;
      border: none;
      color: var(--color-on-surface-variant);
      font-size: 14px;
      font-weight: 600;
      font-family: var(--font-sans);
      cursor: pointer;
      border-radius: var(--radius);
      transition: color 150ms, background 150ms;
    }
    .btn-cancel:hover { color: var(--color-on-surface); background: rgba(255,255,255,0.04); }

    .btn-confirm {
      flex: 1.5;
      padding: 10px 24px;
      background: var(--color-primary);
      color: var(--color-on-primary);
      border: none;
      border-radius: var(--radius-lg);
      font-size: 14px;
      font-weight: 700;
      font-family: var(--font-sans);
      cursor: pointer;
      transition: filter 150ms;
      box-shadow: 0 4px 14px rgba(82, 234, 205, 0.18);
    }
    .btn-confirm:hover { filter: brightness(1.1); }
    .btn-confirm:disabled { opacity: 0.45; cursor: not-allowed; filter: none; }

    /* ── Delete confirm modal ────────────────────────────────────────────────── */
    .delete-icon-wrap {
      width: 42px; height: 42px;
      background: var(--color-error-container);
      color: var(--color-error);
      border-radius: var(--radius-lg);
      display: flex; align-items: center; justify-content: center;
      margin: 20px auto 14px;
    }
    .delete-title {
      font-size: 15px; font-weight: 600;
      color: var(--color-on-surface);
      margin: 0 0 8px;
      text-align: center;
    }
    .delete-body {
      font-size: 13px; color: var(--color-text-muted);
      margin: 0 0 4px; line-height: 1.55;
      text-align: center;
      padding: 0 24px;
    }

    .btn-danger {
      padding: 10px 20px;
      background: var(--color-error-container);
      color: var(--color-error);
      border: none;
      border-radius: var(--radius);
      font-weight: 700;
      font-size: 14px;
      font-family: var(--font-sans);
      cursor: pointer;
      transition: filter 150ms;
    }
    .btn-danger:hover { filter: brightness(1.12); }
    .btn-danger:disabled { opacity: 0.45; cursor: not-allowed; filter: none; }

    /* ── Reduced motion ─────────────────────────────────────────────────────── */
    @media (prefers-reduced-motion: reduce) {
      *, *::before, *::after { transition-duration: 0ms !important; animation: none !important; }
    }
  `],
})
export class CategoriesComponent implements OnInit {
  private readonly categoryService = inject(CategoryService);

  readonly availableIcons = AVAILABLE_ICONS;
  readonly colorPresets   = COLOR_PRESETS;
  readonly typeOptions    = TYPE_OPTIONS;

  categories   = signal<CategoryResponse[]>([]);
  loading      = signal(false);
  error        = signal<string | null>(null);
  modalOpen    = signal(false);
  editingId    = signal<string | null>(null);
  submitting   = signal(false);
  formError    = signal<string | null>(null);
  deleteTarget = signal<CategoryResponse | null>(null);

  groups = computed(() => {
    const cats = this.categories();
    return [
      { type: 'INCOME',  label: 'Ingresos',               items: cats.filter(c => c.type === 'INCOME') },
      { type: 'EXPENSE', label: 'Egresos',                items: cats.filter(c => c.type === 'EXPENSE') },
      { type: 'BOTH',    label: 'Transferencias y otros',  items: cats.filter(c => c.type === 'BOTH') },
    ].filter(g => g.items.length > 0);
  });

  form = new FormGroup({
    name:            new FormControl('',         { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    type:            new FormControl<CategoryType>('EXPENSE', { nonNullable: true }),
    icon:            new FormControl('circle',   { nonNullable: true }),
    color:           new FormControl('#26CDB2',  { nonNullable: true }),
    estimatedAmount: new FormControl<number | null>(null),
  });

  // toSignal converts valueChanges Observable into an Angular signal so computed()
  // can track form mutations — plain FormControl.value is not reactive.
  private readonly formValues = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  previewCategory = computed<CategoryResponse>(() => {
    const v = this.formValues();
    return {
      id:              'preview',
      name:            (v.name  as string)?.trim() || 'Mi categoría',
      icon:            (v.icon  as string)         || 'circle',
      color:           (v.color as string)         || '#26CDB2',
      type:            (v.type  as CategoryType)   || 'EXPENSE',
      isDefault:       false,
      estimatedAmount: null,
    };
  });

  ngOnInit(): void {
    this.loadCategories();
  }

  loadCategories(): void {
    this.loading.set(true);
    this.error.set(null);
    this.categoryService.getCategories().subscribe({
      next:  cats => { this.categories.set(cats); this.loading.set(false); },
      error: ()   => { this.error.set('No se pudieron cargar las categorías'); this.loading.set(false); },
    });
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form.reset({ name: '', type: 'EXPENSE', icon: 'circle', color: '#26CDB2', estimatedAmount: null });
    this.formError.set(null);
    this.modalOpen.set(true);
  }

  openEdit(cat: CategoryResponse): void {
    this.editingId.set(cat.id);
    this.form.setValue({ name: cat.name, type: cat.type, icon: cat.icon, color: cat.color, estimatedAmount: cat.estimatedAmount ?? null });
    this.formError.set(null);
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
    this.editingId.set(null);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.formError.set(null);

    const req = this.form.getRawValue() as CategoryRequest;
    const id  = this.editingId();
    const op  = id
      ? this.categoryService.updateCategory(id, req)
      : this.categoryService.createCategory(req);

    op.subscribe({
      next: saved => {
        this.submitting.set(false);
        this.closeModal();
        if (id) {
          this.categories.update(cats => cats.map(c => c.id === id ? saved : c));
        } else {
          this.categories.update(cats => [...cats, saved]);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.formError.set(err.error?.message ?? 'Ocurrió un error al guardar');
      },
    });
  }

  requestDelete(cat: CategoryResponse): void { this.deleteTarget.set(cat); }
  cancelDelete():                        void { this.deleteTarget.set(null); }

  confirmDelete(): void {
    const cat = this.deleteTarget();
    if (!cat || this.submitting()) return;
    this.submitting.set(true);
    this.categoryService.deleteCategory(cat.id).subscribe({
      next: () => {
        this.categories.update(cats => cats.filter(c => c.id !== cat.id));
        this.deleteTarget.set(null);
        this.submitting.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(err.error?.message ?? 'No se pudo eliminar la categoría');
        this.deleteTarget.set(null);
      },
    });
  }
}
