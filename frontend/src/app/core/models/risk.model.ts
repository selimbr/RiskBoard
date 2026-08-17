export type LimitType = 'CREDIT' | 'MARKET' | 'LIQUIDITY';

export const LIMIT_TYPES: LimitType[] = ['CREDIT', 'MARKET', 'LIQUIDITY'];

export type AlertLevel = 'GREEN' | 'ORANGE' | 'RED';

export interface RiskLimitDashboardRow {
  riskLimitId: number;
  counterpartyId: number;
  counterpartyName: string;
  limitType: LimitType;
  sector: string;
  maxAmount: number;
  usedAmount: number;
  usageRate: number;
  alertLevel: AlertLevel;
}

export interface SectorTypeAggregationRow {
  limitType: LimitType;
  sector: string;
  totalUsedAmount: number;
}

export interface RiskLimitDto {
  id: number;
  counterpartyId: number;
  limitType: LimitType;
  maxAmount: number;
  usedAmount: number;
  currency: string;
}
