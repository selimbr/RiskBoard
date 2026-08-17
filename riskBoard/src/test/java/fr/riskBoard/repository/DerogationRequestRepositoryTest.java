package fr.riskBoard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.entities.DerogationRequest;
import fr.riskBoard.enums.DerogationStatus;
import fr.riskBoard.enums.LimitType;

/**
 * @DataJpaTest enveloppe chaque test dans une transaction (rollback auto), ce qui
 * garderait la session Hibernate ouverte et masquerait le bug "no session" de
 * DerogationRequestService#listPending() (non-@Transactional, exécuté avec
 * spring.jpa.open-in-view=false). Propagation.NOT_SUPPORTED désactive cette
 * transaction de test pour reproduire fidèlement le contexte réel : chaque appel
 * repository ouvre et ferme sa propre session, comme en production.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DerogationRequestRepositoryTest {

    @Autowired
    private DerogationRequestRepository derogationRequestRepository;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Test
    void findByStatusShouldEagerlyLoadCounterpartyOutsideAnyOpenSession() {
        Counterparty counterparty = counterpartyRepository.save(Counterparty.builder()
                .name("BNP PARIBAS")
                .ricosCode("RICOS48213")
                .country("FR")
                .sector("Banking")
                .build());

        derogationRequestRepository.save(DerogationRequest.builder()
                .counterparty(counterparty)
                .limitType(LimitType.CREDIT)
                .requestedBy("j.dupont")
                .amount(BigDecimal.valueOf(100_000))
                .reason("Demande urgente pour financement exceptionnel du client")
                .status(DerogationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        List<DerogationRequest> pending = derogationRequestRepository.findByStatus(DerogationStatus.PENDING);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getCounterparty().getName()).isEqualTo("BNP PARIBAS");
    }

    @Test
    void findByStatusShouldReturnEmptyListWhenNoneMatch() {
        List<DerogationRequest> approved = derogationRequestRepository.findByStatus(DerogationStatus.APPROVED);

        assertThat(approved).isEmpty();
    }
}
