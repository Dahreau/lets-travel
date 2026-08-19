package com.travel_plan.api_gateway.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.api_gateway.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

class JwtGatewayFilterFunctionTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final JwtGatewayFilterFunction filter = new JwtGatewayFilterFunction(jwtService);

    @SuppressWarnings("unchecked")
    private final HandlerFunction<ServerResponse> next = mock(HandlerFunction.class);

    @Test
    void rejectsRequestWithoutAuthorizationHeader() throws Exception {
        ServerRequest request = mockRequestWithHeader(null);

        ServerResponse response = filter.filter(request, next);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(next, never()).handle(any());
    }

    @Test
    void rejectsRequestWithNonBearerHeader() throws Exception {
        ServerRequest request = mockRequestWithHeader("Basic dXNlcjpwYXNz");

        ServerResponse response = filter.filter(request, next);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(next, never()).handle(any());
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        ServerRequest request = mockRequestWithHeader("Bearer bad-token");
        when(jwtService.validateAndParse("bad-token")).thenThrow(new MalformedJwtException("bad token"));

        ServerResponse response = filter.filter(request, next);

        assertThat(response.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(next, never()).handle(any());
    }

    @Test
    void forwardsRequestWithValidToken() throws Exception {
        ServerRequest request = mockRequestWithHeader("Bearer good-token");
        Claims claims = mock(Claims.class);
        when(jwtService.validateAndParse("good-token")).thenReturn(claims);
        ServerResponse downstreamResponse = mock(ServerResponse.class);
        when(next.handle(request)).thenReturn(downstreamResponse);

        ServerResponse response = filter.filter(request, next);

        assertThat(response).isEqualTo(downstreamResponse);
        verify(next).handle(request);
    }

    private ServerRequest mockRequestWithHeader(String authorizationHeader) {
        ServerRequest request = mock(ServerRequest.class);
        ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
        when(request.headers()).thenReturn(headers);
        when(headers.firstHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authorizationHeader);
        return request;
    }
}
