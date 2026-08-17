package fr.riskBoard.dto;

import java.math.BigDecimal;

import fr.riskBoard.enums.LimitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class RiskLimitDto {
    private Long id;
    private Long counterpartyId;
    private LimitType limitType;
    private BigDecimal maxAmount;
    private BigDecimal usedAmount;
    private String currency;
}
