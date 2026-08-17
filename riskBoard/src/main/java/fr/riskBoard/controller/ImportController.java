package fr.riskBoard.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fr.riskBoard.dto.ImportSummary;
import fr.riskBoard.service.CsvImportService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final CsvImportService csvImportService;

    @PostMapping(value = "/risk-limits", consumes = "multipart/form-data")
    public ImportSummary importRiskLimits(@RequestParam("file") MultipartFile file) {
        return csvImportService.importCsv(file);
    }
}
