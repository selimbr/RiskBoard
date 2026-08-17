package fr.riskBoard.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.entities.RiskLimit;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.RiskLimitRepository;

/**
 * Teste la pile complète HTTP -> contrôleur -> service -> base H2 réelle
 * (par opposition aux tests unitaires de RiskCalculationServiceTest, qui
 * testent la formule isolément sans passer par le contrôleur ni la base).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RiskLimitControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private RiskLimitRepository riskLimitRepository;

    private Counterparty seedCounterparty(String name, String ricosCode, String sector) {
        return counterpartyRepository.save(Counterparty.builder()
                .name(name)
                .ricosCode(ricosCode)
                .country("FR")
                .sector(sector)
                .build());
    }

    private void seedRiskLimit(Counterparty counterparty, LimitType limitType, long maxAmount, long usedAmount) {
        riskLimitRepository.save(RiskLimit.builder()
                .counterparty(counterparty)
                .limitType(limitType)
                .maxAmount(BigDecimal.valueOf(maxAmount))
                .usedAmount(BigDecimal.valueOf(usedAmount))
                .currency("EUR")
                .lastUpdated(LocalDateTime.now())
                .build());
    }

    @Test
    void shouldReturnGreenAlertLevelWhenUsageRateBelow70Percent() throws Exception {
        Counterparty bnp = seedCounterparty("BNP PARIBAS", "RICOS48213", "Banking");
        seedRiskLimit(bnp, LimitType.CREDIT, 50_000_000, 20_000_000); // 40%

        mockMvc.perform(get("/api/risk-limits/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].counterpartyName").value("BNP PARIBAS"))
                .andExpect(jsonPath("$[0].usageRate").value(40.0))
                .andExpect(jsonPath("$[0].alertLevel").value("GREEN"));
    }

    @Test
    void shouldReturnOrangeAlertLevelWhenUsageRateBetween70And90Percent() throws Exception {
        Counterparty bnp = seedCounterparty("BNP PARIBAS", "RICOS48213", "Banking");
        seedRiskLimit(bnp, LimitType.CREDIT, 10_000_000, 8_000_000); // 80%

        mockMvc.perform(get("/api/risk-limits/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usageRate").value(80.0))
                .andExpect(jsonPath("$[0].alertLevel").value("ORANGE"));
    }

    @Test
    void shouldReturnRedAlertLevelWhenUsageRateAbove90Percent() throws Exception {
        Counterparty shell = seedCounterparty("SHELL PLC", "RICOS68792", "Energy");
        seedRiskLimit(shell, LimitType.MARKET, 18_000_000, 17_640_000); // 98%

        mockMvc.perform(get("/api/risk-limits/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usageRate").value(98.0))
                .andExpect(jsonPath("$[0].alertLevel").value("RED"));
    }

    @Test
    void shouldAggregateExposureBySectorForAGivenLimitType() throws Exception {
        Counterparty bnp = seedCounterparty("BNP PARIBAS", "RICOS48213", "Banking");
        Counterparty sg = seedCounterparty("SOCIETE GENERALE", "RICOS91427", "Banking");
        seedRiskLimit(bnp, LimitType.CREDIT, 50_000_000, 10_000_000);
        seedRiskLimit(sg, LimitType.CREDIT, 30_000_000, 5_000_000);

        mockMvc.perform(get("/api/risk-limits/aggregation").param("limitType", "CREDIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sector").value("Banking"))
                .andExpect(jsonPath("$[0].limitType").value("CREDIT"))
                .andExpect(jsonPath("$[0].totalUsedAmount").value(15_000_000));
    }

    @Test
    void shouldFindExistingLimitForCounterpartyAndType() throws Exception {
        Counterparty bnp = seedCounterparty("BNP PARIBAS", "RICOS48213", "Banking");
        seedRiskLimit(bnp, LimitType.CREDIT, 50_000_000, 32_000_000);

        mockMvc.perform(get("/api/risk-limits/counterparty/{id}/type/{limitType}", bnp.getId(), "CREDIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxAmount").value(50_000_000))
                .andExpect(jsonPath("$.usedAmount").value(32_000_000));
    }

    @Test
    void shouldReturn404WhenNoLimitExistsForCounterpartyAndType() throws Exception {
        Counterparty bnp = seedCounterparty("BNP PARIBAS", "RICOS48213", "Banking");

        mockMvc.perform(get("/api/risk-limits/counterparty/{id}/type/{limitType}", bnp.getId(), "MARKET"))
                .andExpect(status().isNotFound());
    }
}
