export type AccountKind = 'Banco' | 'Billetera' | 'Efectivo';
export type AccountCcy  = 'ARS' | 'USD';

export interface AccountResponse {
  id:         string;
  name:       string;
  kind:       AccountKind;
  detail:     string | null;
  ccy:        AccountCcy;
  balance:    number;
  remunerada: boolean;
  tna:        number | null;
  createdAt:  string;
  updatedAt:  string;
}

export interface AccountRequest {
  name:       string;
  kind:       AccountKind;
  detail:     string | null;
  ccy:        AccountCcy;
  balance:    number;
  remunerada: boolean;
  tna:        number | null;
}
