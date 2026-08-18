import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';

import { RiskLimitService } from '../../core/services/risk-limit.service';
import { LIMIT_TYPES, LimitType, RiskLimitDashboardRow, SectorTypeAggregationRow } from '../../core/models/risk.model';

type SortColumn = 'counterpartyName' | 'limitType' | 'sector' | 'maxAmount' | 'usedAmount' | 'usageRate' | 'alertLevel';
type SortDirection = 'asc' | 'desc';
type ViewMode = 'DETAILED' | LimitType;

interface ColumnDef {
  key: SortColumn;
  label: string;
  defaultDirection: SortDirection;
}

const COLUMNS: ColumnDef[] = [
  { key: 'counterpartyName', label: 'Nom', defaultDirection: 'asc' },
  { key: 'limitType', label: 'Type de limite', defaultDirection: 'asc' },
  { key: 'sector', label: 'Secteur', defaultDirection: 'asc' },
  { key: 'maxAmount', label: 'Limite Max', defaultDirection: 'desc' },
  { key: 'usedAmount', label: 'Utilisé', defaultDirection: 'desc' },
  { key: 'usageRate', label: '% Usage', defaultDirection: 'desc' },
  { key: 'alertLevel', label: 'Statut', defaultDirection: 'asc' }
];

const PAGE_SIZE = 10;

function compareValue(a: RiskLimitDashboardRow, b: RiskLimitDashboardRow, key: SortColumn): number {
  const av = a[key];
  const bv = b[key];
  if (typeof av === 'number' && typeof bv === 'number') {
    return av - bv;
  }
  return String(av).localeCompare(String(bv));
}

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss'
})
export class Dashboard implements OnInit {
  private readonly riskLimitService = inject(RiskLimitService);

  protected readonly columns = COLUMNS;
  protected readonly limitTypes = LIMIT_TYPES;
  protected readonly pageSize = PAGE_SIZE;

  protected readonly rows = signal<RiskLimitDashboardRow[]>([]);
  protected readonly aggregationRows = signal<SectorTypeAggregationRow[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly filterText = signal('');
  protected readonly viewMode = signal<ViewMode>('DETAILED');
  protected readonly currentPage = signal(1);

  protected readonly sortColumn = signal<SortColumn | null>(null);
  protected readonly sortDirection = signal<SortDirection>('asc');

  protected readonly filteredAndSortedRows = computed(() => {
    const filter = this.filterText().trim().toLowerCase();
    const filtered = filter
      ? this.rows().filter((r) => r.counterpartyName.toLowerCase().includes(filter))
      : this.rows();

    return [...filtered].sort((a, b) => this.compareRows(a, b));
  });

  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.filteredAndSortedRows().length / this.pageSize))
  );

  protected readonly pagedRows = computed(() => {
    const page = this.currentPage();
    const start = (page - 1) * this.pageSize;
    return this.filteredAndSortedRows().slice(start, start + this.pageSize);
  });

  ngOnInit(): void {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.loading.set(true);
    this.error.set(null);
    this.riskLimitService.getDashboard().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("Impossible de charger le tableau de bord. Vérifiez que le backend est démarré.");
        this.loading.set(false);
      }
    });
  }

  protected onFilterChange(value: string): void {
    this.filterText.set(value);
    this.currentPage.set(1);
  }

  protected onViewModeChange(value: string): void {
    this.viewMode.set(value as ViewMode);
    this.currentPage.set(1);
    if (value === 'DETAILED') {
      this.aggregationRows.set([]);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.riskLimitService.getSectorAggregationByType(value as LimitType).subscribe({
      next: (rows) => {
        this.aggregationRows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set("Impossible de charger l'agrégation par secteur.");
        this.loading.set(false);
      }
    });
  }

  protected onSortClick(column: SortColumn): void {
    if (this.sortColumn() === column) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set(COLUMNS.find((c) => c.key === column)!.defaultDirection);
    }
    this.currentPage.set(1);
  }

  protected sortIndicator(column: SortColumn): string {
    if (this.sortColumn() !== column) {
      return '';
    }
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  protected goToPage(page: number): void {
    if (page < 1 || page > this.totalPages()) {
      return;
    }
    this.currentPage.set(page);
  }

  private compareRows(a: RiskLimitDashboardRow, b: RiskLimitDashboardRow): number {
    const activeColumn = this.sortColumn();
    const activeDirection = this.sortDirection();

    const orderedColumns: { key: SortColumn; direction: SortDirection }[] = activeColumn
      ? [
          { key: activeColumn, direction: activeDirection },
          ...COLUMNS.filter((c) => c.key !== activeColumn).map((c) => ({ key: c.key, direction: c.defaultDirection }))
        ]
      : COLUMNS.map((c) => ({ key: c.key, direction: c.defaultDirection }));

    for (const { key, direction } of orderedColumns) {
      const cmp = compareValue(a, b, key);
      if (cmp !== 0) {
        return direction === 'asc' ? cmp : -cmp;
      }
    }
    return 0;
  }
}
