import { Component, inject, signal } from '@angular/core';

import { ImportService } from '../../core/services/import.service';
import { ImportSummary } from '../../core/models/import-summary.model';

@Component({
  selector: 'app-csv-upload',
  imports: [],
  templateUrl: './csv-upload.html',
  styleUrl: './csv-upload.scss'
})
export class CsvUpload {
  private readonly importService = inject(ImportService);

  protected readonly selectedFile = signal<File | null>(null);
  protected readonly uploading = signal(false);
  protected readonly summary = signal<ImportSummary | null>(null);
  protected readonly error = signal<string | null>(null);

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
    this.summary.set(null);
    this.error.set(null);
  }

  protected upload(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }
    this.uploading.set(true);
    this.error.set(null);
    this.summary.set(null);

    this.importService.importRiskLimits(file).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.uploading.set(false);
      },
      error: () => {
        this.error.set("Échec de l'import. Vérifiez que le backend est démarré et que le fichier est un CSV valide.");
        this.uploading.set(false);
      }
    });
  }
}
