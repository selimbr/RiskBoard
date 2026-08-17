package fr.riskBoard.dto;

import java.math.BigDecimal;

import fr.riskBoard.enums.AlertLevel;
import fr.riskBoard.enums.LimitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RiskLimitDashboardRow {
    private Long riskLimitId;
    private Long counterpartyId;
    private String counterpartyName;
    private LimitType limitType;
    private String sector;
    private BigDecimal maxAmount;
    private BigDecimal usedAmount;
    private BigDecimal usageRate;
    private AlertLevel alertLevel;
}
