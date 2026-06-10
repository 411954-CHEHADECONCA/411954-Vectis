import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DashboardViewComponent } from './dashboard-view.component';
import { CurrencyService } from '../../../core/services/currency.service';

describe('DashboardViewComponent', () => {
  let fixture: ComponentFixture<DashboardViewComponent>;
  let component: DashboardViewComponent;
  let currencyService: CurrencyService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardViewComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardViewComponent);
    component = fixture.componentInstance;
    currencyService = TestBed.inject(CurrencyService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders four stat cards', () => {
    const cards = fixture.nativeElement.querySelectorAll('.stat-card');
    expect(cards.length).toBe(4);
  });

  it('formats totalAssets in ARS by default', () => {
    const value = fixture.nativeElement.querySelector('.stat-card__value');
    expect(value.textContent).toContain('16.777.724');
  });

  it('toggles symbol to US$ when currency changes', () => {
    currencyService.toggle();
    fixture.detectChanges();
    const symbols = fixture.nativeElement.querySelectorAll('.stat-card__currency');
    expect(symbols[0].textContent).toContain('US$');
  });

  it('fmt returns ARS formatted string by default', () => {
    expect(component.fmt(1000000)).toBe('1.000.000');
  });

  it('fmt converts to USD when selected', () => {
    currencyService.selected.set('USD');
    const result = component.fmt(1167400);
    expect(result).toContain('1.000,00');
  });
});
