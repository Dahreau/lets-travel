package com.travel_plan.user_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travel_plan.user_service.vault.VaultClient;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtSigningKeyConfigTest {

    @Test
    void buildsSecretKeyFromVaultSecret() {
        VaultClient vaultClient = mock(VaultClient.class);
        String encodedSecret =
                Base64.getEncoder().encodeToString("test-secret-key-must-be-at-least-32-bytes-long!".getBytes());
        when(vaultClient.fetchSharedSecret("shared/jwt", "secret")).thenReturn(encodedSecret);

        SecretKey key = new JwtSigningKeyConfig().jwtSigningKey(vaultClient);

        assertThat(key).isNotNull();
        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
