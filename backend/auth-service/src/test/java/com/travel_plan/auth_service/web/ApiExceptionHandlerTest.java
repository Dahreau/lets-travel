package com.travel_plan.auth_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// fix/audit-gaps : couvre les handlers ajoutes pour combler le trou de couverture Sonar
// (auth-service etait le seul service sans ce filet, cf. troubleshooting.md #35).
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handlesDataIntegrityViolationAsConflict() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("duplicate username");

        ResponseEntity<ApiExceptionHandler.ErrorResponse> response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Nom d'utilisateur deja utilise");
    }

    @Test
    void handlesValidationErrorsAsBadRequest() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("registerRequest", "username", "must not be blank")));

        ResponseEntity<ApiExceptionHandler.ErrorResponse> response = handler.handleValidation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("username: must not be blank");
    }

    @Test
    void handlesMalformedRequestBodyAsBadRequest() {
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("JSON parse error");

        ResponseEntity<ApiExceptionHandler.ErrorResponse> response = handler.handleMalformedRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Corps de requete invalide ou mal forme");
    }

    @Test
    void handlesTypeMismatchAsBadRequest() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("id");
        when(exception.getMessage()).thenReturn("invalid UUID");

        ResponseEntity<ApiExceptionHandler.ErrorResponse> response = handler.handleTypeMismatch(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Valeur invalide pour le parametre 'id'");
    }

    @Test
    void handlesUnexpectedExceptionAsInternalServerError() {
        ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Erreur inattendue");
    }
}
