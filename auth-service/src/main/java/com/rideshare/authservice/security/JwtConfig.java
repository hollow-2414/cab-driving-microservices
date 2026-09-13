package com.rideshare.authservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${application.security.jwt.public-key}")
            Resource publicKeyResource
    ) throws Exception {

        String key = Files.readString(
                publicKeyResource.getFile().toPath(),
                StandardCharsets.UTF_8
        );

        key = key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decoded = Base64.getDecoder().decode(key);

        X509EncodedKeySpec keySpec =
                new X509EncodedKeySpec(decoded);

        KeyFactory keyFactory =
                KeyFactory.getInstance("RSA");

        RSAPublicKey publicKey =
                (RSAPublicKey) keyFactory.generatePublic(keySpec);

        return NimbusJwtDecoder
                .withPublicKey(publicKey)
                .build();
    }
}
