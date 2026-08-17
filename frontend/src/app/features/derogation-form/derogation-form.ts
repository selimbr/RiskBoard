import { Component, inject, OnInit, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { DecimalPipe } from '@angular/common';

import { CounterpartyService } from '../../core/services/counterparty.service';
import { DerogationService } from '../../core/services/derogation.service';
import { RiskLimitService } from '../../core/services/risk-limit.service';
import { Counterparty } from '../../core/models/counterparty.model';
import { LIMIT_TYPES, LimitType } from '../../core/models/risk.model';

function greaterThanZero(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    return value !== null && value !== '' && Number(value) > 0 ? null : { mustBePositive: true };
  };
}

type LimitCheckState = 'idle' | 'checking' | 'exists' | 'missing';

@Component({
  selector: 'app-derogation-form',
  imports: [ReactiveFormsModule, DecimalPipe],
  templateUrl: './derogation-form.html',
  styleUrl: './derogation-form.scss'
})
export class DerogationForm implements OnInit {
  private readonly counterpartyService = inject(CounterpartyService);
  private readonly riskLimitService = inject(RiskLimitService);
  private readonly derogationService = inject(DerogationService);

  protected readonly limitTypes = LIMIT_TYPES;
  protected readonly counterparties = signal<Counterparty[]>([]);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly submitted = signal(false);
  protected readonly limitCheckState = signal<LimitCheckState>('idle');

  protected readonly form = new FormGroup({
    counterpartyId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    limitType: new FormControl<LimitType | null>(null, { validators: [Validators.required] }),
    amount: new FormControl<number | null>(null, {
      validators: [Validators.required, greaterThanZero()]
    }),
    reason: new FormControl<string>('', { nonNullable: true, validators: [Validators.required, Validators.minLength(20)] }),
    requestedBy: new FormControl<string>('', { nonNullable: true, validators: [Validators.required, Validators.minLength(6)] })
  });

  ngOnInit(): void {
    this.counterpartyService.getAll().subscribe((list) => this.counterparties.set(list));

    this.form.controls.counterpartyId.valueChanges.subscribe(() => this.onLimitSelectorChanged());
    this.form.controls.limitType.valueChanges.subscribe(() => this.onLimitSelectorChanged());
  }

  private onLimitSelectorChanged(): void {
    this.form.controls.amount.updateValueAndValidity();

    const counterpartyId = this.form.controls.counterpartyId.value;
    const limitType = this.form.controls.limitType.value;

    if (!counterpartyId || !limitType) {
      this.limitCheckState.set('idle');
      return;
    }

    this.limitCheckState.set('checking');
    this.riskLimitService.findLimit(counterpartyId, limitType).subscribe({
      next: () => this.limitCheckState.set('exists'),
      error: () => this.limitCheckState.set('missing')
    });
  }

  protected get canSubmit(): boolean {
    return this.form.valid && this.limitCheckState() === 'exists' && !this.submitting();
  }

  protected submit(): void {
    if (!this.canSubmit) {
      return;
    }
    const value = this.form.getRawValue();
    this.submitting.set(true);
    this.submitError.set(null);
    this.submitted.set(false);

    this.derogationService
      .create({
        counterpartyId: value.counterpartyId!,
        limitType: value.limitType!,
        amount: value.amount!,
        reason: value.reason,
        requestedBy: value.requestedBy
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.submitted.set(true);
          this.form.reset();
          this.limitCheckState.set('idle');
        },
        error: (err) => {
          this.submitting.set(false);
          this.submitError.set(err?.error?.message ?? "Échec de l'envoi de la demande de dérogation.");
        }
      });
  }
}
