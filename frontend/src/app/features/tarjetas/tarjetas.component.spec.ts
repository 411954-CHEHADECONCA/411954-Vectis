import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { TarjetasComponent } from './tarjetas.component';
import { CardProjectionService } from '../../core/services/card-projection.service';
import { CardMatrix, CardOverview } from '../../core/models/card-projection.models';

const OVERVIEW: CardOverview = {
  cards: [
    { id: 'c1', bank: 'Galicia', network: 'Visa', last4: '4821', ccy: 'ARS', accent: '#52eacd',
      creditLimit: 1500000, consumido: 612300, nextClosingDate: '2026-06-24', nextDueDate: '2026-07-02' },
    { id: 'c2', bank: 'Brubank', network: 'Mastercard', last4: '0937', ccy: 'USD', accent: '#9ed1c5',
      creditLimit: 1750000, consumido: 420000, nextClosingDate: '2026-06-20', nextDueDate: '2026-06-28' },
  ],
  totalDue: 1032300,
  nextDueCardName: 'Brubank ····0937', nextDueDate: '2026-06-28', nextDueAmount: 420000,
  cuotasActivas: [
    { groupId: 'g1', description: 'Notebook', cardName: 'Galicia ····4821', categoryIcon: 'monitor',
      categoryColor: '#52eacd', currentNumber: 3, totalInstallments: 6, monthlyAmount: 102050, ccy: 'ARS' },
  ],
  vencimientos: [
    { cardId: 'c2', cardName: 'Brubank ····0937', dueDate: '2026-06-28', amount: 420000 },
    { cardId: 'c1', cardName: 'Galicia ····4821', dueDate: '2026-07-02', amount: 612300 },
  ],
};

const MATRIX: CardMatrix = {
  months: ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'],
  cards: [
    {
      cardId: 'c1', cardName: 'Galicia ····4821', accent: '#52eacd',
      consumos: [
        { description: 'Notebook', installmentLabel: '3/6', categoryIcon: 'monitor', categoryColor: '#52eacd',
          cellsByMonth: [102050, 102050, 102050, null, null, null] },
      ],
      subtotalsByMonth: [102050, 102050, 102050, 0, 0, 0],
    },
  ],
  totalsByMonth: [102050, 102050, 102050, 0, 0, 0],
};

describe('TarjetasComponent', () => {
  let fixture: ComponentFixture<TarjetasComponent>;
  let component: TarjetasComponent;
  let serviceSpy: jasmine.SpyObj<CardProjectionService>;

  beforeEach(async () => {
    serviceSpy = jasmine.createSpyObj<CardProjectionService>('CardProjectionService', ['getOverview', 'getMatrix']);
    serviceSpy.getOverview.and.returnValue(of(OVERVIEW));
    serviceSpy.getMatrix.and.returnValue(of(MATRIX));

    await TestBed.configureTestingModule({
      imports: [TarjetasComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CardProjectionService, useValue: serviceSpy },
      ],
    }).compileComponents();

    fixture   = TestBed.createComponent(TarjetasComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads overview and matrix on init', () => {
    expect(component).toBeTruthy();
    expect(serviceSpy.getOverview).toHaveBeenCalled();
    expect(serviceSpy.getMatrix).toHaveBeenCalledWith(6);
    expect(component.overview()?.cards.length).toBe(2);
  });

  it('renders two card faces and shows stat cards with deuda total', () => {
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.card-face').length).toBe(2);
    expect(el.textContent).toContain('Galicia');
    // deuda total = 1.032.300 debe aparecer en alguna stat card
    expect(el.textContent).toContain('1.032.300');
    // stats row: 3 tarjetas de resumen
    expect(el.querySelectorAll('.stat-card').length).toBe(3);
  });

  it('switches tabs', () => {
    expect(component.activeTab()).toBe('cuotas');
    component.setTab('matriz');
    fixture.detectChanges();
    expect(component.activeTab()).toBe('matriz');
    expect((fixture.nativeElement as HTMLElement).querySelector('table.matrix')).toBeTruthy();
  });

  it('expands all cards by default and toggles a card', () => {
    expect(component.isExpanded('c1')).toBeTrue();
    component.toggleCard('c1');
    expect(component.isExpanded('c1')).toBeFalse();
    component.toggleCard('c1');
    expect(component.isExpanded('c1')).toBeTrue();
  });

  it('changing months reloads the matrix', () => {
    serviceSpy.getMatrix.calls.reset();
    component.setMonths(12);
    expect(component.months()).toBe(12);
    expect(serviceSpy.getMatrix).toHaveBeenCalledWith(12);
  });

  it('fmtCell renders a dash for null or zero', () => {
    expect(component.fmtCell(null)).toBe('—');
    expect(component.fmtCell(0)).toBe('—');
    expect(component.fmtCell(102050)).toContain('102.050');
  });

  it('renders the matrix total footer when on matriz tab', () => {
    component.setTab('matriz');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const footer = el.querySelector('.mx-total');
    expect(footer?.textContent).toContain('TOTAL');
  });
});
