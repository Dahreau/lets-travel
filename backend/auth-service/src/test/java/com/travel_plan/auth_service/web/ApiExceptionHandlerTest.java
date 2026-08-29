package com.travel_plan.auth_service.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.travel_plan.auth_service.exception.AccountNotFoundException;
import com.travel_plan.auth_service.exception.ForbiddenException;
import com.travel_plan.auth_service.exception.InvalidRegistrationTokenException;
import com.travel_plan.auth_service.exception.UsernameAlreadyTakenException;

// fix/audit-gaps : couvre les handlers ajoutes pour combler le trou de couverture Sonar
// (auth-service etait le seul service sans ce filet, cf. troubleshooting.md #35).
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handlesBadCredentialsAsUnauthorized() {
        ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
                handler.handleBadCredentials(new BadCredentialsException("Identifiants invalides"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Identifiants invalides");
    }

    @Test
    void handlesUsernameAlreadyTakenAsConflict() {
        ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
                handler.handleUsernameAlreadyTaken(new UsernameAlreadyTakenException("Nom d'utilisateur deja pris"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Nom d'utilisateur deja pris");
    }

    @Test
    void handlesAccountNotFoundAsNotFound() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
                handler.handleAccountNotFound(new AccountNotFoundException(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains(userId.toString());
    }

    @Test
    void handlesForbiddenAsForbidden() {
        ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
                handler.handleForbidden(new ForbiddenException("Acces refuse"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("Acces refuse");
    }

    @Test
    void handlesInvalidRegistrationTokenAsBadRequest() {
        ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
                handler.handleInvalidRegistrationToken(new InvalidRegistrationTokenException("Jeton invalide"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Jeton invalide");
    }

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
