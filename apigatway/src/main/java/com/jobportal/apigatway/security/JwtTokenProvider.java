package com.jobportal.apigatway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class JwtTokenProvider {

    private static final String DEV_DEFAULT_SECRET = "defaultSecretKeyForDevelopmentEnvironmentOnly";
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret:defaultSecretKeyForDevelopmentEnvironmentOnly}")
    private String jwtSecret;

    private final Environment environment;

    public JwtTokenProvider(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateConfig() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is missing. Set the JWT_SECRET environment variable.");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HMAC-SHA256.");
        }
        boolean isDev = Arrays.asList(environment.getActiveProfiles()).contains("dev");
        if (!isDev && DEV_DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "jwt.secret is using the dev fallback in a non-dev profile. " +
                    "Set JWT_SECRET to a real secret or activate the 'dev' profile via " +
                    "spring.profiles.active=dev.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("role", String.class);
    }
}
