package fr.riskBoard.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.riskBoard.enums.LimitType;
import fr.riskBoard.entities.RiskLimit;
import fr.riskBoard.dto.RiskLimitDashboardRow;
import fr.riskBoard.dto.RiskLimitDto;
import fr.riskBoard.dto.SectorTypeAggregationRow;
import fr.riskBoard.repository.RiskLimitRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RiskLimitService {

    private final RiskLimitRepository riskLimitRepository;
    private final RiskCalculationService riskCalculationService;

    public List<RiskLimitDashboardRow> getDashboard() {
        return riskLimitRepository.findAllWithCounterparty().stream()
                .map(this::toDashboardRow)
                .toList();
    }

    private RiskLimitDashboardRow toDashboardRow(RiskLimit riskLimit) {
        return RiskLimitDashboardRow.builder()
                .riskLimitId(riskLimit.getId())
                .counterpartyId(riskLimit.getCounterparty().getId())
                .counterpartyName(riskLimit.getCounterparty().getName())
                .limitType(riskLimit.getLimitType())
                .sector(riskLimit.getCounterparty().getSector())
                .maxAmount(riskLimit.getMaxAmount())
                .usedAmount(riskLimit.getUsedAmount())
                .usageRate(riskCalculationService.usageRate(riskLimit))
                .alertLevel(riskCalculationService.alertLevel(riskLimit))
                .build();
    }

    public Map<String, BigDecimal> getSectorExposure() {
        return riskCalculationService.aggregateExposureBySector(riskLimitRepository.findAllWithCounterparty());
    }

    public List<SectorTypeAggregationRow> getSectorExposureByType(LimitType limitType) {
        List<RiskLimit> limits = riskLimitRepository.findAllWithCounterparty().stream()
                .filter(rl -> rl.getLimitType() == limitType)
                .toList();

        Map<String, BigDecimal> bySector = riskCalculationService.aggregateExposureBySector(limits);

        return bySector.entrySet().stream()
                .map(e -> SectorTypeAggregationRow.builder()
                        .limitType(limitType)
                        .sector(e.getKey())
                        .totalUsedAmount(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    public Optional<RiskLimitDto> findLimit(Long counterpartyId, LimitType limitType) {
        return riskLimitRepository.findByCounterpartyIdAndLimitType(counterpartyId, limitType)
                .map(rl -> RiskLimitDto.builder()
                        .id(rl.getId())
                        .counterpartyId(counterpartyId)
                        .limitType(limitType)
                        .maxAmount(rl.getMaxAmount())
                        .usedAmount(rl.getUsedAmount())
                        .currency(rl.getCurrency())
                        .build());
    }
}
