import { Routes } from '@angular/router';

import { Dashboard } from './features/dashboard/dashboard';
import { CsvUpload } from './features/csv-upload/csv-upload';
import { DerogationForm } from './features/derogation-form/derogation-form';
import { DerogationPending } from './features/derogation-pending/derogation-pending';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', component: Dashboard },
  { path: 'upload', component: CsvUpload },
  { path: 'derogations/new', component: DerogationForm },
  { path: 'derogations/pending', component: DerogationPending },
  { path: '**', redirectTo: 'dashboard' }
];
