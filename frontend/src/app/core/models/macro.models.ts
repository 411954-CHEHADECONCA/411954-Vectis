export interface ExchangeRateResponse {
  rateType: string;
  buy:      string;
  sell:     string;
  rateDate: string;
  source:   string;
}

export interface InflationResponse {
  monthlyRate: string;
  periodDate:  string;
  source:      string;
}
