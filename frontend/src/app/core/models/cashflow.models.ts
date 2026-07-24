/**
 * Monto desglosado por moneda. Todo agregado del cashflow es bimonetario (ARS y USD conviven en la
 * misma cuenta de usuario): ningún total puede colapsarse a un único número sin asumir implícitamente
 * que todo es ARS — ese fue exactamente el bug de amortizaciones en USD apareciendo como pesos.
 * Espeja `PortfolioByCurrency` (ver `PortfolioSummaryService`): la conversión ARS↔USD la resuelve
 * el consumidor con `CurrencyService`, nunca este tipo.
 *
 * Serializado por Jackson como número JSON (BigDecimal sin @JsonSerialize a string), verificado
 * contra InvestmentControllerTest (jsonPath con comparación numérica tipada).
 */
export interface MoneyByCcy {
  ars: number;
  usd: number;
}

export interface CashflowAccountBalance {
  accountId: string;
  name:      string;
  ccy:       string;
  balance:   string;
}

export interface CashflowBalanceSection {
  total:    MoneyByCcy;
  accounts: CashflowAccountBalance[];
}

export interface CashflowCategoryRow {
  categoryId:   string | null;
  name:         string;
  icon:         string;
  color:        string;
  amount:       MoneyByCcy;
  pctOfTotal:   string;
  budgeted:     MoneyByCcy | null;
  pctOfBudget:  string | null;
}

export interface CashflowFlowSection {
  total:          MoneyByCcy;
  totalBudgeted:  MoneyByCcy;
  byCategory:     CashflowCategoryRow[];
}

export interface CashflowSubtotal {
  balance:          MoneyByCcy;
  operativeResult:  MoneyByCcy;
  savingRatePct:    string;
}

export interface CashflowInvestmentRow {
  name:   string;
  icon:   string;
  color:  string;
  amount: MoneyByCcy;
  teaPct: string | null;
}

export interface CashflowInvestmentSection {
  total:           MoneyByCcy;
  pctOfPreBalance: string | null;
  instruments:     CashflowInvestmentRow[];
}

export interface CashflowResponse {
  year:                  number;
  month:                 number;
  periodLabel:           string;
  monthShort:            string;
  status:                'cerrado' | 'curso' | 'abierto' | 'proyectado';
  isProjection:          boolean;
  recurringMaterialized: boolean;
  needsConfirmation:     boolean;
  hasLiquidityDeficit:   boolean;
  liquidityDeficit:      string;
  openingBalance:       CashflowBalanceSection;
  income:               CashflowFlowSection;
  expenses:             CashflowFlowSection;
  preInvestmentBalance: CashflowSubtotal;
  investments:          CashflowInvestmentSection;
  closingBalance:       CashflowBalanceSection;
  oficialRateAtPeriod:  string | null;
  earliestNavigableYear:  number;
  earliestNavigableMonth: number;
}
