package com.travel_plan.payment_service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.domain.PaymentStatus;
import com.travel_plan.payment_service.domain.ProviderType;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@SuppressWarnings({"unchecked", "rawtypes"})
class StripePaymentProviderTest {

    private final RestClient restClient = mock(RestClient.class);
    private final StripeCredentials credentials = new StripeCredentials("sk_test_123");
    private final StripePaymentProvider provider =
            new StripePaymentProvider(restClient, credentials, "https://api.stripe.com");

    @Test
    void typeReturnsStripe() {
        assertThat(provider.type()).isEqualTo(ProviderType.STRIPE);
    }

    @Test
    void chargeReturnsSucceededWhenStripeConfirms() {
        stubStripeResponse(Map.of("id", "pi_123", "status", "succeeded"));

        ChargeResult result = provider.charge(new ChargeRequest(new BigDecimal("42.00"), "EUR", "pm_card_visa"));

        assertThat(result.providerReference()).isEqualTo("pi_123");
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void chargeReturnsFailedWhenStripeDoesNotConfirm() {
        stubStripeResponse(Map.of("id", "pi_456", "status", "requires_action"));

        ChargeResult result = provider.charge(new ChargeRequest(new BigDecimal("42.00"), "EUR", "pm_card_visa"));

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void chargeThrowsWhenStripeResponseHasNoId() {
        stubStripeResponse(Map.of("status", "succeeded"));
        ChargeRequest request = new ChargeRequest(new BigDecimal("10.00"), "EUR", "pm_card_visa");

        assertThatThrownBy(() -> provider.charge(request)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundSucceedsWhenStripeConfirms() {
        stubStripeResponse(Map.of("id", "re_123", "status", "succeeded"));

        assertThatCode(() -> provider.refund("pi_123")).doesNotThrowAnyException();
    }

    @Test
    void refundThrowsWhenStripeReportsFailed() {
        stubStripeResponse(Map.of("id", "re_456", "status", "failed"));

        assertThatThrownBy(() -> provider.refund("pi_456")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundThrowsWhenStripeResponseHasNoId() {
        stubStripeResponse(Map.of("status", "succeeded"));

        assertThatThrownBy(() -> provider.refund("pi_789")).isInstanceOf(IllegalStateException.class);
    }

    private void stubStripeResponse(Map<String, Object> response) {
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(MultiValueMap.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(Map.class)).thenReturn(response);
    }
}
