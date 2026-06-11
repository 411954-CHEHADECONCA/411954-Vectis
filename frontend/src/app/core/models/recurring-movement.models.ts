export interface RecurringMovementResponse {
  id: string;
  description: string;
  amount: number;
  ccy: 'ARS' | 'USD';
  type: 'INCOME' | 'EXPENSE';
  categoryId: string | null;
  categoryName: string | null;
  categoryIcon: string | null;
  categoryColor: string | null;
  accountId: string | null;
  accountName: string | null;
  dayOfMonth: number;
  active: boolean;
  createdAt: string;
}

export interface RecurringMovementRequest {
  description: string;
  amount: number;
  ccy: 'ARS' | 'USD';
  type: 'INCOME' | 'EXPENSE';
  categoryId: string | null;
  accountId: string | null;
  dayOfMonth: number;
}
