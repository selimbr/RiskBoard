package fr.riskBoard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.riskBoard.dto.CounterpartyDto;
import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.repository.CounterpartyRepository;

@ExtendWith(MockitoExtension.class)
class CounterpartyServiceTest {

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @InjectMocks
    private CounterpartyService service;

    @Test
    void shouldMapAllCounterpartiesToDto() {
        Counterparty bnp = Counterparty.builder()
                .id(1L)
                .name("BNP PARIBAS")
                .ricosCode("RICOS48213")
                .country("FR")
                .sector("Banking")
                .build();
        when(counterpartyRepository.findAll()).thenReturn(List.of(bnp));

        List<CounterpartyDto> result = service.getAll();

        assertThat(result).hasSize(1);
        CounterpartyDto dto = result.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("BNP PARIBAS");
        assertThat(dto.getRicosCode()).isEqualTo("RICOS48213");
        assertThat(dto.getCountry()).isEqualTo("FR");
        assertThat(dto.getSector()).isEqualTo("Banking");
    }

    @Test
    void shouldReturnEmptyListWhenNoCounterpartyExists() {
        when(counterpartyRepository.findAll()).thenReturn(List.of());

        assertThat(service.getAll()).isEmpty();
    }
}
