package fr.riskBoard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.dto.ImportSummary;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.RiskLimitRepository;

@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private RiskLimitRepository riskLimitRepository;

    private CsvImportService csvImportService;

    @Test
    void shouldImportValidRowsAndReportInvalidOnes() {
        csvImportService = new CsvImportService(counterpartyRepository, riskLimitRepository);

        when(counterpartyRepository.findByRicosCode(any())).thenReturn(Optional.empty());
        when(counterpartyRepository.save(any())).thenAnswer(invocation -> {
            Counterparty c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(any(), any())).thenReturn(Optional.empty());

        String csv = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BNP PARIBAS,RICOS48213,FR,Banking,CREDIT,50000000,32000000,EUR\n"
                + "BAD ROW,RICOS00000,FR,Banking,NOT_A_TYPE,50000000,32000000,EUR\n"
                + "ANOTHER BAD,RICOS00001,FR,Banking,CREDIT,notanumber,32000000,EUR\n";

        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = csvImportService.importCsv(file);

        assertThat(summary.getSuccessCount()).isEqualTo(1);
        assertThat(summary.getErrorCount()).isEqualTo(2);
        assertThat(summary.getErrors()).hasSize(2);
    }

    @Test
    void shouldReportMissingHeaderColumns() {
        csvImportService = new CsvImportService(counterpartyRepository, riskLimitRepository);

        String csv = "name,ricosCode,country\nBNP,RICOS1,FR\n";
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = csvImportService.importCsv(file);

        assertThat(summary.getSuccessCount()).isEqualTo(0);
        assertThat(summary.getErrorCount()).isGreaterThan(0);
    }
}
