import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  AsyncValidatorFn,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { catchError, debounceTime, EMPTY, map, merge, of, switchMap } from 'rxjs';
import { DecimalPipe } from '@angular/common';

import { CounterpartyService } from '../../core/services/counterparty.service';
import { DerogationService } from '../../core/services/derogation.service';
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
  private readonly derogationService = inject(DerogationService);
  private readonly destroyRef = inject(DestroyRef);

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
      validators: [Validators.required, greaterThanZero()],
      asyncValidators: [this.amountWithinLimitValidator()]
    }),
    reason: new FormControl<string>('', { nonNullable: true, validators: [Validators.required, Validators.minLength(20)] }),
    requestedBy: new FormControl<string>('', { nonNullable: true, validators: [Validators.required, Validators.minLength(6)] })
  });

  ngOnInit(): void {
    this.counterpartyService
      .getAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((list) => this.counterparties.set(list));
    this.watchLimitSelectorChanges();
  }

  private watchLimitSelectorChanges(): void {
    merge(this.form.controls.counterpartyId.valueChanges, this.form.controls.limitType.valueChanges)
      .pipe(
        switchMap(() => {
          this.form.controls.amount.updateValueAndValidity();

          const counterpartyId = this.form.controls.counterpartyId.value;
          const limitType = this.form.controls.limitType.value;

          if (!counterpartyId || !limitType) {
            this.limitCheckState.set('idle');
            return EMPTY;
          }

          this.limitCheckState.set('checking');
          return this.derogationService.checkEligibility(counterpartyId, limitType, 0).pipe(
            map(() => 'exists' as const),
            catchError(() => of('missing' as const))
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((state) => this.limitCheckState.set(state));
  }

  private amountWithinLimitValidator(): AsyncValidatorFn {
    return (control: AbstractControl) => {
      const counterpartyId = control.parent?.get('counterpartyId')?.value;
      const limitType = control.parent?.get('limitType')?.value;
      const amount = control.value;

      if (!counterpartyId || !limitType || !amount || amount <= 0) {
        return of(null);
      }

      return of(null).pipe(
        debounceTime(300),
        switchMap(() => this.derogationService.checkEligibility(counterpartyId, limitType, amount)),
        map((eligibility) =>
          eligibility.allowed ? null : { exceeds150: { maxAllowed: eligibility.maxAllowedAmount } }
        ),
        catchError(() => of({ limitNotFound: true }))
      );
    };
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
