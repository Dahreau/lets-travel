package com.travel_plan.api_gateway.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.web.client.RestClient;

class GatewayHttpClientConfigTest {

    private final GatewayHttpClientConfig config = new GatewayHttpClientConfig();

    @Test
    @SuppressWarnings("unchecked")
    void skipsSslSetupWhenBundleNameIsBlank() {
        ObjectProvider<SslBundles> sslBundlesProvider = mock(ObjectProvider.class);

        RestClient.Builder builder = config.gatewayRestClientBuilder("", sslBundlesProvider);

        assertThat(builder).isNotNull();
        verify(sslBundlesProvider, never()).getIfAvailable();
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsSslContextWhenNoBundlesAvailable() {
        ObjectProvider<SslBundles> sslBundlesProvider = mock(ObjectProvider.class);
        when(sslBundlesProvider.getIfAvailable()).thenReturn(null);

        RestClient.Builder builder = config.gatewayRestClientBuilder("internal-services", sslBundlesProvider);

        assertThat(builder).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void appliesSslContextWhenBundleIsResolved() throws Exception {
        ObjectProvider<SslBundles> sslBundlesProvider = mock(ObjectProvider.class);
        SslBundles sslBundles = mock(SslBundles.class);
        SslBundle sslBundle = mock(SslBundle.class);
        when(sslBundlesProvider.getIfAvailable()).thenReturn(sslBundles);
        when(sslBundles.getBundle("internal-services")).thenReturn(sslBundle);
        when(sslBundle.createSslContext()).thenReturn(SSLContext.getDefault());

        RestClient.Builder builder = config.gatewayRestClientBuilder("internal-services", sslBundlesProvider);

        assertThat(builder).isNotNull();
        verify(sslBundle).createSslContext();
    }
}
