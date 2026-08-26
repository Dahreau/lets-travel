package com.travel_plan.api_gateway.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class RouteConfig {

    private static final String AUTH_SERVICE = "auth-service";
    private static final String USER_SERVICE = "user-service";
    private static final String TRAVEL_SERVICE = "travel-service";
    private static final String PAYMENT_SERVICE = "payment-service";

    @Bean
    public RouterFunction<ServerResponse> authServiceRoutes(JwtGatewayFilterFunction jwtFilter) {
        // /register public au meme titre que /login (2e etape de l'inscription publique
        // traveler, voir AuthController) - sinon jwtFilter la bloque en 401 avant meme
        // d'atteindre le permitAll() de auth-service.
        RouterFunction<ServerResponse> publicRoutes = route("auth-service-public")
                .POST("/api/auth/login", http())
                .POST("/api/auth/register", http())
                .filter(lb(AUTH_SERVICE))
                .build();

        RouterFunction<ServerResponse> protectedRoutes = route(AUTH_SERVICE)
                .route(path("/api/auth/**"), http())
                .filter(lb(AUTH_SERVICE))
                .filter(jwtFilter)
                .build();

        return publicRoutes.and(protectedRoutes);
    }

    @Bean
    public RouterFunction<ServerResponse> userServiceRoutes(JwtGatewayFilterFunction jwtFilter) {
        // /register public (1ere etape de l'inscription publique traveler) - meme raison
        // que auth-service-public ci-dessus.
        RouterFunction<ServerResponse> publicRoutes = route("user-service-public")
                .POST("/api/users/register", http())
                .filter(lb(USER_SERVICE))
                .build();

        RouterFunction<ServerResponse> protectedRoutes = route(USER_SERVICE)
                .route(path("/api/users/**"), http())
                .filter(lb(USER_SERVICE))
                .filter(jwtFilter)
                .build();

        return publicRoutes.and(protectedRoutes);
    }

    @Bean
    public RouterFunction<ServerResponse> travelServiceRoutes(JwtGatewayFilterFunction jwtFilter) {
        return route(TRAVEL_SERVICE)
                .route(path("/api/travels/**"), http())
                .filter(lb(TRAVEL_SERVICE))
                .filter(jwtFilter)
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> paymentServiceRoutes(JwtGatewayFilterFunction jwtFilter) {
        RouterFunction<ServerResponse> payments = route("payment-service-payments")
                .route(path("/api/payments/**"), http())
                .filter(lb(PAYMENT_SERVICE))
                .filter(jwtFilter)
                .build();

        RouterFunction<ServerResponse> paymentMethods = route("payment-service-payment-methods")
                .route(path("/api/payment-methods/**"), http())
                .filter(lb(PAYMENT_SERVICE))
                .filter(jwtFilter)
                .build();

        return payments.and(paymentMethods);
    }
}
