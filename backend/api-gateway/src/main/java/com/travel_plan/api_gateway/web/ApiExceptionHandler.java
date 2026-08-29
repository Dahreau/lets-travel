package com.travel_plan.api_gateway.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

// fix/audit-gaps (troubleshooting.md #40) : sans ce handler, une panne totale d'un service en
// aval (ses 2 replicas down, timeout, ou tout autre echec du RestClient fourni par
// GatewayHttpClientConfig) remontait comme une 500 Tomcat brute (page HTML par defaut), pas une
// erreur JSON exploitable par le frontend - meme pattern que payment-service ApiExceptionHandler.
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Couvre notamment ResourceAccessException (timeout, connexion refusee, TLS) et toute
    // reponse d'erreur HTTP renvoyee par le service en aval via RestClientProxyExchange.
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamFailure(RestClientException ex) {
        log.warn("Upstream service call failed: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "An upstream service rejected the request or was unreachable");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while routing request", ex);
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
