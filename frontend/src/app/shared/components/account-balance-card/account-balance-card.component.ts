import { Component, ChangeDetectionStrategy, input, computed, inject } from '@angular/core';
import {
  LucideBuilding2,
  LucideWallet,
  LucideBanknote,
  LucideTrendingUp,
  LucideTrendingDown,
} from '@lucide/angular';
import { AccountResponse } from '../../../core/models/account.models';
import { CurrencyService } from '../../../core/services/currency.service';

@Component({
  selector: 'app-account-balance-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideBuilding2, LucideWallet, LucideBanknote, LucideTrendingUp, LucideTrendingDown],
  template: `
    <div class="balance-card">
      <div class="balance-card__header">
        <div class="balance-card__icon-wrap">
          @switch (account().kind) {
            @case ('Banco')     { <svg lucideBuilding2 [size]="16" [strokeWidth]="2" /> }
            @case ('Billetera') { <svg lucideWallet    [size]="16" [strokeWidth]="2" /> }
            @default            { <svg lucideBanknote  [size]="16" [strokeWidth]="2" /> }
          }
        </div>
        <div class="balance-card__meta">
          <span class="balance-card__name">{{ account().name }}</span>
          @if (account().detail) {
            <span class="balance-card__detail">{{ account().detail }}</span>
          }
        </div>
        <span class="balance-card__ccy-badge" [class.balance-card__ccy-badge--usd]="account().ccy === 'USD'">
          {{ account().ccy }}
        </span>
      </div>

      <div class="balance-card__body">
        <div class="balance-card__amount-row">
          <span class="balance-card__label">Saldo calculado</span>
          <span class="balance-card__amount balance-card__amount--main">{{ fmtAmount(effectiveBalance(), account().ccy) }}</span>
        </div>
        <div class="balance-card__amount-row balance-card__amount-row--sub">
          <span class="balance-card__label">Saldo inicial</span>
          <span class="balance-card__amount balance-card__amount--sub">{{ fmtAmount(account().balance, account().ccy) }}</span>
        </div>
        @if (netMovements() !== 0) {
          <div class="balance-card__net" [class.balance-card__net--positive]="netMovements() > 0" [class.balance-card__net--negative]="netMovements() < 0">
            @if (netMovements() > 0) { <svg lucideTrendingUp  [size]="13" [strokeWidth]="2.5" /> }
            @if (netMovements() < 0) { <svg lucideTrendingDown [size]="13" [strokeWidth]="2.5" /> }
            <span>{{ netMovements() > 0 ? '+' : '' }}{{ fmtAmount(netMovements(), account().ccy) }} en movimientos</span>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .balance-card {
      background: var(--color-surface-raised, #1a2236);
      border: 1px solid var(--color-border, rgba(255,255,255,0.08));
      border-radius: 12px;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .balance-card__header {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .balance-card__icon-wrap {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      background: rgba(82, 234, 205, 0.12);
      border: 1px solid rgba(82, 234, 205, 0.2);
      display: flex;
      align-items: center;
      justify-content: center;
      color: #52eacd;
      flex-shrink: 0;
    }

    .balance-card__meta {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .balance-card__name {
      font-size: 14px;
      font-weight: 600;
      color: var(--color-text-primary, #f1f5f9);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .balance-card__detail {
      font-size: 12px;
      color: var(--color-text-muted, #64748b);
    }

    .balance-card__ccy-badge {
      font-size: 11px;
      font-weight: 700;
      padding: 2px 8px;
      border-radius: 9999px;
      background: rgba(82, 234, 205, 0.12);
      color: #52eacd;
      border: 1px solid rgba(82, 234, 205, 0.25);
      letter-spacing: 0.05em;
      flex-shrink: 0;
    }

    .balance-card__ccy-badge--usd {
      background: rgba(126, 200, 227, 0.12);
      color: #7ec8e3;
      border-color: rgba(126, 200, 227, 0.25);
    }

    .balance-card__body {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .balance-card__amount-row {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      gap: 8px;
    }

    .balance-card__amount-row--sub {
      opacity: 0.65;
    }

    .balance-card__label {
      font-size: 12px;
      color: var(--color-text-muted, #64748b);
    }

    .balance-card__amount {
      font-variant-numeric: tabular-nums;
      font-weight: 600;
    }

    .balance-card__amount--main {
      font-size: 18px;
      color: var(--color-text-primary, #f1f5f9);
    }

    .balance-card__amount--sub {
      font-size: 13px;
      color: var(--color-text-secondary, #94a3b8);
    }

    .balance-card__net {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;
      font-weight: 500;
      margin-top: 2px;
      font-variant-numeric: tabular-nums;
    }

    .balance-card__net--positive {
      color: #52eacd;
    }

    .balance-card__net--negative {
      color: #ffb4ab;
    }
  `],
})
export class AccountBalanceCardComponent {
  private readonly currencyService = inject(CurrencyService);

  account = input.required<AccountResponse>();

  effectiveBalance = computed(() => this.account().computedBalance ?? this.account().balance);
  netMovements     = computed(() => this.effectiveBalance() - this.account().balance);

  fmtAmount(value: number, ccy: 'ARS' | 'USD'): string {
    const converted = this.currencyService.convert(value, ccy);
    if (converted === null) return '–';
    const sel = this.currencyService.selected();
    if (sel === 'USD') {
      return `US$ ${converted.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    return `$ ${Math.round(converted).toLocaleString('es-AR')}`;
  }
}
