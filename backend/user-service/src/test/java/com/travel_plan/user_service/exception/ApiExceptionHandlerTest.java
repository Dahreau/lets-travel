package com.travel_plan.user_service.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// fix/audit-gaps : verrouille les messages traduits en francais (troubleshooting.md #44.3)
// contre une regression - jusqu'ici seul auth-service testait le contenu de ces messages.
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handlesDataIntegrityViolationAsConflict() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate email");

        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message")).isEqualTo("Email deja utilise");
    }

    @Test
    void handlesValidationErrorsAsBadRequest() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("userRequest", "email", "must not be blank")));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("email: must not be blank");
    }

    @Test
    void handlesMalformedRequestBodyAsBadRequest() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("JSON parse error");

        ResponseEntity<Map<String, Object>> response = handler.handleMalformedRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("Corps de requete invalide ou mal forme");
    }

    @Test
    void handlesTypeMismatchAsBadRequest() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("id");
        when(exception.getMessage()).thenReturn("invalid UUID");

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("message")).isEqualTo("Valeur invalide pour le parametre 'id'");
    }

    @Test
    void handlesUnexpectedExceptionAsInternalServerError() {
        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message")).isEqualTo("Erreur inattendue");
    }
}
