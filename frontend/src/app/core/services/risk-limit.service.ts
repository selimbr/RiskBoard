import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api-base-url';
import { LimitType, RiskLimitDashboardRow, RiskLimitDto, SectorTypeAggregationRow } from '../models/risk.model';

@Injectable({ providedIn: 'root' })
export class RiskLimitService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/risk-limits`;

  getDashboard(): Observable<RiskLimitDashboardRow[]> {
    return this.http.get<RiskLimitDashboardRow[]>(`${this.baseUrl}/dashboard`);
  }

  getSectorAggregationByType(limitType: LimitType): Observable<SectorTypeAggregationRow[]> {
    return this.http.get<SectorTypeAggregationRow[]>(`${this.baseUrl}/aggregation`, {
      params: { limitType }
    });
  }

  findLimit(counterpartyId: number, limitType: LimitType): Observable<RiskLimitDto> {
    return this.http.get<RiskLimitDto>(`${this.baseUrl}/counterparty/${counterpartyId}/type/${limitType}`);
  }
}
