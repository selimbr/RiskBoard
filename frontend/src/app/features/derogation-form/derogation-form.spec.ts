import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { DerogationForm } from './derogation-form';
import { CounterpartyService } from '../../core/services/counterparty.service';
import { RiskLimitService } from '../../core/services/risk-limit.service';
import { DerogationService } from '../../core/services/derogation.service';
import { Counterparty } from '../../core/models/counterparty.model';
import { RiskLimitDto } from '../../core/models/risk.model';

describe('DerogationForm', () => {
  const counterparties: Counterparty[] = [
    { id: 1, name: 'BNP PARIBAS', ricosCode: 'RICOS48213', country: 'FR', sector: 'Banking' }
  ];

  const riskLimit: RiskLimitDto = {
    id: 10,
    counterpartyId: 1,
    limitType: 'CREDIT',
    maxAmount: 1_000_000,
    usedAmount: 500_000,
    currency: 'EUR'
  };

  const validFormValue = {
    counterpartyId: 1,
    limitType: 'CREDIT' as const,
    amount: 100_000,
    reason: 'Raison suffisamment longue pour passer la validation du formulaire',
    requestedBy: 'j.dupont'
  };

  const maxAllowedAmount = riskLimit.maxAmount * 1.5;

  let counterpartyServiceSpy: { getAll: ReturnType<typeof vi.fn> };
  let riskLimitServiceSpy: { findLimit: ReturnType<typeof vi.fn> };
  let derogationServiceSpy: { create: ReturnType<typeof vi.fn>; checkEligibility: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    counterpartyServiceSpy = { getAll: vi.fn().mockReturnValue(of(counterparties)) };
    riskLimitServiceSpy = { findLimit: vi.fn().mockReturnValue(of(riskLimit)) };
    derogationServiceSpy = {
      create: vi.fn().mockReturnValue(of({})),
      // Reflète la règle des 150% telle que calculée par le backend (le composant
      // ne fait plus ce calcul lui-même) : maxAllowedAmount vient de la réponse API.
      checkEligibility: vi.fn().mockImplementation((_counterpartyId: number, _limitType: string, amount: number) =>
        of({ allowed: amount <= maxAllowedAmount, maxAllowedAmount })
      )
    };

    await TestBed.configureTestingModule({
      imports: [DerogationForm],
      providers: [
        { provide: CounterpartyService, useValue: counterpartyServiceSpy },
        { provide: RiskLimitService, useValue: riskLimitServiceSpy },
        { provide: DerogationService, useValue: derogationServiceSpy }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(DerogationForm);
    fixture.detectChanges();
    // `form`/`submit`/... are `protected` (template-only API by design) — cast to
    // reach them directly from the spec instead of driving the whole DOM per test.
    const component = fixture.componentInstance as unknown as any;
    return { fixture, component };
  }

  it('should load the counterparty list on init', () => {
    const { component } = createComponent();

    expect(counterpartyServiceSpy.getAll).toHaveBeenCalled();
    expect(component.counterparties()).toEqual(counterparties);
  });

  it('should keep canSubmit false while the form is empty', () => {
    const { component } = createComponent();

    expect(component.canSubmit).toBe(false);
  });

  it('should set limitCheckState to "exists" once a counterparty and limit type are picked', () => {
    const { component } = createComponent();

    component.form.controls.counterpartyId.setValue(1);
    component.form.controls.limitType.setValue('CREDIT');

    expect(riskLimitServiceSpy.findLimit).toHaveBeenCalledWith(1, 'CREDIT');
    expect(component.limitCheckState()).toBe('exists');
  });

  it('should set limitCheckState to "missing" when no risk limit exists for the pair', () => {
    riskLimitServiceSpy.findLimit.mockReturnValue(throwError(() => new Error('404')));
    const { component } = createComponent();

    component.form.controls.counterpartyId.setValue(1);
    component.form.controls.limitType.setValue('CREDIT');

    expect(component.limitCheckState()).toBe('missing');
  });

  it('should enable submit once the form is valid and the limit exists', () => {
    const { component } = createComponent();
    vi.useFakeTimers();

    component.form.setValue(validFormValue);
    vi.advanceTimersByTime(300); // laisse le temps au validator asynchrone (debounce 300ms) de se résoudre

    expect(component.limitCheckState()).toBe('exists');
    expect(component.canSubmit).toBe(true);
  });

  it('should not call the backend for the 150% check while counterparty or limit type is not yet picked', () => {
    const { component } = createComponent();

    component.form.controls.amount.setValue(2_000_000);

    expect(derogationServiceSpy.checkEligibility).not.toHaveBeenCalled();
  });

  it('should flag exceeds150 after the debounce when amount is above 150% of the limit', () => {
    const { component } = createComponent();

    component.form.controls.counterpartyId.setValue(1);
    component.form.controls.limitType.setValue('CREDIT');
    vi.useFakeTimers();

    component.form.controls.amount.setValue(2_000_000); // > 1_000_000 * 1.5
    vi.advanceTimersByTime(300);

    expect(derogationServiceSpy.checkEligibility).toHaveBeenCalledWith(1, 'CREDIT', 2_000_000);
    expect(component.form.controls.amount.errors).toEqual({ exceeds150: { maxAllowed: 1_500_000 } });
    expect(component.canSubmit).toBe(false);
  });

  it('should be valid after the debounce when amount is within 150% of the limit', () => {
    const { component } = createComponent();

    component.form.controls.counterpartyId.setValue(1);
    component.form.controls.limitType.setValue('CREDIT');
    component.form.controls.reason.setValue(validFormValue.reason);
    component.form.controls.requestedBy.setValue('j.dupont');
    vi.useFakeTimers();

    component.form.controls.amount.setValue(1_200_000); // < 1_000_000 * 1.5
    vi.advanceTimersByTime(300);

    expect(component.form.controls.amount.errors).toBeNull();
    expect(component.canSubmit).toBe(true);
  });

  it('should not call the derogation API when the form is invalid', () => {
    const { component } = createComponent();

    component.submit();

    expect(derogationServiceSpy.create).not.toHaveBeenCalled();
  });

  it('should submit the derogation request and reset the form on success', () => {
    const { component } = createComponent();
    vi.useFakeTimers();
    component.form.setValue(validFormValue);
    vi.advanceTimersByTime(300);

    component.submit();

    expect(derogationServiceSpy.create).toHaveBeenCalledWith({
      counterpartyId: 1,
      limitType: 'CREDIT',
      amount: 100_000,
      reason: validFormValue.reason,
      requestedBy: 'j.dupont'
    });
    expect(component.submitted()).toBe(true);
    expect(component.submitting()).toBe(false);
    expect(component.submitError()).toBeNull();
    expect(component.form.controls.amount.value).toBeNull();
  });

  it('should surface a backend business-rule rejection on submit failure even though the frontend validator passed', () => {
    // Simule un écart entre le validator asynchrone (passé) et la validation
    // finale côté backend (rejetée) - ex. la limite a changé entre-temps.
    derogationServiceSpy.create.mockReturnValue(
      throwError(() => ({
        error: { message: 'Montant demandé (1200000) supérieur à 150% de la limite max (900000)' }
      }))
    );
    const { component } = createComponent();
    vi.useFakeTimers();
    component.form.setValue({ ...validFormValue, amount: 1_200_000 });
    vi.advanceTimersByTime(300);
    expect(component.canSubmit).toBe(true);

    component.submit();

    expect(component.submitError()).toContain('150%');
    expect(component.submitting()).toBe(false);
    expect(component.submitted()).toBe(false);
  });

  it('should fall back to a generic error message when the backend response has no message', () => {
    derogationServiceSpy.create.mockReturnValue(throwError(() => ({})));
    const { component } = createComponent();
    vi.useFakeTimers();
    component.form.setValue(validFormValue);
    vi.advanceTimersByTime(300);

    component.submit();

    expect(component.submitError()).toBe("Échec de l'envoi de la demande de dérogation.");
  });
});
