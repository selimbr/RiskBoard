package fr.riskBoard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.riskBoard.dto.CreateDerogationRequest;
import fr.riskBoard.dto.DerogationEligibility;
import fr.riskBoard.dto.DerogationRequestDto;
import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.entities.DerogationRequest;
import fr.riskBoard.entities.RiskLimit;
import fr.riskBoard.enums.DerogationStatus;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.exception.BusinessRuleException;
import fr.riskBoard.exception.NotFoundException;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.DerogationRequestRepository;
import fr.riskBoard.repository.RiskLimitRepository;

@ExtendWith(MockitoExtension.class)
class DerogationRequestServiceTest {

    private static final String VALID_REASON = "Demande urgente pour financement exceptionnel du client";

    @Mock
    private DerogationRequestRepository derogationRequestRepository;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private RiskLimitRepository riskLimitRepository;

    private DerogationRequestService service;

    private Counterparty counterparty;
    private RiskLimit riskLimit;

    @BeforeEach
    void setUp() {
        service = new DerogationRequestService(derogationRequestRepository, counterpartyRepository, riskLimitRepository);

        counterparty = Counterparty.builder()
                .id(1L)
                .name("BNP PARIBAS")
                .ricosCode("RICOS48213")
                .country("FR")
                .sector("Banking")
                .build();

        riskLimit = RiskLimit.builder()
                .id(10L)
                .counterparty(counterparty)
                .limitType(LimitType.CREDIT)
                .maxAmount(BigDecimal.valueOf(1_000_000))
                .usedAmount(BigDecimal.valueOf(500_000))
                .currency("EUR")
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private CreateDerogationRequest requestFor(BigDecimal amount) {
        CreateDerogationRequest request = new CreateDerogationRequest();
        request.setCounterpartyId(1L);
        request.setLimitType(LimitType.CREDIT);
        request.setAmount(amount);
        request.setReason(VALID_REASON);
        request.setRequestedBy("j.dupont");
        return request;
    }

    private DerogationRequest pendingEntity() {
        return entityWithStatus(DerogationStatus.PENDING);
    }

    private DerogationRequest entityWithStatus(DerogationStatus status) {
        return DerogationRequest.builder()
                .id(1L)
                .counterparty(counterparty)
                .limitType(LimitType.CREDIT)
                .requestedBy("j.dupont")
                .amount(BigDecimal.valueOf(100_000))
                .reason(VALID_REASON)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // --- create() : règle des 150% ---

    @Test
    void shouldCreateDerogationWhenAmountWithin150PercentOfLimit() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.of(counterparty));
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(1L, LimitType.CREDIT)).thenReturn(Optional.of(riskLimit));
        when(derogationRequestRepository.save(any())).thenAnswer(invocation -> {
            DerogationRequest saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        DerogationRequestDto dto = service.create(requestFor(BigDecimal.valueOf(1_200_000)));

        assertThat(dto.getId()).isEqualTo(100L);
        assertThat(dto.getStatus()).isEqualTo(DerogationStatus.PENDING);
        assertThat(dto.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_200_000));
        assertThat(dto.getCounterpartyName()).isEqualTo("BNP PARIBAS");
    }

    @Test
    void shouldAllowAmountExactlyAt150PercentBoundary() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.of(counterparty));
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(1L, LimitType.CREDIT)).thenReturn(Optional.of(riskLimit));
        when(derogationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal exactlyMaxAllowed = riskLimit.getMaxAmount().multiply(BigDecimal.valueOf(1.5));

        DerogationRequestDto dto = service.create(requestFor(exactlyMaxAllowed));

        assertThat(dto.getAmount()).isEqualByComparingTo(exactlyMaxAllowed);
    }

    @Test
    void shouldRejectAmountAbove150PercentOfLimit() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.of(counterparty));
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(1L, LimitType.CREDIT)).thenReturn(Optional.of(riskLimit));

        BigDecimal tooHigh = riskLimit.getMaxAmount().multiply(BigDecimal.valueOf(1.5)).add(BigDecimal.ONE);

        assertThatThrownBy(() -> service.create(requestFor(tooHigh)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("150%");

        verify(derogationRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenNoRiskLimitExistsForCounterpartyAndType() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.of(counterparty));
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(1L, LimitType.CREDIT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(requestFor(BigDecimal.valueOf(100_000))))
                .isInstanceOf(BusinessRuleException.class);

        verify(derogationRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenCounterpartyNotFound() {
        when(counterpartyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(requestFor(BigDecimal.valueOf(100_000))))
                .isInstanceOf(NotFoundException.class);

        verify(riskLimitRepository, never()).findByCounterpartyIdAndLimitType(any(), any());
        verify(derogationRequestRepository, never()).save(any());
    }

    // --- checkEligibility() ---

    @Test
    void shouldReportEligibleWhenAmountWithin150PercentOfLimit() {
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(1L, LimitType.CREDIT)).thenReturn(Optional.of(riskLimit));

        DerogationEligibility eligibility = service.checkEligibility(1L, LimitType.CREDIT, BigDecimal.valueOf(1_200_000));

        assertThat(eligibility.isAllowed()).isTrue();
        assertThat(eligibility.getMaxAllowedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_500_000));
    }

    @Test
    void shouldReportNotEligibleWhenAmountAbove150PercentOfLimit() {
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(1L, LimitType.CREDIT)).thenReturn(Optional.of(riskLimit));

        DerogationEligibility eligibility = service.checkEligibility(1L, LimitType.CREDIT, BigDecimal.valueOf(1_600_000));

        assertThat(eligibility.isAllowed()).isFalse();
        assertThat(eligibility.getMaxAllowedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_500_000));
    }

    @Test
    void shouldThrowNotFoundWhenCheckingEligibilityForMissingLimit() {
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(1L, LimitType.CREDIT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkEligibility(1L, LimitType.CREDIT, BigDecimal.valueOf(100_000)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- listPending() ---

    @Test
    void shouldListOnlyPendingDerogations() {
        when(derogationRequestRepository.findByStatus(DerogationStatus.PENDING)).thenReturn(List.of(pendingEntity()));

        List<DerogationRequestDto> result = service.listPending();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(DerogationStatus.PENDING);
    }

    // --- approve() / reject() ---

    @Test
    void shouldApproveDerogation() {
        when(derogationRequestRepository.findById(1L)).thenReturn(Optional.of(pendingEntity()));
        when(derogationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DerogationRequestDto dto = service.approve(1L);

        assertThat(dto.getStatus()).isEqualTo(DerogationStatus.APPROVED);
    }

    @Test
    void shouldRejectDerogationRequestOnRejectAction() {
        when(derogationRequestRepository.findById(1L)).thenReturn(Optional.of(pendingEntity()));
        when(derogationRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DerogationRequestDto dto = service.reject(1L);

        assertThat(dto.getStatus()).isEqualTo(DerogationStatus.REJECTED);
    }

    @Test
    void shouldRejectApprovingADerogationThatIsAlreadyApproved() {
        when(derogationRequestRepository.findById(1L)).thenReturn(Optional.of(entityWithStatus(DerogationStatus.APPROVED)));

        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BusinessRuleException.class);

        verify(derogationRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectRejectingADerogationThatIsAlreadyApproved() {
        // Le scénario signalé : POST /reject sur une demande déjà APPROVED
        // ne doit pas la repasser en REJECTED.
        when(derogationRequestRepository.findById(1L)).thenReturn(Optional.of(entityWithStatus(DerogationStatus.APPROVED)));

        assertThatThrownBy(() -> service.reject(1L))
                .isInstanceOf(BusinessRuleException.class);

        verify(derogationRequestRepository, never()).save(any());
    }

    @Test
    void shouldRejectApprovingADerogationThatIsAlreadyRejected() {
        when(derogationRequestRepository.findById(1L)).thenReturn(Optional.of(entityWithStatus(DerogationStatus.REJECTED)));

        assertThatThrownBy(() -> service.approve(1L))
                .isInstanceOf(BusinessRuleException.class);

        verify(derogationRequestRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenApprovingUnknownDerogation() {
        when(derogationRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldThrowNotFoundWhenRejectingUnknownDerogation() {
        when(derogationRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(99L))
                .isInstanceOf(NotFoundException.class);
    }
}
