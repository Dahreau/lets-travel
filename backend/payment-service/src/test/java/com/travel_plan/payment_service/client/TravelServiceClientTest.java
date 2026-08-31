package com.travel_plan.payment_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.exception.TravelNotFoundException;
import com.travel_plan.payment_service.exception.TravelServiceUnavailableException;
import com.travel_plan.payment_service.exception.TravelPriceNotSetException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Meme convention que Stripe/PayPalPaymentProviderTest : on mocke la chaine fluide de
// RestClient plutot que MockRestServiceServer, pour rester coherent.
@SuppressWarnings({"unchecked", "rawtypes"})
class TravelServiceClientTest {

    private final RestClient restClient = mock(RestClient.class);
    private final TravelServiceClient client = new TravelServiceClient(restClient);

    @Test
    void getPricedTravelReturnsSummaryWhenPriceIsSet() {
        UUID travelId = UUID.randomUUID();
        TravelSummary summary = new TravelSummary(travelId, new BigDecimal("450.00"), "EUR");
        stubGetResponse(travelId, summary);

        TravelSummary result = client.getPricedTravel(travelId, "Bearer test-token");

        assertThat(result).isEqualTo(summary);
    }

    @Test
    void getPricedTravelThrowsWhenPriceNotSet() {
        UUID travelId = UUID.randomUUID();
        stubGetResponse(travelId, new TravelSummary(travelId, null, null));

        assertThatThrownBy(() -> client.getPricedTravel(travelId, "Bearer test-token"))
                .isInstanceOf(TravelPriceNotSetException.class);
    }

    @Test
    void getPricedTravelThrowsTravelNotFoundOn404() {
        UUID travelId = UUID.randomUUID();
        stubGetError(travelId, new HttpClientErrorException(HttpStatusCode.valueOf(404)));

        assertThatThrownBy(() -> client.getPricedTravel(travelId, "Bearer test-token"))
                .isInstanceOf(TravelNotFoundException.class);
    }

    @Test
    void getPricedTravelDoesNotRetryOnNon404ClientError() {
        UUID travelId = UUID.randomUUID();
        RestClientResponseException badRequest = new HttpClientErrorException(HttpStatusCode.valueOf(400));
        stubGetError(travelId, badRequest);

        assertThatThrownBy(() -> client.getPricedTravel(travelId, "Bearer test-token")).isSameAs(badRequest);
    }

    // Fallback (feat/admin-dashboard-overview) : un 503 est transitoire, donc reessaye - et
    // reussit ici des la 2e tentative sans jamais remonter d'erreur a l'appelant.
    @Test
    void getPricedTravelRetriesOnTransientServerErrorThenSucceeds() {
        UUID travelId = UUID.randomUUID();
        TravelSummary summary = new TravelSummary(travelId, new BigDecimal("450.00"), "EUR");
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/travels/{id}", travelId)).thenReturn(headersSpec);
        when(headersSpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(TravelSummary.class))
                .thenThrow(new HttpServerErrorException(HttpStatusCode.valueOf(503)))
                .thenReturn(summary);

        TravelSummary result = client.getPricedTravel(travelId, "Bearer test-token");

        assertThat(result).isEqualTo(summary);
    }

    // Panne de connexion persistante (travel-service injoignable) : les 3 tentatives echouent,
    // un TravelServiceUnavailableException clair remonte plutot que l'erreur reseau brute.
    @Test
    void getPricedTravelThrowsUnavailableAfterMaxAttemptsOnPersistentConnectionFailure() {
        UUID travelId = UUID.randomUUID();
        stubGetError(travelId, new ResourceAccessException("connect timed out"));

        assertThatThrownBy(() -> client.getPricedTravel(travelId, "Bearer test-token"))
                .isInstanceOf(TravelServiceUnavailableException.class);
    }

    // Circuit breaker (client/CircuitBreaker) : s'ouvre au 5e echec consecutif et coupe court
    // sans plus jamais appeler RestClient, tant qu'il reste ouvert.
    @Test
    void circuitBreakerOpensAfterRepeatedFailuresAndShortCircuitsWithoutCallingRestClient() {
        UUID travelId = UUID.randomUUID();
        stubGetError(travelId, new ResourceAccessException("connect timed out"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.getPricedTravel(travelId, "Bearer test-token"))
                    .isInstanceOf(TravelServiceUnavailableException.class);
        }

        clearInvocations(restClient);

        assertThatThrownBy(() -> client.getPricedTravel(travelId, "Bearer test-token"))
                .isInstanceOf(TravelServiceUnavailableException.class);
        verifyNoInteractions(restClient);
    }

    private void stubGetResponse(UUID travelId, TravelSummary response) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/travels/{id}", travelId)).thenReturn(headersSpec);
        when(headersSpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(TravelSummary.class)).thenReturn(response);
    }

    private void stubGetError(UUID travelId, RuntimeException error) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri("/api/travels/{id}", travelId)).thenReturn(headersSpec);
        when(headersSpec.header(eq(HttpHeaders.AUTHORIZATION), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(TravelSummary.class)).thenThrow(error);
    }
}
