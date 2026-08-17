package fr.riskBoard.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import fr.riskBoard.enums.LimitType;
import fr.riskBoard.dto.RiskLimitDashboardRow;
import fr.riskBoard.dto.RiskLimitDto;
import fr.riskBoard.dto.SectorTypeAggregationRow;
import fr.riskBoard.service.RiskLimitService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/risk-limits")
@RequiredArgsConstructor
public class RiskLimitController {

    private final RiskLimitService riskLimitService;

    @GetMapping("/dashboard")
    public List<RiskLimitDashboardRow> getDashboard() {
        return riskLimitService.getDashboard();
    }

    @GetMapping("/aggregation/sector")
    public Map<String, BigDecimal> getSectorExposure() {
        return riskLimitService.getSectorExposure();
    }

    @GetMapping("/aggregation")
    public List<SectorTypeAggregationRow> getSectorExposureByType(@RequestParam LimitType limitType) {
        return riskLimitService.getSectorExposureByType(limitType);
    }

    @GetMapping("/counterparty/{counterpartyId}/type/{limitType}")
    public ResponseEntity<RiskLimitDto> getLimit(@PathVariable Long counterpartyId, @PathVariable LimitType limitType) {
        return riskLimitService.findLimit(counterpartyId, limitType)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
