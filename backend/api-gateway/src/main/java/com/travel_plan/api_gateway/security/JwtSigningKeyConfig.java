package com.travel_plan.api_gateway.security;

import com.travel_plan.api_gateway.vault.VaultClient;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtSigningKeyConfig {

    @Bean
    public SecretKey jwtSigningKey(VaultClient vaultClient) {
        String secret = vaultClient.fetchSharedSecret("shared/jwt", "secret");
        return Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    }
}
