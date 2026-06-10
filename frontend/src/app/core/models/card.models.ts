export type CardNetwork = 'Visa' | 'Mastercard' | 'Amex';
export type CardCcy     = 'ARS' | 'USD';

export interface CardResponse {
  id:          string;
  bank:        string;
  network:     CardNetwork;
  last4:       string;
  ccy:         CardCcy;
  creditLimit: number;
  closingDay:  number;
  dueDay:      number;
  accent:      string;
  createdAt:   string;
  updatedAt:   string;
}

export interface CardRequest {
  bank:        string;
  network:     CardNetwork;
  last4:       string;
  ccy:         CardCcy;
  creditLimit: number;
  closingDay:  number;
  dueDay:      number;
  accent:      string;
}
