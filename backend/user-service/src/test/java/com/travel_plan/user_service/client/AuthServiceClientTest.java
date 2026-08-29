package com.travel_plan.user_service.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel_plan.user_service.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

// fix/audit-gaps : meme convention que TravelServiceClientTest (mock de la chaine fluide RestClient).
@SuppressWarnings({"unchecked", "rawtypes"})
class AuthServiceClientTest {

    private static final String DELETE_URI = "/api/auth/accounts/by-user/{userId}";

    private final RestClient restClient = mock(RestClient.class);
    private final AuthServiceClient client = new AuthServiceClient(restClient);

    @Test
    void deleteAccountByUserIdThrowsWhenAuthorizationHeaderIsMissing() {
        assertThatThrownBy(() -> client.deleteAccountByUserId(UUID.randomUUID(), null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.deleteAccountByUserId(UUID.randomUUID(), " "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteAccountByUserIdCallsAuthService() {
        UUID userId = UUID.randomUUID();
        RestClient.ResponseSpec responseSpec = stubDelete(userId);

        client.deleteAccountByUserId(userId, "Bearer admin-token");

        verify(responseSpec).toBodilessEntity();
    }

    @Test
    void deleteAccountByUserIdSwallows404() {
        UUID userId = UUID.randomUUID();
        RestClient.ResponseSpec responseSpec = stubDelete(userId);
        when(responseSpec.toBodilessEntity()).thenThrow(new HttpClientErrorException(HttpStatusCode.valueOf(404)));

        client.deleteAccountByUserId(userId, "Bearer admin-token");
    }

    @Test
    void deleteAccountByUserIdRethrowsNon404Errors() {
        UUID userId = UUID.randomUUID();
        RestClient.ResponseSpec responseSpec = stubDelete(userId);
        when(responseSpec.toBodilessEntity()).thenThrow(new HttpServerErrorException(HttpStatusCode.valueOf(503)));

        assertThatThrownBy(() -> client.deleteAccountByUserId(userId, "Bearer admin-token"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void createAccountCallsAuthService() {
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri("/api/auth/accounts")).thenReturn(bodySpec);
        when(bodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(bodySpec);
        when(bodySpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);

        client.createAccount("traveler1", "secret", Role.TRAVELER, UUID.randomUUID(), "Bearer admin-token");

        verify(responseSpec).toBodilessEntity();
    }

    private RestClient.ResponseSpec stubDelete(UUID userId) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClient.delete()).thenReturn(uriSpec);
        when(uriSpec.uri(DELETE_URI, userId)).thenReturn(headersSpec);
        when(headersSpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        return responseSpec;
    }
}
