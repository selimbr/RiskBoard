package fr.riskBoard.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ImportSummary {
    private int successCount;
    private int errorCount;
    private List<ImportError> errors;
}
