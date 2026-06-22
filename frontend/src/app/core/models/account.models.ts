export type AccountKind = 'Banco' | 'Billetera' | 'Efectivo';
export type AccountCcy  = 'ARS' | 'USD';

export interface AccountResponse {
  id:                 string;
  name:               string;
  kind:               AccountKind;
  detail:             string | null;
  ccy:                AccountCcy;
  balance:            number;
  computedBalance:    number | null;
  remunerada:         boolean;
  tna:                number | null;
  includeInCashflow?: boolean;
  createdAt:          string;
  updatedAt:          string;
}

export interface AccountBalanceResponse {
  accountId:       string;
  name:            string;
  ccy:             AccountCcy;
  openingBalance:  number;
  computedBalance: number;
  asOf:            string;
}

export interface AccountRequest {
  name:              string;
  kind:              AccountKind;
  detail:            string | null;
  ccy:               AccountCcy;
  balance:           number;
  remunerada:        boolean;
  tna:               number | null;
  includeInCashflow?: boolean;
}
