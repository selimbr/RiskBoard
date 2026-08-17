package fr.riskBoard.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DerogationEligibility {
    private boolean allowed;
    private BigDecimal maxAllowedAmount;
}
