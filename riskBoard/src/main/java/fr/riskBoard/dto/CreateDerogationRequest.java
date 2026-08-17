package fr.riskBoard.dto;

import java.math.BigDecimal;

import fr.riskBoard.enums.LimitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDerogationRequest {

    @NotNull
    private Long counterpartyId;

    @NotNull
    private LimitType limitType;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal amount;

    @NotNull
    @Size(min = 20)
    private String reason;

    @NotNull
    @Size(min = 6)
    private String requestedBy;
}
