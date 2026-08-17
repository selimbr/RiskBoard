package fr.riskBoard.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.riskBoard.dto.CounterpartyDto;
import fr.riskBoard.repository.CounterpartyRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {

    private final CounterpartyRepository counterpartyRepository;

    @GetMapping
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
