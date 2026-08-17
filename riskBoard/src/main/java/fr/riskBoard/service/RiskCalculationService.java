package fr.riskBoard.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import fr.riskBoard.enums.AlertLevel;
import fr.riskBoard.entities.RiskLimit;

@Service
public class RiskCalculationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ORANGE_THRESHOLD = BigDecimal.valueOf(70);
    private static final BigDecimal RED_THRESHOLD = BigDecimal.valueOf(90);

    public BigDecimal usageRate(RiskLimit riskLimit) {
        return usageRate(riskLimit.getUsedAmount(), riskLimit.getMaxAmount());
    }

    public BigDecimal usageRate(BigDecimal usedAmount, BigDecimal maxAmount) {
        if (maxAmount == null || maxAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return usedAmount.divide(maxAmount, 4, RoundingMode.HALF_UP).multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public AlertLevel alertLevel(BigDecimal usageRate) {
        if (usageRate.compareTo(ORANGE_THRESHOLD) < 0) {
            return AlertLevel.GREEN;
        }
        if (usageRate.compareTo(RED_THRESHOLD) <= 0) {
            return AlertLevel.ORANGE;
        }
        return AlertLevel.RED;
    }

    public AlertLevel alertLevel(RiskLimit riskLimit) {
        return alertLevel(usageRate(riskLimit));
    }

    /**
     * Exposition agrégée par secteur : somme des usedAmount de toutes les
     * limites, groupée par secteur de la contrepartie.
     */
    public Map<String, BigDecimal> aggregateExposureBySector(List<RiskLimit> riskLimits) {
        return riskLimits.stream().collect(Collectors.groupingBy(
                rl -> rl.getCounterparty().getSector(),
                Collectors.reducing(BigDecimal.ZERO, RiskLimit::getUsedAmount, BigDecimal::add)));
    }
}
