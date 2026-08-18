import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';

import { CsvUpload } from './csv-upload';
import { ImportService } from '../../core/services/import.service';
import { ImportSummary } from '../../core/models/import-summary.model';

describe('CsvUpload', () => {
  function createComponent(importServiceSpy: { importRiskLimits: ReturnType<typeof vi.fn> }) {
    TestBed.configureTestingModule({
      imports: [CsvUpload],
      providers: [{ provide: ImportService, useValue: importServiceSpy }]
    });
    const fixture = TestBed.createComponent(CsvUpload);
    return fixture;
  }

  it('should render one <li> per error even when several errors share the same line number', () => {
    // Reproduit le cas "plusieurs colonnes d'en-tête manquantes" : CsvImportService
    // attribue line=1 à chacune (voir CsvImportService.java), donc s.errors contient
    // des doublons sur ce champ. Avant le fix (`track e.line`), Angular levait NG0955
    // ("track expression resulted in duplicated key") au rendu de ce cas précis.
    const summary: ImportSummary = {
      successCount: 0,
      errorCount: 2,
      errors: [
        { line: 1, message: "Colonne manquante dans l'en-tête : sector" },
        { line: 1, message: "Colonne manquante dans l'en-tête : currency" }
      ]
    };
    const importServiceSpy = { importRiskLimits: vi.fn().mockReturnValue(of(summary)) };
    const fixture = createComponent(importServiceSpy);
    const component = fixture.componentInstance as unknown as any;

    component.selectedFile.set(new File(['irrelevant'], 'data.csv', { type: 'text/csv' }));

    expect(() => {
      component.upload();
      fixture.detectChanges();
    }).not.toThrow();

    const items = (fixture.nativeElement as HTMLElement).querySelectorAll('.error-list li');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('sector');
    expect(items[1].textContent).toContain('currency');
  });
});
