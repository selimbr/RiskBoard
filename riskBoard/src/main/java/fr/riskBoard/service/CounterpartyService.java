package fr.riskBoard.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.riskBoard.dto.CounterpartyDto;
import fr.riskBoard.repository.CounterpartyRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CounterpartyService {

    private final CounterpartyRepository counterpartyRepository;

    @Transactional(readOnly = true)
    public List<CounterpartyDto> getAll() {
        return counterpartyRepository.findAll().stream()
                .map(c -> CounterpartyDto.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .ricosCode(c.getRicosCode())
                        .country(c.getCountry())
                        .sector(c.getSector())
                        .build())
                .toList();
    }
}
