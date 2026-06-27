export type InvestmentAssetType = 'FCI' | 'FCI_CUOTAPARTES' | 'PLAZO_FIJO' | 'LETRA' | 'BONO' | 'ON';

export type InvestmentMovementType = 'SUSCRIPCION' | 'RESCATE' | 'REVALUO';

export interface InvestmentMovement {
  id:           string;
  movementDate: string;   // yyyy-MM-dd
  type:         InvestmentMovementType;
  amount:       number;
  units:        number | null;   // cuotapartes; presente solo en FCI_CUOTAPARTES
  createdAt:    string;
}

export interface InvestmentMovementRequest {
  movementDate:  string;
  type:          InvestmentMovementType;
  amount:        number;
  units?:        number;         // requerido para FCI_CUOTAPARTES
  pricePerUnit?: number;         // precio por cuotaparte/nominal al momento del movimiento
}

export interface InvestmentValuation {
  id:            string;
  valuationDate: string;
  pricePerUnit:  number;
  source:        'MANUAL' | 'ARGENTINADATOS' | 'DOCTA_CAPITAL';
  createdAt:     string;
}

export interface InvestmentValuationRequest {
  valuationDate: string;
  pricePerUnit:  number;
}

export interface InvestmentResponse {
  id:           string;
  name:         string;
  type:         InvestmentAssetType;
  currency:     'ARS' | 'USD';
  principal:    number;       // BigDecimal → number en JSON
  purchaseDate: string;       // yyyy-MM-dd
  maturityDate: string | null;
  tna:          number;       // porcentaje, ej. 65.00
  accountId:    string | null;
  accountName:  string | null;
  autoTrack:    boolean;
  externalId:   string | null;
  createdAt:    string;
  updatedAt:    string;
  movements:    InvestmentMovement[];
  valuations:   InvestmentValuation[];
}

export interface InvestmentRequest {
  name:         string;
  type:         InvestmentAssetType;
  currency:     'ARS' | 'USD';
  principal:    number;
  purchaseDate: string;
  maturityDate: string | null;
  tna:          number;
  accountId:    string | null;
  autoTrack:    boolean;
  externalId:   string | null;
}

export const ASSET_TYPE_LABELS: Record<InvestmentAssetType, string> = {
  FCI:             'Fondo Money Market',
  FCI_CUOTAPARTES: 'Fondo Cuotapartes',
  PLAZO_FIJO:      'Plazo fijo',
  LETRA:           'Letra capitalizable',
  BONO:            'Bono soberano',
  ON:              'Obligación Negociable',
};

export interface MarketApiStatus {
  fciSnapshotsTotal: number;
  fciLastSync:       string | null;   // yyyy-MM-dd
  ppiConfigured:     boolean;
}

export interface FciFundOption {
  fondo:     string;
  categoria: string;
  vcp:       number;
  fecha:     string;
}

export interface InstrumentOption {
  ticker:    string;
  nombre:    string;
  tipo:      string;
  lastPrice: number | null;
  priceDate: string | null;
}

export const ASSET_TINTS = [
  'var(--color-primary)',
  'var(--color-primary-fixed-dim)',
  'var(--color-secondary)',
  'var(--color-tertiary)',
];
