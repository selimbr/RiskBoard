package fr.riskBoard.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import fr.riskBoard.dto.CreateDerogationRequest;
import fr.riskBoard.dto.DerogationRequestDto;
import fr.riskBoard.service.DerogationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/derogations")
@RequiredArgsConstructor
public class DerogationRequestController {

    private final DerogationRequestService derogationRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DerogationRequestDto create(@Valid @RequestBody CreateDerogationRequest request) {
        return derogationRequestService.create(request);
    }

    @GetMapping("/pending")
    public List<DerogationRequestDto> listPending() {
        return derogationRequestService.listPending();
    }

    @PostMapping("/{id}/approve")
    public DerogationRequestDto approve(@PathVariable Long id) {
        return derogationRequestService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public DerogationRequestDto reject(@PathVariable Long id) {
        return derogationRequestService.reject(id);
    }
}
