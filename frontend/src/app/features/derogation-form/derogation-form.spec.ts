import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, Subject, throwError } from 'rxjs';

import { DerogationForm } from './derogation-form';
import { CounterpartyService } from '../../core/services/counterparty.service';
import { DerogationService } from '../../core/services/derogation.service';
import { Counterparty } from '../../core/models/counterparty.model';
import { DerogationEligibility } from '../../core/models/derogation.model';

describe('DerogationForm', () => {
  const counterparties: Counterparty[] = [
    { id: 1, name: 'BNP PARIBAS', ricosCode: 'RICOS48213', country: 'FR', sector: 'Banking' }
  ];

  const maxAllowedAmount = 1_500_000; // 1_000_000 (maxAmount) * 1.5

  const validFormValue = {
    counterpartyId: 1,
    limitType: 'CREDIT' as const,
    amount: 100_000,
    reason: 'Raison suffisamment longue pour passer la validation du formulaire',
    requestedBy: 'j.dupont'
  };

  let counterpartyServiceSpy: { getAll: ReturnType<typeof vi.fn> };
  let derogationServiceSpy: { create: ReturnType<typeof vi.fn>; checkEligibility: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    counterpartyServiceSpy = { getAll: vi.fn().mockReturnValue(of(counterparties)) };
    derogationServiceSpy = {
      create: vi.fn().mockReturnValue(of({})),
      checkEligibility: vi.fn().mockImplementation((_counterpartyId: number, _limitType: string, amount: number) =>
        of({ allowed: amount <= maxAllowedAmount, maxAllowedAmount } satisfies DerogationEligibility)
      )
    };

    await TestBed.configureTestingModule({
      imports: [DerogationForm],
      providers: [
        { provide: CounterpartyService, useValue: counterpartyServiceSpy },
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

    expect(derogationServiceSpy.checkEligibility).toHaveBeenCalledWith(1, 'CREDIT', 0);
    expect(component.limitCheckState()).toBe('exists');
  });

  it('should set limitCheckState to "missing" when no risk limit exists for the pair', () => {
    derogationServiceSpy.checkEligibility.mockReturnValue(throwError(() => new Error('404')));
    const { component } = createComponent();

    component.form.controls.counterpartyId.setValue(1);
    component.form.controls.limitType.setValue('CREDIT');

    expect(component.limitCheckState()).toBe('missing');
  });

  it('should ignore a stale limit-check response that resolves after a newer selection', () => {
    // Race condition : deux changements rapides déclenchent deux appels
    // checkEligibility(). Sans switchMap, celui qui répond en dernier
    // écraserait limitCheckState, même s'il correspond à une sélection périmée.
    const staleResponse$ = new Subject<DerogationEligibility>();
    const freshResponse$ = new Subject<DerogationEligibility>();
    derogationServiceSpy.checkEligibility.mockReturnValueOnce(staleResponse$).mockReturnValueOnce(freshResponse$);

    const { component } = createComponent();

    component.form.controls.counterpartyId.setValue(1);
    component.form.controls.limitType.setValue('CREDIT'); // 1er appel réel -> staleResponse$
    component.form.controls.limitType.setValue('MARKET'); // 2e appel réel -> freshResponse$, doit annuler le 1er

    expect(derogationServiceSpy.checkEligibility).toHaveBeenCalledTimes(2);
    expect(component.limitCheckState()).toBe('checking');

    // La réponse périmée du 1er appel arrive en dernier : ne doit rien changer,
    // le composant n'est plus abonné à ce flux (switchMap l'a désabonné).
    staleResponse$.next({ allowed: true, maxAllowedAmount });
    expect(component.limitCheckState()).toBe('checking');

    // La réponse de la sélection courante arrive enfin.
    freshResponse$.error(new Error('404'));
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
