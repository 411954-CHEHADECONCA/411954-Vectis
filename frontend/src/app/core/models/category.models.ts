export type CategoryType = 'INCOME' | 'EXPENSE' | 'BOTH';

export interface CategoryResponse {
  id: string;
  name: string;
  icon: string;
  color: string;
  type: CategoryType;
  isDefault: boolean;
}

export interface CategoryRequest {
  name: string;
  icon: string;
  color: string;
  type: CategoryType;
}
