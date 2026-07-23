import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountBalanceCardComponent } from './account-balance-card.component';
import { AccountResponse } from '../../../core/models/account.models';
import { CurrencyService } from '../../../core/services/currency.service';

function buildAccount(overrides: Partial<AccountResponse> = {}): AccountResponse {
  return {
    id:              '1',
    name:            'Cuenta Galicia',
    kind:            'Banco',
    detail:          'Caja de Ahorro $',
    ccy:             'ARS',
    balance:         100000,
    computedBalance: 113500,
    remunerada:      false,
    tna:             null,
    createdAt:       '2025-01-01T00:00:00Z',
    updatedAt:       '2025-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('AccountBalanceCardComponent', () => {
  let fixture: ComponentFixture<AccountBalanceCardComponent>;
  let component: AccountBalanceCardComponent;

  function create(account: AccountResponse): void {
    fixture = TestBed.createComponent(AccountBalanceCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('account', account);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountBalanceCardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('muestra el nombre de la cuenta', () => {
    create(buildAccount({ name: 'Cuenta Galicia' }));
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Cuenta Galicia');
  });

  it('muestra computedBalance como saldo calculado cuando está disponible', () => {
    create(buildAccount({ balance: 100000, computedBalance: 113500 }));
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('113.500');
  });

  it('muestra balance cuando computedBalance es null', () => {
    create(buildAccount({ balance: 100000, computedBalance: null }));
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('100.000');
  });

  it('netMovements() calcula la diferencia entre computedBalance y balance', () => {
    create(buildAccount({ balance: 100000, computedBalance: 113500 }));
    expect(component.netMovements()).toBe(13500);
  });

  it('netMovements() es 0 cuando computedBalance es null', () => {
    create(buildAccount({ balance: 100000, computedBalance: null }));
    expect(component.netMovements()).toBe(0);
  });

  it('muestra badge USD en color acento alternativo', () => {
    create(buildAccount({ ccy: 'USD', computedBalance: 5000 }));
    const el: HTMLElement = fixture.nativeElement;
    const badge = el.querySelector('.balance-card__ccy-badge');
    expect(badge?.classList).toContain('balance-card__ccy-badge--usd');
    expect(badge?.textContent?.trim()).toBe('USD');
  });

  it('fmtAmount formatea ARS sin decimales cuando moneda seleccionada es ARS', () => {
    create(buildAccount());
    expect(component.fmtAmount(150000, 'ARS')).toBe('$ 150.000');
  });

  it('fmtAmount retorna guion cuando hay cross-conversion sin cotizacion cargada', () => {
    create(buildAccount());
    // selected=ARS, fromCcy=USD → rate null → '–'
    expect(component.fmtAmount(1500.5, 'USD')).toBe('–');
  });

  it('fmtAmount formatea USD nativo cuando moneda seleccionada es USD', () => {
    create(buildAccount());
    const currencyService = TestBed.inject(CurrencyService);
    currencyService.selected.set('USD');
    // selected=USD, fromCcy=USD → no-op → 'US$ 1.500,50'
    expect(component.fmtAmount(1500.5, 'USD')).toBe('US$ 1.500,50');
  });
});
