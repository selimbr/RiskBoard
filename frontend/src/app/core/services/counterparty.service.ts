import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../api-base-url';
import { Counterparty } from '../models/counterparty.model';

@Injectable({ providedIn: 'root' })
export class CounterpartyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${API_BASE_URL}/counterparties`;

  getAll(): Observable<Counterparty[]> {
    return this.http.get<Counterparty[]>(this.baseUrl);
  }
}
