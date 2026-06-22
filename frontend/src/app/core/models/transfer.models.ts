export interface TransferRequest {
  sourceAccountId: string;
  destAccountId:   string;
  sourceAmount:    number;
  destAmount?:     number;    // solo si cuentas difieren en ccy
  transactionDate: string;    // ISO date (yyyy-MM-dd)
  description?:    string;
}

export interface TransferResponse {
  transferGroupId:     string;
  sourceTransactionId: string;
  destTransactionId:   string;
  sourceAccountId:     string;
  sourceAccountName:   string;
  destAccountId:       string;
  destAccountName:     string;
  sourceAmount:        number;
  sourceCcy:           string;
  destAmount:          number;
  destCcy:             string;
  transactionDate:     string;
  createdAt:           string;
}
