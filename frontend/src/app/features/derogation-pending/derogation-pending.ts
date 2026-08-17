import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';

import { DerogationService } from '../../core/services/derogation.service';
import { DerogationRequestDto } from '../../core/models/derogation.model';

@Component({
  selector: 'app-derogation-pending',
  imports: [DecimalPipe, DatePipe],
  templateUrl: './derogation-pending.html',
  styleUrl: './derogation-pending.scss'
})
export class DerogationPending implements OnInit {
  private readonly derogationService = inject(DerogationService);

  protected readonly pending = signal<DerogationRequestDto[]>([]);
  protected readonly loading = signal(false);
  protected readonly processingId = signal<number | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.derogationService.listPending().subscribe({
      next: (rows) => {
        this.pending.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les demandes en attente.');
        this.loading.set(false);
      }
    });
  }

  protected approve(id: number): void {
    this.processingId.set(id);
    this.derogationService.approve(id).subscribe({
      next: () => this.removeFromList(id),
      error: () => {
        this.error.set('Échec de la validation de la demande.');
        this.processingId.set(null);
      }
    });
  }

  protected reject(id: number): void {
    this.processingId.set(id);
    this.derogationService.reject(id).subscribe({
      next: () => this.removeFromList(id),
      error: () => {
        this.error.set('Échec du rejet de la demande.');
        this.processingId.set(null);
      }
    });
  }

  private removeFromList(id: number): void {
    this.pending.set(this.pending().filter((d) => d.id !== id));
    this.processingId.set(null);
  }
}
