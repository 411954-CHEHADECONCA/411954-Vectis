export type MovementType = 'INCOME' | 'EXPENSE';
export type MovementCcy = 'ARS' | 'USD';

export interface MovementResponse {
  id: string;
  type: MovementType;
  description: string;
  amount: number;
  ccy: MovementCcy;
  categoryId: string | null;
  categoryName: string | null;
  categoryIcon: string | null;
  categoryColor: string | null;
  accountId: string | null;
  accountName: string | null;
  cardId: string | null;
  cardName: string | null;
  transactionDate: string; // ISO date (yyyy-MM-dd)
  dueDate: string;         // ISO date (yyyy-MM-dd)
  installment: boolean;
  installmentNumber: number | null;
  totalInstallments: number | null;
  installmentGroupId: string | null;
  createdAt: string;
}

export interface MovementRequest {
  description: string;
  amount: number;
  ccy: MovementCcy;
  type: MovementType;
  categoryId: string | null;
  accountId: string | null;
  cardId: string | null;
  transactionDate: string; // ISO date (yyyy-MM-dd)
  installments: number;
}

export interface MovementSummary {
  totalIncome: number;
  totalExpense: number;
  net: number;
  count: number;
}

/** Filtros del listado/resumen de movimientos. */
export interface MovementFilters {
  from: string;
  to: string;
  type?: MovementType;
  categoryId?: string;
  q?: string;
  page?: number;
  size?: number;
}
