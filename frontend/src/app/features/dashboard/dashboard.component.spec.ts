import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { of, throwError, BehaviorSubject } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../../core/services/auth.service';
import { CurrencyService } from '../../core/services/currency.service';
import { InvestmentService } from '../../core/services/investment.service';
import { UserInfo } from '../../core/models/auth.models';
import { ExchangeRateResponse } from '../../core/models/macro.models';

const MOCK_USER: UserInfo = {
  id: 'user-1', email: 'test@vectis.com', fullName: 'Test User',
};

const MOCK_RATE: ExchangeRateResponse = {
  rateType: 'OFICIAL', buy: '1060.0000', sell: '1062.5000', rateDate: '2026-06-22', source: 'dolarapi.com',
};

function buildSpies() {
  const authSpy = jasmine.createSpyObj<AuthService>('AuthService', ['logout'], {
    currentUser$: new BehaviorSubject<UserInfo | null>(MOCK_USER),
  });

  const currencySpy = jasmine.createSpyObj<CurrencyService>('CurrencyService', ['toggle'], {
    selected:    signal<'ARS' | 'USD'>('ARS'),
    oficialRate: signal<ExchangeRateResponse | null>(MOCK_RATE),
  });

  const investmentSpy = jasmine.createSpyObj<InvestmentService>('InvestmentService', ['refreshMarketData']);
  investmentSpy.refreshMarketData.and.returnValue(of({
    sources: [
      { source: 'mep', status: 'refreshed', lastUpdate: '2026-07-07' },
      { source: 'oficial', status: 'upToDate', lastUpdate: '2026-07-07' },
      { source: 'fci', status: 'refreshed', lastUpdate: '2026-07-07' },
      { source: 'ppi', status: 'notConfigured', lastUpdate: null },
    ],
  }));

  return { authSpy, currencySpy, investmentSpy };
}

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let component: DashboardComponent;
  let investmentSpy: jasmine.SpyObj<InvestmentService>;

  async function setup(spies = buildSpies()) {
    investmentSpy = spies.investmentSpy;

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService,       useValue: spies.authSpy     },
        { provide: CurrencyService,   useValue: spies.currencySpy },
        { provide: InvestmentService, useValue: spies.investmentSpy },
      ],
    }).compileComponents();

    fixture   = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('se crea correctamente', async () => {
    await setup();
    expect(component).toBeTruthy();
  });

  // ── Refresh silencioso de datos de mercado (login fresco / reingreso) ───────

  it('ngOnInit dispara refreshMarketData(false) de forma silenciosa', async () => {
    await setup();
    expect(investmentSpy.refreshMarketData).toHaveBeenCalledWith(false);
  });

  it('no rompe el shell si el refresh de mercado falla (catchError silencioso)', async () => {
    const spies = buildSpies();
    spies.investmentSpy.refreshMarketData.and.returnValue(throwError(() => new Error('network error')));

    await expectAsync(setup(spies)).toBeResolved();
    expect(component).toBeTruthy();
    expect(spies.investmentSpy.refreshMarketData).toHaveBeenCalledWith(false);
  });
});
