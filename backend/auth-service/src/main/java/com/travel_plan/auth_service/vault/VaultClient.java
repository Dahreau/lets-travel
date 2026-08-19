package com.travel_plan.auth_service.vault;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class VaultClient {

    private final RestClient restClient;
    private final String vaultAddr;
    private final String roleId;
    private final String secretId;

    @Autowired
    public VaultClient(
            @Value("${app.vault.addr}") String vaultAddr,
            @Value("${app.vault.role-id}") String roleId,
            @Value("${app.vault.secret-id}") String secretId,
            @Value("${app.vault.tls-cert-path}") String vaultTlsCertPath) {
        this(vaultAddr, roleId, secretId, RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(vaultHttpClient(vaultTlsCertPath)))
                .build());
    }

    // Vault a sa propre CA auto-signee (infra/vault/certs/vault.crt) : on ne fait confiance qu'a elle.
    private static HttpClient vaultHttpClient(String vaultTlsCertPath) {
        Path certPath = Path.of(vaultTlsCertPath);
        if (!Files.exists(certPath)) {
            return HttpClient.newHttpClient();
        }
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            try (InputStream in = Files.newInputStream(certPath)) {
                X509Certificate vaultCert =
                        (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
                trustStore.setCertificateEntry("vault", vaultCert);
            }

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
            return HttpClient.newBuilder().sslContext(sslContext).build();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load Vault TLS certificate from " + vaultTlsCertPath, e);
        }
    }

    VaultClient(String vaultAddr, String roleId, String secretId, RestClient restClient) {
        this.restClient = restClient;
        this.vaultAddr = vaultAddr;
        this.roleId = roleId;
        this.secretId = secretId;
    }

    public String fetchSharedSecret(String path, String field) {
        if (roleId == null || roleId.isBlank() || secretId == null || secretId.isBlank()) {
            throw new IllegalStateException(
                    "Vault AppRole credentials not configured (VAULT_ROLE_ID/VAULT_SECRET_ID)");
        }

        String token = login();
        VaultKvResponse response = restClient.get()
                .uri(vaultAddr + "/v1/secret/data/" + path)
                .header("X-Vault-Token", token)
                .retrieve()
                .body(VaultKvResponse.class);

        if (response == null || response.data() == null || response.data().data() == null) {
            throw new IllegalStateException("Vault secret at path '" + path + "' is empty or missing");
        }

        String value = response.data().data().get(field);
        if (value == null) {
            throw new IllegalStateException("Vault secret at path '" + path + "' has no field '" + field + "'");
        }
        return value;
    }

    private String login() {
        Map<String, Object> body = Map.of("role_id", roleId, "secret_id", secretId);
        VaultLoginResponse response = restClient.post()
                .uri(vaultAddr + "/v1/auth/approle/login")
                .body(body)
                .retrieve()
                .body(VaultLoginResponse.class);

        if (response == null || response.auth() == null || response.auth().clientToken() == null) {
            throw new IllegalStateException("Vault AppRole login did not return a client token");
        }
        return response.auth().clientToken();
    }
}
