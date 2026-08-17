import { LimitType } from './risk.model';

export type DerogationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface DerogationRequestDto {
  id: number;
  counterpartyId: number;
  counterpartyName: string;
  limitType: LimitType;
  requestedBy: string;
  amount: number;
  reason: string;
  status: DerogationStatus;
  createdAt: string;
}

export interface CreateDerogationRequest {
  counterpartyId: number;
  limitType: LimitType;
  amount: number;
  reason: string;
  requestedBy: string;
}
