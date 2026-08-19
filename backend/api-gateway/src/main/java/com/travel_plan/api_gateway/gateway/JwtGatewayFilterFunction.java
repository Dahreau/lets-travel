package com.travel_plan.api_gateway.gateway;

import com.travel_plan.api_gateway.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class JwtGatewayFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtGatewayFilterFunction(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {
        String header = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            jwtService.validateAndParse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
        }

        return next.handle(request);
    }
}
