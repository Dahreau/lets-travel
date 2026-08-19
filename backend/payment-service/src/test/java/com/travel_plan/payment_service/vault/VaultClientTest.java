package com.travel_plan.payment_service.vault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

@SuppressWarnings({"unchecked", "rawtypes"})
class VaultClientTest {

    private static final String VAULT_ADDR = "http://localhost:8200";
    private static final String TEST_CERT_PEM = """
        -----BEGIN CERTIFICATE-----
        MIIDCzCCAfOgAwIBAgIUEUk8p8rZf/ddIXd+xtGtfRdsSjUwDQYJKoZIhvcNAQEL
        BQAwFTETMBEGA1UEAwwKdmF1bHQtdGVzdDAeFw0yNjA3MzAyMzI3NDRaFw0zNjA3
        MjcyMzI3NDRaMBUxEzARBgNVBAMMCnZhdWx0LXRlc3QwggEiMA0GCSqGSIb3DQEB
        AQUAA4IBDwAwggEKAoIBAQCKnh9IANMndeb8/vCSaF0yKALS12JBhUPnX423B3D/
        LjOAc6yNaFAqw5kIj6OxM9kTtaQL4MfFc/vTIEm55OnS8TbohUbLwpcJ4tmBPGdB
        pgk+s9T1JigF3Bz7GtoejDLMmLXrZZddCFecWKV7tMtrpfsy6DwgVK64vSH9yMHD
        97c43mH4zitzGAKTuoIp3NzJZ9ENdeF04HgtVvq56Hm/A8qSQvhSDR8bdAXEmTeR
        mqEAD2rsxQK6wYwhpf9IgfG2SdMLTrmb0+MdgvA6U1kPWn4lbaim9vTXnc/B3uGj
        8bYt0+4Vn7cvHvmJXKjO1NRIOXkYCcrUDuZTvvgErE3/AgMBAAGjUzBRMB0GA1Ud
        DgQWBBSmT2sE4ENclY0Q3nm9VYFwvVju5DAfBgNVHSMEGDAWgBSmT2sE4ENclY0Q
        3nm9VYFwvVju5DAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQB7
        rCIJrQExa01gv1iY7QSGT3MO5ng0J0bbvlBr7rRDUwUWEBvuSZI2ZEwrtLZp7Nva
        GktrNxEs3b/OUPO9frzwgPfLqcL+uduh0q6BlA3fj7IDjJ8fzza+j/qK7gWQTlW/
        f90QNEQOQieAF2kx+GF8CAusuiFPhkkfiYJPZzLe0VbZ/+AxxRFlVerwU+QsouiF
        2R3zDPQM2S0Twd5pQsp9lNIHaID8xwlsGU7cWjAZ+6Oh6xEUV+e53bozmUEG3qy5
        /VBQ5zNUwSC4jBQf8tao/Qcon5yQNeIu61uAUtbNfu+3nzCkPqR/XA3wCtIRjLb6
        6SOFyTMiGQnxyANxAh7a
        -----END CERTIFICATE-----
        """;

    @Test
    void throwsWhenRoleIdMissing() {
        VaultClient client = new VaultClient(VAULT_ADDR, null, "secret", mock(RestClient.class));

        assertThatThrownBy(() -> client.fetchSharedSecret("payment-service/stripe", "secret_key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAULT_ROLE_ID");
    }

    @Test
    void throwsWhenSecretIdBlank() {
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "  ", mock(RestClient.class));

        assertThatThrownBy(() -> client.fetchSharedSecret("payment-service/stripe", "secret_key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAULT_ROLE_ID");
    }

    @Test
    void fetchesSharedSecretSuccessfully() {
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token")));
        stubKvRead(
                restClient,
                "vault-token",
                new VaultKvResponse(new VaultKvResponse.VaultKvData(Map.of("secret_key", "c2tfdGVzdA=="))));

        String value = client.fetchSharedSecret("payment-service/stripe", "secret_key");

        assertThat(value).isEqualTo("c2tfdGVzdA==");
    }

    @Test
    void throwsWhenLoginDoesNotReturnToken() {
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, null);

        assertThatThrownBy(() -> client.fetchSharedSecret("payment-service/stripe", "secret_key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("client token");
    }

    @Test
    void throwsWhenSecretDataMissing() {
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token")));
        stubKvRead(restClient, "vault-token", null);

        assertThatThrownBy(() -> client.fetchSharedSecret("payment-service/stripe", "secret_key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty or missing");
    }

    @Test
    void throwsWhenFieldMissingFromSecret() {
        RestClient restClient = mock(RestClient.class);
        VaultClient client = new VaultClient(VAULT_ADDR, "role", "secret", restClient);

        stubLogin(restClient, new VaultLoginResponse(new VaultLoginResponse.VaultAuth("vault-token")));
        stubKvRead(
                restClient,
                "vault-token",
                new VaultKvResponse(new VaultKvResponse.VaultKvData(Map.of("other", "value"))));

        assertThatThrownBy(() -> client.fetchSharedSecret("payment-service/stripe", "secret_key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no field");
    }

    @Test
    void constructsHttpClientWithoutThrowingWhenCertFileMissing() {
        assertThatCode(() -> new VaultClient(VAULT_ADDR, "role", "secret", "/nonexistent/vault.crt"))
                .doesNotThrowAnyException();
    }

    @Test
    void constructsHttpClientWithoutThrowingWhenCertFileValid(@TempDir Path tempDir) throws IOException {
        Path certPath = tempDir.resolve("vault.crt");
        Files.writeString(certPath, TEST_CERT_PEM);

        assertThatCode(() -> new VaultClient(VAULT_ADDR, "role", "secret", certPath.toString()))
                .doesNotThrowAnyException();
    }

    private void stubLogin(RestClient restClient, VaultLoginResponse response) {
        RestClient.RequestBodyUriSpec bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(VAULT_ADDR + "/v1/auth/approle/login")).thenReturn(bodySpec);
        when(bodySpec.body(Map.of("role_id", "role", "secret_id", "secret"))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(VaultLoginResponse.class)).thenReturn(response);
    }

    private void stubKvRead(RestClient restClient, String token, VaultKvResponse response) {
        RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(VAULT_ADDR + "/v1/secret/data/payment-service/stripe")).thenReturn(headersSpec);
        when(headersSpec.header("X-Vault-Token", token)).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(VaultKvResponse.class)).thenReturn(response);
    }
}
