import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api-base-url';
import { LimitType } from '../models/risk.model';
import { CreateDerogationRequest, DerogationEligibility, DerogationRequestDto } from '../models/derogation.model';

@Injectable({ providedIn: 'root' })
export class DerogationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/derogations`;

  create(request: CreateDerogationRequest): Observable<DerogationRequestDto> {
    return this.http.post<DerogationRequestDto>(this.baseUrl, request);
  }

  checkEligibility(counterpartyId: number, limitType: LimitType, amount: number): Observable<DerogationEligibility> {
    return this.http.get<DerogationEligibility>(`${this.baseUrl}/eligibility`, {
      params: { counterpartyId, limitType, amount }
    });
  }

  listPending(): Observable<DerogationRequestDto[]> {
    return this.http.get<DerogationRequestDto[]>(`${this.baseUrl}/pending`);
  }

  approve(id: number): Observable<DerogationRequestDto> {
    return this.http.post<DerogationRequestDto>(`${this.baseUrl}/${id}/approve`, {});
  }

  reject(id: number): Observable<DerogationRequestDto> {
    return this.http.post<DerogationRequestDto>(`${this.baseUrl}/${id}/reject`, {});
  }
}
