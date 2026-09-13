package com.rideshare.authservice.service;

import com.rideshare.authservice.entity.User;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final PrivateKey privateKey;
    private final long expiration;

    public  JwtService(
            @Value("${application.security.jwt.private-key}") Resource privateKeyResource,
            @Value("${application.security.jwt.expiration}") long expiration
    ) throws Exception{
        this.privateKey = loadPrivateKey(privateKeyResource);
        this.expiration = expiration;
    }

    public String generateToken(User user){
        Date now = new Date();
        Date Expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role" , user.getRole())
                .issuer("auth-service")
                .issuedAt(now)
                .expiration(Expiry)
                .signWith(privateKey)
                .compact();
    }

    private PrivateKey loadPrivateKey(Resource resource) throws Exception {
        String key = Files.readString(
                resource.getFile().toPath(),
                StandardCharsets.UTF_8
        );

        key = key
                .replace("-----BEGIN PRIVATE KEY-----" , "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decoded = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec =
                new PKCS8EncodedKeySpec(decoded);

        KeyFactory keyFactory =
                 KeyFactory.getInstance("RSA");

        return keyFactory.generatePrivate(keySpec);

    }
}
