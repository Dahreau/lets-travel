package com.travel_plan.payment_service.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PaymentMethodNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentMethodNotFound(PaymentMethodNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(PaymentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Voyage inexistant cote travel-service (voir client/TravelServiceClient).
    @ExceptionHandler(TravelNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTravelNotFound(TravelNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Voyage existant mais sans prix renseigne cote travel-service (voyages crees avant la
    // migration V4) - pas de montant fiable a facturer.
    @ExceptionHandler(TravelPriceNotSetException.class)
    public ResponseEntity<Map<String, Object>> handleTravelPriceNotSet(TravelPriceNotSetException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPaymentRequest(InvalidPaymentRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidRefundException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefund(InvalidRefundException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Data integrity violation");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((first, second) -> first + ", " + second)
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message);
    }

    // Fournisseur de paiement (Stripe/PayPal) rejete/indisponible, OU appel a travel-service
    // (voir client/TravelServiceClient) rejete/indisponible pour une raison non geree ci-dessus
    // (ex: 5xx, timeout) -> 502 explicite plutot qu'un 500/403.
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamCallFailure(RestClientException ex) {
        log.warn("Upstream service call failed: {}", ex.getMessage());
        return build(
                HttpStatus.BAD_GATEWAY,
                "An upstream service (payment provider or travel-service) rejected the request or was unreachable");
    }

    // @RequestHeader obligatoire absent (ex: Authorization manquant sur POST /api/payments) ->
    // 400, pas 500 : sans ce handler explicite, MissingRequestHeaderException matchait quand
    // meme le handler generique Exception.class ci-dessous (le ExceptionHandlerExceptionResolver
    // s'arrete au premier @ExceptionHandler qui correspond dans ce @RestControllerAdvice, il ne
    // retombe pas sur le traitement 400 par defaut de Spring une fois qu'un handler local existe).
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required header '{}': {}", ex.getHeaderName(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Missing required header '" + ex.getHeaderName() + "'");
    }

    // JSON invalide ou enum inconnue echoue avant Bean Validation -> 400, pas 500.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed or invalid request body");
    }

    // ID d'URL non-UUID (ex: /payments/null) -> 400, pas 500.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Invalid path parameter '{}': {}", ex.getName(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
