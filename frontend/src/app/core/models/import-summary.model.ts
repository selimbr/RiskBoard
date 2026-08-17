export interface ImportError {
  line: number;
  message: string;
}

export interface ImportSummary {
  successCount: number;
  errorCount: number;
  errors: ImportError[];
}
