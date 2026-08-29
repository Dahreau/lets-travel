package com.travel_plan.api_gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleUpstreamFailureReturns502WithMessage() {
        ResourceAccessException ex = new ResourceAccessException("Connection refused");

        ResponseEntity<Map<String, Object>> response = handler.handleUpstreamFailure(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody())
                .containsEntry("status", HttpStatus.BAD_GATEWAY.value())
                .containsEntry("message", "An upstream service rejected the request or was unreachable");
        assertThat(response.getBody()).containsKey("timestamp");
    }

    @Test
    void handleUnexpectedReturns500WithGenericMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .containsEntry("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
                .containsEntry("message", "Unexpected error");
    }
}
