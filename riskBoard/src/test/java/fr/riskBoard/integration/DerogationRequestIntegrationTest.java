package fr.riskBoard.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import fr.riskBoard.dto.CreateDerogationRequest;
import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.entities.RiskLimit;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.RiskLimitRepository;

/**
 * Vérifie le workflow de dérogation bout en bout via HTTP, y compris la règle
 * métier des 150% appliquée côté backend (voir DerogationRequestServiceTest
 * pour la version unitaire de cette même règle). GET /pending en particulier
 * exerce le même chemin que le bug corrigé dans DerogationRequestRepository
 * (LazyInitializationException: no session) - une régression sur le "join
 * fetch" y ferait échouer ce test en conditions réelles, pas seulement au
 * niveau repository.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DerogationRequestIntegrationTest {

    private static final String VALID_REASON = "Demande urgente pour financement exceptionnel du client";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private RiskLimitRepository riskLimitRepository;

    private Counterparty counterparty;

    @BeforeEach
    void setUp() {
        counterparty = counterpartyRepository.save(Counterparty.builder()
                .name("BNP PARIBAS")
                .ricosCode("RICOS48213")
                .country("FR")
                .sector("Banking")
                .build());

        riskLimitRepository.save(RiskLimit.builder()
                .counterparty(counterparty)
                .limitType(LimitType.CREDIT)
                .maxAmount(BigDecimal.valueOf(1_000_000))
                .usedAmount(BigDecimal.valueOf(500_000))
                .currency("EUR")
                .lastUpdated(LocalDateTime.now())
                .build());
    }

    private CreateDerogationRequest requestFor(BigDecimal amount) {
        CreateDerogationRequest request = new CreateDerogationRequest();
        request.setCounterpartyId(counterparty.getId());
        request.setLimitType(LimitType.CREDIT);
        request.setAmount(amount);
        request.setReason(VALID_REASON);
        request.setRequestedBy("j.dupont");
        return request;
    }

    @Test
    void shouldCreateDerogationAndListItAsPending() throws Exception {
        mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFor(BigDecimal.valueOf(1_200_000)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.counterpartyName").value("BNP PARIBAS"));

        mockMvc.perform(get("/api/derogations/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].counterpartyName").value("BNP PARIBAS"))
                .andExpect(jsonPath("$[0].amount").value(1_200_000));
    }

    @Test
    void shouldReturnEligibleTrueWhenAmountWithin150Percent() throws Exception {
        mockMvc.perform(get("/api/derogations/eligibility")
                        .param("counterpartyId", counterparty.getId().toString())
                        .param("limitType", "CREDIT")
                        .param("amount", "1200000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.maxAllowedAmount").value(1_500_000));
    }

    @Test
    void shouldReturnEligibleFalseWhenAmountAbove150Percent() throws Exception {
        mockMvc.perform(get("/api/derogations/eligibility")
                        .param("counterpartyId", counterparty.getId().toString())
                        .param("limitType", "CREDIT")
                        .param("amount", "2000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.maxAllowedAmount").value(1_500_000));
    }

    @Test
    void shouldReturn404WhenCheckingEligibilityForLimitTypeWithoutRiskLimit() throws Exception {
        mockMvc.perform(get("/api/derogations/eligibility")
                        .param("counterpartyId", counterparty.getId().toString())
                        .param("limitType", "LIQUIDITY")
                        .param("amount", "100000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectRequestAbove150PercentOfLimit() throws Exception {
        mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFor(BigDecimal.valueOf(2_000_000)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("150%")));
    }

    @Test
    void shouldRejectWhenNoRiskLimitExistsForRequestedType() throws Exception {
        mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                requestForType(LimitType.LIQUIDITY, BigDecimal.valueOf(100_000)))))
                .andExpect(status().isBadRequest());
    }

    private CreateDerogationRequest requestForType(LimitType limitType, BigDecimal amount) {
        CreateDerogationRequest request = requestFor(amount);
        request.setLimitType(limitType);
        return request;
    }

    @Test
    void shouldRejectWhenRequiredFieldsAreInvalid() throws Exception {
        CreateDerogationRequest request = requestFor(BigDecimal.valueOf(100_000));
        request.setReason("trop court"); // < 20 caractères, viole @Size(min = 20)

        mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    @Test
    void shouldReturn400InsteadOf500WhenReasonExceedsColumnLength() throws Exception {
        CreateDerogationRequest request = requestFor(BigDecimal.valueOf(100_000));
        request.setReason("a".repeat(5000));

        mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    @Test
    void shouldReturn400WhenRequestedByExceedsColumnLength() throws Exception {
        CreateDerogationRequest request = requestFor(BigDecimal.valueOf(100_000));
        request.setRequestedBy("a".repeat(300));

        mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.requestedBy").exists());
    }

    @Test
    void shouldApproveDerogationAndRemoveItFromPendingList() throws Exception {
        String body = mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFor(BigDecimal.valueOf(600_000)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/derogations/{id}/approve", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/derogations/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldRejectDerogationAndRemoveItFromPendingList() throws Exception {
        String body = mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFor(BigDecimal.valueOf(600_000)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/derogations/{id}/reject", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/derogations/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldReturn404WhenApprovingUnknownDerogation() throws Exception {
        mockMvc.perform(post("/api/derogations/{id}/approve", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectRejectingAnAlreadyApprovedDerogation() throws Exception {
        // Scénario signalé : un POST /reject sur une demande déjà APPROVED ne
        // doit pas la repasser en REJECTED.
        String body = mockMvc.perform(post("/api/derogations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestFor(BigDecimal.valueOf(600_000)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(post("/api/derogations/{id}/approve", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/derogations/{id}/reject", id))
                .andExpect(status().isBadRequest());
    }
}
