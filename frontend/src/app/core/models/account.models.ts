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
  /** Derivado: true si hay un activo FCI (Cuenta Remunerada) vinculado a esta cuenta. No editable. */
  remunerada:         boolean;
  /** TNA del activo FCI vinculado más reciente (null si no hay ninguno). No editable. */
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
  includeInCashflow?: boolean;
}
