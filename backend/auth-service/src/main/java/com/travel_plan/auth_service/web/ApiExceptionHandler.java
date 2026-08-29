package com.travel_plan.auth_service.web;

import com.travel_plan.auth_service.exception.AccountNotFoundException;
import com.travel_plan.auth_service.exception.ForbiddenException;
import com.travel_plan.auth_service.exception.InvalidRegistrationTokenException;
import com.travel_plan.auth_service.exception.UsernameAlreadyTakenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(exception.getMessage()));
    }

    // feat/traveler-experience : inscription publique.
    @ExceptionHandler(UsernameAlreadyTakenException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyTaken(UsernameAlreadyTakenException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(exception.getMessage()));
    }

    // fix/audit-gaps : filet manquant jusqu'ici sur ce service - une course entre 2 inserts
    // concurrents du meme username (AdminSeeder sur 2 replicas, ou 2 register() paralleles)
    // remontait en 500 brut au lieu d'un 409 clair.
    // fix/audit-gaps (troubleshooting.md #41) : DELETE /api/auth/accounts/by-user/{userId} -
    // appele par un userId deja supprime (ou jamais cree, cas ADMIN sans fiche User).
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(exception.getMessage()));
    }

    // fix/audit-gaps (troubleshooting.md #41) : appelant ni ADMIN ni proprietaire du userId cible
    // sur DELETE /api/auth/accounts/by-user/{userId} - meme garde que le fix IDOR #38.
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(InvalidRegistrationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRegistrationToken(InvalidRegistrationTokenException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("Nom d'utilisateur deja utilise"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((first, second) -> first + ", " + second)
                .orElse("Echec de la validation");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    // JSON invalide ou enum inconnue echoue avant Bean Validation -> 400, pas 500.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException exception) {
        log.warn("Malformed request body: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Corps de requete invalide ou mal forme"));
    }

    // ID d'URL non-UUID -> 400, pas 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        log.warn("Invalid path parameter '{}': {}", exception.getName(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("Valeur invalide pour le parametre '" + exception.getName() + "'"));
    }

    // Filet de securite : toute exception imprevue reste un 500 clair et loggue, pas un
    // silence total (seul service des 5 sans ce filet avant ce fix).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unhandled exception while processing request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Erreur inattendue"));
    }

    public record ErrorResponse(String message) {
    }
}
