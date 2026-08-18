package fr.riskBoard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Mock
    private TransactionTemplate transactionTemplate;

    private CsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        // Simule le comportement réel de TransactionTemplate.executeWithoutResult :
        // exécute le callback immédiatement (pas de vraie base ici) et laisse
        // remonter toute exception qu'il lève, comme le ferait un vrai rollback.
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        csvImportService = new CsvImportService(counterpartyRepository, riskLimitRepository, transactionTemplate);
    }

    @Test
    void shouldImportValidRowsAndReportInvalidOnes() {
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
    void shouldWrapEachRowInItsOwnRequiresNewTransaction() {
        when(counterpartyRepository.findByRicosCode(any())).thenReturn(Optional.empty());
        when(counterpartyRepository.save(any())).thenAnswer(invocation -> {
            Counterparty c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(riskLimitRepository.findByCounterpartyIdAndLimitType(any(), any())).thenReturn(Optional.empty());

        String csv = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BNP PARIBAS,RICOS48213,FR,Banking,CREDIT,50000000,32000000,EUR\n"
                + "SHELL PLC,RICOS68792,UK,Energy,MARKET,18000000,17200000,GBP\n";
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        csvImportService.importCsv(file);

        verify(transactionTemplate, times(2)).executeWithoutResult(any());
    }

    @Test
    void shouldRejectRowsWithInvalidFormats() {
        // Les 3 lignes échouent à la validation de format, avant tout appel
        // repository - aucun stub sur counterpartyRepository/riskLimitRepository
        // n'est donc nécessaire ici.
        String csv = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                + "BAD RICOS,RIC123,FR,Banking,CREDIT,50000000,32000000,EUR\n"
                + "BAD COUNTRY,RICOS48213,FRA,Banking,CREDIT,50000000,32000000,EUR\n"
                + "BAD CURRENCY,RICOS72905,FR,Banking,CREDIT,50000000,32000000,XYZ\n";
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = csvImportService.importCsv(file);

        assertThat(summary.getSuccessCount()).isEqualTo(0);
        assertThat(summary.getErrorCount()).isEqualTo(3);
        assertThat(summary.getErrors().get(0).getMessage()).contains("ricosCode");
        assertThat(summary.getErrors().get(1).getMessage()).contains("country");
        assertThat(summary.getErrors().get(2).getMessage()).contains("currency");
    }

    @Test
    void shouldReportMissingHeaderColumns() {
        String csv = "name,ricosCode,country\nBNP,RICOS1,FR\n";
        MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = csvImportService.importCsv(file);

        assertThat(summary.getSuccessCount()).isEqualTo(0);
        assertThat(summary.getErrorCount()).isGreaterThan(0);
    }
}
