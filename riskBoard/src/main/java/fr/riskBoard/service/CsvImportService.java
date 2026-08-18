package fr.riskBoard.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import fr.riskBoard.entities.Counterparty;
import fr.riskBoard.enums.LimitType;
import fr.riskBoard.entities.RiskLimit;
import fr.riskBoard.dto.ImportError;
import fr.riskBoard.dto.ImportSummary;
import fr.riskBoard.repository.CounterpartyRepository;
import fr.riskBoard.repository.RiskLimitRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsvImportService {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "name", "ricosCode", "country", "sector", "limitType", "maxAmount", "usedAmount", "currency");

    private static final Pattern RICOS_CODE_PATTERN = Pattern.compile("^RICOS\\d{5}$");

    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^[A-Za-z]{2}$");

    private final CounterpartyRepository counterpartyRepository;
    private final RiskLimitRepository riskLimitRepository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public ImportSummary importCsv(MultipartFile file) {
        List<ImportError> errors = new ArrayList<>();
        int successCount = 0;

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setIgnoreSurroundingSpaces(true)
                    .build();

            try (CSVParser parser = CSVParser.parse(reader, format)) {
                for (String header : REQUIRED_HEADERS) {
                    if (!parser.getHeaderNames().contains(header)) {
                        errors.add(ImportError.builder()
                                .line(1)
                                .message("Colonne manquante dans l'en-tête : " + header)
                                .build());
                    }
                }

                if (!errors.isEmpty()) {
                    return ImportSummary.builder().successCount(0).errorCount(errors.size()).errors(errors).build();
                }

                for (CSVRecord record : parser) {
                    int line = (int) record.getRecordNumber() + 1;
                    try {
                        // REQUIRES_NEW : les deux save() (Counterparty puis RiskLimit) de
                        // cette ligne committent ou annulent ensemble, dans une transaction
                        // dédiée à cette seule ligne - sans affecter les lignes déjà
                        // traitées ni bloquer le traitement des suivantes en cas d'échec.
                        requiresNewTransactionTemplate.executeWithoutResult(status -> importRecord(record));
                        successCount++;
                    } catch (Exception e) {
                        errors.add(ImportError.builder().line(line).message(e.getMessage()).build());
                    }
                }
            }
        } catch (IOException e) {
            errors.add(ImportError.builder().line(0).message("Impossible de lire le fichier : " + e.getMessage()).build());
        }

        return ImportSummary.builder()
                .successCount(successCount)
                .errorCount(errors.size())
                .errors(errors)
                .build();
    }

    private void importRecord(CSVRecord record) {
        String name = requireNonBlank(record, "name");
        String ricosCode = requireValidRicosCode(record);
        String country = requireValidCountry(record);
        String sector = requireNonBlank(record, "sector");
        LimitType limitType = parseLimitType(record.get("limitType"));
        BigDecimal maxAmount = parsePositiveAmount(record.get("maxAmount"), "maxAmount");
        BigDecimal usedAmount = parseNonNegativeAmount(record.get("usedAmount"), "usedAmount");
        String currency = requireValidCurrency(record);

        Counterparty counterparty = counterpartyRepository.findByRicosCode(ricosCode)
                .orElseGet(() -> Counterparty.builder().ricosCode(ricosCode).build());
        counterparty.setName(name);
        counterparty.setCountry(country);
        counterparty.setSector(sector);
        final Counterparty savedCounterparty = counterpartyRepository.save(counterparty);

        RiskLimit riskLimit = riskLimitRepository
                .findByCounterpartyIdAndLimitType(savedCounterparty.getId(), limitType)
                .orElseGet(() -> RiskLimit.builder().counterparty(savedCounterparty).limitType(limitType).build());
        riskLimit.setMaxAmount(maxAmount);
        riskLimit.setUsedAmount(usedAmount);
        riskLimit.setCurrency(currency);
        riskLimit.setLastUpdated(LocalDateTime.now());
        riskLimitRepository.save(riskLimit);
    }

    private String requireNonBlank(CSVRecord record, String field) {
        String value = record.isMapped(field) ? record.get(field) : null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Champ '" + field + "' manquant ou vide");
        }
        return value.trim();
    }

    private String requireValidRicosCode(CSVRecord record) {
        String value = requireNonBlank(record, "ricosCode");
        if (!RICOS_CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Champ 'ricosCode' invalide : '" + value + "' (format attendu : RICOS suivi de 5 chiffres)");
        }
        return value;
    }

    private String requireValidCountry(CSVRecord record) {
        String value = requireNonBlank(record, "country");
        if (!COUNTRY_CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Champ 'country' invalide : '" + value + "' (code pays à 2 lettres attendu)");
        }
        return value;
    }

    private String requireValidCurrency(CSVRecord record) {
        String value = requireNonBlank(record, "currency");
        try {
            Currency.getInstance(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Champ 'currency' invalide : '" + value + "' (code ISO 4217 attendu, ex. EUR, USD, GBP)");
        }
        return value;
    }

    private LimitType parseLimitType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Champ 'limitType' manquant ou vide");
        }
        try {
            return LimitType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Type de limite invalide : '" + raw + "' (attendu CREDIT, MARKET ou LIQUIDITY)");
        }
    }

    private BigDecimal parsePositiveAmount(String raw, String field) {
        BigDecimal value = parseAmount(raw, field);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Champ '" + field + "' doit être strictement positif");
        }
        return value;
    }

    private BigDecimal parseNonNegativeAmount(String raw, String field) {
        BigDecimal value = parseAmount(raw, field);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Champ '" + field + "' ne peut pas être négatif");
        }
        return value;
    }

    private BigDecimal parseAmount(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Champ '" + field + "' manquant ou vide");
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Champ '" + field + "' n'est pas un nombre valide : '" + raw + "'");
        }
    }
}
