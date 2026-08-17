package fr.riskBoard.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.repository.CounterpartyRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CounterpartyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Test
    void shouldReturnAllPersistedCounterparties() throws Exception {
        counterpartyRepository.save(Counterparty.builder()
                .name("BNP PARIBAS").ricosCode("RICOS48213").country("FR").sector("Banking").build());
        counterpartyRepository.save(Counterparty.builder()
                .name("SHELL PLC").ricosCode("RICOS68792").country("UK").sector("Energy").build());

        mockMvc.perform(get("/api/counterparties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.ricosCode == 'RICOS48213')].name").value("BNP PARIBAS"));
    }

    @Test
    void shouldReturnEmptyListWhenNoCounterpartyExists() throws Exception {
        mockMvc.perform(get("/api/counterparties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
