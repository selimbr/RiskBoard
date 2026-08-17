package fr.riskBoard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import fr.riskBoard.enums.DerogationStatus;
import fr.riskBoard.enums.LimitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DerogationRequestDto {
    private Long id;
    private Long counterpartyId;
    private String counterpartyName;
    private LimitType limitType;
    private String requestedBy;
    private BigDecimal amount;
    private String reason;
    private DerogationStatus status;
    private LocalDateTime createdAt;
}
