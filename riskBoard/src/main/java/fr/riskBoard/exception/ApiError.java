package fr.riskBoard.exception;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiError {
    private String message;
    private Map<String, String> fieldErrors;
}
