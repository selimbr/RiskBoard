package fr.riskBoard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import fr.riskBoard.enums.AlertLevel;
import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.entities.RiskLimit;

class RiskCalculationServiceTest {

    private final RiskCalculationService service = new RiskCalculationService();

    @Test
    void usageRateBelow70ShouldBeGreen() {
        RiskLimit limit = riskLimit("Banking", "60", "100");

        assertThat(service.usageRate(limit)).isEqualByComparingTo("60.00");
        assertThat(service.alertLevel(limit)).isEqualTo(AlertLevel.GREEN);
    }

    @Test
    void usageRateBetween70And90ShouldBeOrange() {
        RiskLimit limit80 = riskLimit("Banking", "80", "100");
        RiskLimit limitExactly70 = riskLimit("Banking", "70", "100");
        RiskLimit limitExactly90 = riskLimit("Banking", "90", "100");

        assertThat(service.alertLevel(limit80)).isEqualTo(AlertLevel.ORANGE);
        assertThat(service.alertLevel(limitExactly70)).isEqualTo(AlertLevel.ORANGE);
        assertThat(service.alertLevel(limitExactly90)).isEqualTo(AlertLevel.ORANGE);
    }

    @Test
    void usageRateAbove90ShouldBeRed() {
        RiskLimit limit = riskLimit("Banking", "95", "100");

        assertThat(service.usageRate(limit)).isEqualByComparingTo("95.00");
        assertThat(service.alertLevel(limit)).isEqualTo(AlertLevel.RED);
    }

    @Test
    void shouldAggregateExposureBySector() {
        RiskLimit bankingLimit1 = riskLimit("Banking", "32000000", "50000000");
        RiskLimit bankingLimit2 = riskLimit("Banking", "18500000", "20000000");
        RiskLimit energyLimit = riskLimit("Energy", "15000000", "25000000");

        Map<String, BigDecimal> exposure = service.aggregateExposureBySector(
                List.of(bankingLimit1, bankingLimit2, energyLimit));

        assertThat(exposure.get("Banking")).isCloseTo(new BigDecimal("50500000"), within(new BigDecimal("0.001")));
        assertThat(exposure.get("Energy")).isCloseTo(new BigDecimal("15000000"), within(new BigDecimal("0.001")));
    }

    private RiskLimit riskLimit(String sector, String usedAmount, String maxAmount) {
        Counterparty counterparty = Counterparty.builder()
                .id(1L)
                .name("Test Counterparty")
                .ricosCode("RICOS00001")
                .country("FR")
                .sector(sector)
                .build();

        return RiskLimit.builder()
                .id(1L)
                .counterparty(counterparty)
                .limitType(LimitType.CREDIT)
                .maxAmount(new BigDecimal(maxAmount))
                .usedAmount(new BigDecimal(usedAmount))
                .currency("EUR")
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}
