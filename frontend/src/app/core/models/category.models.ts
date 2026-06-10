export type CategoryType = 'INCOME' | 'EXPENSE' | 'BOTH';

export interface CategoryResponse {
  id: string;
  name: string;
  icon: string;
  color: string;
  type: CategoryType;
  isDefault: boolean;
  estimatedAmount: number | null;
}

export interface CategoryRequest {
  name: string;
  icon: string;
  color: string;
  type: CategoryType;
  estimatedAmount: number | null;
}
