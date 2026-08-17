package fr.riskBoard.controller;

import fr.riskBoard.dto.CreateDerogationRequest;
import fr.riskBoard.dto.DerogationEligibility;
import fr.riskBoard.dto.DerogationRequestDto;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.service.DerogationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

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

    @GetMapping("/eligibility")
    public DerogationEligibility checkEligibility(
            @RequestParam Long counterpartyId,
            @RequestParam LimitType limitType,
            @RequestParam BigDecimal amount) {
        return derogationRequestService.checkEligibility(counterpartyId, limitType, amount);
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
