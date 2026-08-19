package com.travel_plan.payment_service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.payment_service.domain.ProviderType;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentProviderResolverTest {

    @Test
    void resolveReturnsMatchingProvider() {
        PaymentProvider stripe = mock(PaymentProvider.class);
        when(stripe.type()).thenReturn(ProviderType.STRIPE);
        PaymentProvider payPal = mock(PaymentProvider.class);
        when(payPal.type()).thenReturn(ProviderType.PAYPAL);

        PaymentProviderResolver resolver = new PaymentProviderResolver(List.of(stripe, payPal));

        assertThat(resolver.resolve(ProviderType.STRIPE)).isSameAs(stripe);
        assertThat(resolver.resolve(ProviderType.PAYPAL)).isSameAs(payPal);
    }

    @Test
    void resolveThrowsForUnregisteredProvider() {
        PaymentProviderResolver resolver = new PaymentProviderResolver(List.of());

        assertThatThrownBy(() -> resolver.resolve(ProviderType.STRIPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRIPE");
    }
}
