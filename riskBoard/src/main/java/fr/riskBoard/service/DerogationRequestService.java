package fr.riskBoard.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.entities.DerogationRequest;
import fr.riskBoard.entities.RiskLimit;
import fr.riskBoard.enums.DerogationStatus;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.dto.CreateDerogationRequest;
import fr.riskBoard.dto.DerogationEligibility;
import fr.riskBoard.dto.DerogationRequestDto;
import fr.riskBoard.exception.BusinessRuleException;
import fr.riskBoard.exception.NotFoundException;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.DerogationRequestRepository;
import fr.riskBoard.repository.RiskLimitRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DerogationRequestService {

    private static final BigDecimal MAX_DEROGATION_MULTIPLIER = BigDecimal.valueOf(1.5);

    private final DerogationRequestRepository derogationRequestRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final RiskLimitRepository riskLimitRepository;

    @Transactional
    public DerogationRequestDto create(CreateDerogationRequest request) {
        Counterparty counterparty = counterpartyRepository.findById(request.getCounterpartyId())
                .orElseThrow(() -> new NotFoundException("Contrepartie introuvable : " + request.getCounterpartyId()));

        RiskLimit riskLimit = riskLimitRepository.findByCounterpartyIdAndLimitType(counterparty.getId(), request.getLimitType())
                .orElseThrow(() -> new BusinessRuleException(
                        "Aucune limite " + request.getLimitType() + " n'existe pour la contrepartie " + counterparty.getName()));

        BigDecimal maxAllowed = maxDerogationAmount(riskLimit);
        if (request.getAmount().compareTo(maxAllowed) > 0) {
            throw new BusinessRuleException(
                    "Montant demandé (" + request.getAmount() + ") supérieur à 150% de la limite max ("
                            + maxAllowed + ")");
        }

        DerogationRequest derogationRequest = DerogationRequest.builder()
                .counterparty(counterparty)
                .limitType(request.getLimitType())
                .requestedBy(request.getRequestedBy())
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(DerogationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        return toDto(derogationRequestRepository.save(derogationRequest));
    }

    /**
     * Vérifie la règle des 150% pour un montant donné sans créer de demande -
     * utilisée par le validator asynchrone du formulaire frontend, qui ne doit
     * pas reproduire ce calcul côté client.
     */
    @Transactional(readOnly = true)
    public DerogationEligibility checkEligibility(Long counterpartyId, LimitType limitType, BigDecimal amount) {
        RiskLimit riskLimit = riskLimitRepository.findByCounterpartyIdAndLimitType(counterpartyId, limitType)
                .orElseThrow(() -> new NotFoundException(
                        "Aucune limite " + limitType + " n'existe pour la contrepartie " + counterpartyId));

        BigDecimal maxAllowed = maxDerogationAmount(riskLimit);
        return DerogationEligibility.builder()
                .allowed(amount.compareTo(maxAllowed) <= 0)
                .maxAllowedAmount(maxAllowed)
                .build();
    }

    private BigDecimal maxDerogationAmount(RiskLimit riskLimit) {
        return riskLimit.getMaxAmount().multiply(MAX_DEROGATION_MULTIPLIER);
    }

    @Transactional(readOnly = true)
    public List<DerogationRequestDto> listPending() {
        return derogationRequestRepository.findByStatus(DerogationStatus.PENDING).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DerogationRequestDto approve(Long id) {
        return updateStatus(id, DerogationStatus.APPROVED);
    }

    @Transactional
    public DerogationRequestDto reject(Long id) {
        return updateStatus(id, DerogationStatus.REJECTED);
    }

    private DerogationRequestDto updateStatus(Long id, DerogationStatus status) {
        DerogationRequest derogationRequest = derogationRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Demande de dérogation introuvable : " + id));
        if (derogationRequest.getStatus() != DerogationStatus.PENDING) {
            throw new BusinessRuleException(
                    "Impossible de modifier une demande déjà traitée (statut actuel : " + derogationRequest.getStatus() + ")");
        }
        derogationRequest.setStatus(status);
        return toDto(derogationRequestRepository.save(derogationRequest));
    }

    private DerogationRequestDto toDto(DerogationRequest d) {
        return DerogationRequestDto.builder()
                .id(d.getId())
                .counterpartyId(d.getCounterparty().getId())
                .counterpartyName(d.getCounterparty().getName())
                .limitType(d.getLimitType())
                .requestedBy(d.getRequestedBy())
                .amount(d.getAmount())
                .reason(d.getReason())
                .status(d.getStatus())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
