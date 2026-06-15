export interface CashflowAccountBalance {
  accountId: string;
  name:      string;
  ccy:       string;
  balance:   string;
}

export interface CashflowBalanceSection {
  total:    string;
  accounts: CashflowAccountBalance[];
}

export interface CashflowCategoryRow {
  categoryId:   string | null;
  name:         string;
  icon:         string;
  color:        string;
  amount:       string;
  pctOfTotal:   string;
  budgeted:     string | null;
  pctOfBudget:  string | null;
}

export interface CashflowFlowSection {
  total:      string;
  byCategory: CashflowCategoryRow[];
}

export interface CashflowSubtotal {
  balance:          string;
  operativeResult:  string;
  savingRatePct:    string;
}

export interface CashflowInvestmentRow {
  name:   string;
  icon:   string;
  color:  string;
  amount: string;
  teaPct: string | null;
}

export interface CashflowInvestmentSection {
  total:           string;
  pctOfPreBalance: string | null;
  instruments:     CashflowInvestmentRow[];
}

export interface CashflowResponse {
  year:                 number;
  month:                number;
  periodLabel:          string;
  monthShort:           string;
  status:               'cerrado' | 'curso' | 'proyectado';
  isProjection:         boolean;
  openingBalance:       CashflowBalanceSection;
  income:               CashflowFlowSection;
  expenses:             CashflowFlowSection;
  preInvestmentBalance: CashflowSubtotal;
  investments:          CashflowInvestmentSection;
  closingBalance:       CashflowBalanceSection;
}
