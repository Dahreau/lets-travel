package com.travel_plan.payment_service.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.exception.TravelNotFoundException;
import com.travel_plan.payment_service.exception.TravelPriceNotSetException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// Meme convention de mock que StripePaymentProviderTest/PayPalPaymentProviderTest : on mocke
// directement la chaine fluide de RestClient plutot que MockRestServiceServer, pour rester
// coherent avec le reste de payment-service.
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
    void getPricedTravelRethrowsOnNon404Error() {
        UUID travelId = UUID.randomUUID();
        RestClientResponseException serviceUnavailable = new HttpServerErrorException(HttpStatusCode.valueOf(503));
        stubGetError(travelId, serviceUnavailable);

        assertThatThrownBy(() -> client.getPricedTravel(travelId, "Bearer test-token"))
                .isSameAs(serviceUnavailable);
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

    private void stubGetError(UUID travelId, RestClientResponseException error) {
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
