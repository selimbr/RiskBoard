package fr.riskBoard.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldTranslateDataIntegrityViolationTo400WithoutLeakingSqlDetails() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement [Value too long for column \"REASON VARCHAR(2000)\"]");

        ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).doesNotContain("VARCHAR", "SQL", "statement");
    }
}
