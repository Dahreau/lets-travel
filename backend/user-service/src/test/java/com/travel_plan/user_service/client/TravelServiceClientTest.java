package com.travel_plan.user_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.user_service.client.TravelServiceClient.SubscriberCheckResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// fix/audit-gaps : troubleshooting.md #38 - meme convention que payment-service
// TravelServiceClientTest (mock de la chaine fluide RestClient).
@SuppressWarnings({"unchecked", "rawtypes"})
class TravelServiceClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final TravelServiceClient client = new TravelServiceClient(restClient);

    @Test
    void returnsTrueWhenTravelServiceConfirmsSubscription() {
        UUID travelerId = UUID.randomUUID();
        stubGetResponse(travelerId, new SubscriberCheckResponse(true));

        boolean result = client.isSubscriberOfCallingManager(travelerId, "Bearer manager-token");

        assertThat(result).isTrue();
    }

    @Test
    void returnsFalseWhenTravelServiceDeniesSubscription() {
        UUID travelerId = UUID.randomUUID();
        stubGetResponse(travelerId, new SubscriberCheckResponse(false));

        boolean result = client.isSubscriberOfCallingManager(travelerId, "Bearer manager-token");

        assertThat(result).isFalse();
    }

    @Test
    void returnsFalseWhenAuthorizationHeaderIsMissing() {
        boolean result = client.isSubscriberOfCallingManager(UUID.randomUUID(), null);

        assertThat(result).isFalse();
    }

    // Fail-closed (voir TravelServiceClient) : travel-service injoignable ou en erreur ->
    // "pas abonne", jamais une exception qui remonterait en 500 sur GET /api/users/{id}.
    @Test
    void returnsFalseWhenTravelServiceIsUnreachable() {
        UUID travelerId = UUID.randomUUID();
        stubGetError(travelerId, new ResourceAccessException("connect timed out"));

        boolean result = client.isSubscriberOfCallingManager(travelerId, "Bearer manager-token");

        assertThat(result).isFalse();
    }

    @Test
    void returnsFalseWhenTravelServiceReturnsServerError() {
        UUID travelerId = UUID.randomUUID();
        stubGetError(travelerId, new HttpServerErrorException(HttpStatusCode.valueOf(503)));

        boolean result = client.isSubscriberOfCallingManager(travelerId, "Bearer manager-token");

        assertThat(result).isFalse();
    }

    private void stubGetResponse(UUID travelerId, SubscriberCheckResponse response) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/travels/managers/me/subscribers/{travelerId}", travelerId)).thenReturn(headersSpec);
        when(headersSpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(SubscriberCheckResponse.class)).thenReturn(response);
    }

    private void stubGetError(UUID travelerId, RuntimeException error) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/travels/managers/me/subscribers/{travelerId}", travelerId)).thenReturn(headersSpec);
        when(headersSpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(SubscriberCheckResponse.class)).thenThrow(error);
    }
}
