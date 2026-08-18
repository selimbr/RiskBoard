package fr.riskBoard.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.riskBoard.dto.CounterpartyDto;
import fr.riskBoard.service.CounterpartyService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/counterparties")
@RequiredArgsConstructor
public class CounterpartyController {

    private final CounterpartyService counterpartyService;

    @GetMapping
    public List<CounterpartyDto> getAll() {
        return counterpartyService.getAll();
    }
}
