package fr.riskBoard.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.entities.RiskLimit;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.RiskLimitRepository;

/**
 * Vérifie l'import CSV bout en bout : upload multipart réel via HTTP, puis
 * inspection directe de la base pour confirmer que les données sont
 * effectivement persistées (et pas seulement que le résumé JSON est correct).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CsvImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private RiskLimitRepository riskLimitRepository;

    @AfterEach
    void cleanUp() {
        riskLimitRepository.deleteAll();
        counterpartyRepository.deleteAll();
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "data.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldImportValidRowsAndPersistThemInDatabase() throws Exception {
        String csv = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BNP PARIBAS,RICOS48213,FR,Banking,CREDIT,50000000,32000000,EUR\n"
                + "SHELL PLC,RICOS68792,UK,Energy,MARKET,18000000,17200000,GBP\n";

        mockMvc.perform(multipart("/api/import/risk-limits").file(csvFile(csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.errorCount").value(0));

        assertThat(counterpartyRepository.count()).isEqualTo(2);
        Optional<Counterparty> bnp = counterpartyRepository.findByRicosCode("RICOS48213");
        assertThat(bnp).isPresent();
        assertThat(bnp.get().getName()).isEqualTo("BNP PARIBAS");

        Optional<RiskLimit> limit = riskLimitRepository.findByCounterpartyIdAndLimitType(bnp.get().getId(), LimitType.CREDIT);
        assertThat(limit).isPresent();
        assertThat(limit.get().getMaxAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000_000));
    }

    @Test
    void shouldReportPerLineErrorsWithoutBlockingValidRows() throws Exception {
        String csv = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BNP PARIBAS,RICOS48213,FR,Banking,CREDIT,50000000,32000000,EUR\n"
                + "BAD ROW,RICOS00000,FR,Banking,NOT_A_TYPE,50000000,32000000,EUR\n"
                + "ANOTHER BAD,RICOS00001,FR,Banking,CREDIT,notanumber,32000000,EUR\n";

        mockMvc.perform(multipart("/api/import/risk-limits").file(csvFile(csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(2))
                .andExpect(jsonPath("$.errors[0].line").exists())
                .andExpect(jsonPath("$.errors[0].message").exists());

        assertThat(counterpartyRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectRowsWithInvalidRicosCodeCountryOrCurrencyFormat() throws Exception {
        String csv = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BAD RICOS,RIC123,FR,Banking,CREDIT,50000000,32000000,EUR\n"
                + "BAD COUNTRY,RICOS48213,FRA,Banking,CREDIT,50000000,32000000,EUR\n"
                + "BAD CURRENCY,RICOS72905,FR,Banking,CREDIT,50000000,32000000,XYZ\n";

        mockMvc.perform(multipart("/api/import/risk-limits").file(csvFile(csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.errorCount").value(3));

        assertThat(counterpartyRepository.count()).isZero();
    }

    @Test
    void shouldUpsertOnReimportInsteadOfDuplicating() throws Exception {
        String firstImport = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BNP PARIBAS,RICOS48213,FR,Banking,CREDIT,50000000,32000000,EUR\n";
        String secondImport = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BNP PARIBAS,RICOS48213,FR,Banking,CREDIT,50000000,45000000,EUR\n";

        mockMvc.perform(multipart("/api/import/risk-limits").file(csvFile(firstImport)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/import/risk-limits").file(csvFile(secondImport)))
                .andExpect(status().isOk());

        assertThat(counterpartyRepository.count()).isEqualTo(1);
        assertThat(riskLimitRepository.count()).isEqualTo(1);

        Counterparty bnp = counterpartyRepository.findByRicosCode("RICOS48213").orElseThrow();
        RiskLimit limit = riskLimitRepository.findByCounterpartyIdAndLimitType(bnp.getId(), LimitType.CREDIT).orElseThrow();
        assertThat(limit.getUsedAmount()).isEqualByComparingTo(BigDecimal.valueOf(45_000_000));
    }
}
