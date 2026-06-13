export interface CardFace {
  id: string;
  bank: string;
  network: string;
  last4: string;
  ccy: 'ARS' | 'USD';
  accent: string;
  creditLimit: number;
  consumido: number;
  nextClosingDate: string; // ISO date
  nextDueDate: string;     // ISO date
}

export interface InstallmentPlan {
  groupId: string;
  description: string;
  cardName: string;
  categoryIcon: string | null;
  categoryColor: string | null;
  currentNumber: number;
  totalInstallments: number;
  monthlyAmount: number;
  ccy: 'ARS' | 'USD';
}

export interface CardDue {
  cardId: string;
  cardName: string;
  dueDate: string; // ISO date
  amount: number;
}

export interface CardOverview {
  cards: CardFace[];
  totalDue: number;
  nextDueCardName: string | null;
  nextDueDate: string | null;
  nextDueAmount: number | null;
  cuotasActivas: InstallmentPlan[];
  vencimientos: CardDue[];
}

export interface CardMatrixConsumo {
  description: string;
  installmentLabel: string | null;
  categoryIcon: string | null;
  categoryColor: string | null;
  cellsByMonth: (number | null)[];
}

export interface CardMatrixCard {
  cardId: string;
  cardName: string;
  accent: string;
  consumos: CardMatrixConsumo[];
  subtotalsByMonth: number[];
}

export interface CardMatrix {
  months: string[]; // "YYYY-MM" keys
  cards: CardMatrixCard[];
  totalsByMonth: number[];
}
