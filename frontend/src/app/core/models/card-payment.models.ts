export interface ExtraChargeItem {
  description: string;
  amount: number;
  categoryId: string | null;
}

export interface CardPaymentRequest {
  accountId: string;
  paidDate: string;        // ISO date (yyyy-MM-dd)
  paidAmount: number;
  periodYear: number;
  periodMonth: number;
  paymentCategoryId: string | null;
  extraCharges: ExtraChargeItem[];
}

export interface CardPaymentResponse {
  cuotasMarcadas: number;
  paymentTransactionId: string;
  extraChargesCreated: number;
}

export interface CardPaymentCuotaItem {
  description: string;
  installmentLabel: string | null;
  amount: number;
  dueDate: string; // ISO date
  categoryIcon: string | null;
  categoryColor: string | null;
}

export interface CardPaymentExtraItem {
  description: string;
  amount: number;
  categoryIcon: string | null;
  categoryColor: string | null;
}

export interface CardPaymentHistory {
  paymentTransactionId: string;
  cardId: string;
  cardName: string;
  periodYear: number;
  periodMonth: number;
  paidDate: string; // ISO date
  paidAmount: number;
  ccy: 'ARS' | 'USD';
  cuotas: CardPaymentCuotaItem[];
  extraCharges: CardPaymentExtraItem[];
}
