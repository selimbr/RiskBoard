package fr.riskBoard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import fr.riskBoard.dto.ImportSummary;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.RiskLimitRepository;

/**
 * Vérifie que CsvImportService.importRecord() est atomique ligne par ligne :
 * si le save() du RiskLimit échoue après que celui du Counterparty ait déjà
 * commité, aucune contrepartie orpheline ne doit rester en base.
 *
 * @DataJpaTest (base H2 embarquée, isolée du "riskboard-test" partagé par les
 * @SpringBootTest) + Propagation.NOT_SUPPORTED pour désactiver la transaction
 * de test qui, sinon, engloberait les deux save() dans UNE seule transaction
 * (celle du test) et masquerait complètement le bug - exactement comme pour
 * DerogationRequestRepositoryTest.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CsvImportOrphanRecordTest {

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private RiskLimitRepository riskLimitRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldNotLeaveAnOrphanCounterpartyWhenTheRiskLimitSaveFailsAfterIt() {
        TransactionTemplate requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        CsvImportService csvImportService =
                new CsvImportService(counterpartyRepository, riskLimitRepository, requiresNewTransactionTemplate);

        try {
            // maxAmount a 18 chiffres avant la virgule, dépassant la colonne
            // numeric(19,2) de RiskLimit (17 chiffres entiers max) : le save
            // du Counterparty (1er save de la ligne) réussit et commit, puis
            // celui du RiskLimit (2e save) échoue côté base.
            String csv = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n"
                    + "OVERFLOW CORP,RICOS99999,FR,Banking,CREDIT,999999999999999999,1000,EUR\n";
            MockMultipartFile file = new MockMultipartFile("file", "data.csv", "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8));

            ImportSummary summary = csvImportService.importCsv(file);

            assertThat(summary.getSuccessCount()).isEqualTo(0);
            assertThat(summary.getErrorCount()).isEqualTo(1);
            assertThat(counterpartyRepository.findByRicosCode("RICOS99999")).isEmpty();
        } finally {
            counterpartyRepository.findByRicosCode("RICOS99999").ifPresent(counterpartyRepository::delete);
        }
    }
}
