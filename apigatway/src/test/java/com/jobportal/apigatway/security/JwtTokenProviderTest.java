package com.jobportal.apigatway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for the API Gateway's {@link JwtTokenProvider}.
 *
 * <p>Note: the gateway variant of {@code JwtTokenProvider} does <em>not</em>
 * generate tokens (token issuance is the auth-service's responsibility);
 * it only validates and extracts claims. We therefore mint test tokens
 * directly with jjwt using the same signing key the provider is configured
 * with.</p>
 */
class JwtTokenProviderTest {

    private static final String DEV_DEFAULT_SECRET =
            "defaultSecretKeyForDevelopmentEnvironmentOnly";
    private static final String VALID_SECRET =
            "this-is-a-sufficiently-long-real-production-secret-32+bytes";

    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
    }

    private JwtTokenProvider providerWith(String secret, String... activeProfiles) {
        if (activeProfiles != null && activeProfiles.length > 0) {
            environment.setActiveProfiles(activeProfiles);
        }
        JwtTokenProvider provider = new JwtTokenProvider(environment);
        ReflectionTestUtils.setField(provider, "jwtSecret", secret);
        return provider;
    }

    private static String mintToken(String secret, String subject, String role,
                                    Instant issuedAt, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    @Nested
    class ValidateConfig {

        @Test
        void blankSecretThrows() {
            JwtTokenProvider provider = providerWith("");
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> ReflectionTestUtils.invokeMethod(provider, "validateConfig"));
            assertTrue(ex.getMessage().contains("jwt.secret is missing"));
        }

        @Test
        void nullSecretThrows() {
            JwtTokenProvider provider = providerWith(null);
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> ReflectionTestUtils.invokeMethod(provider, "validateConfig"));
            assertTrue(ex.getMessage().contains("jwt.secret is missing"));
        }

        @Test
        void shortSecretThrows() {
            // 16 bytes — under the 32-byte minimum for HS256
            JwtTokenProvider provider = providerWith("tooShortSecret16");
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> ReflectionTestUtils.invokeMethod(provider, "validateConfig"));
            assertTrue(ex.getMessage().contains("at least 32 bytes"));
        }

        @Test
        void devFallbackInNonDevProfileThrows() {
            JwtTokenProvider provider = providerWith(DEV_DEFAULT_SECRET);
            // No active profile set => non-dev
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> ReflectionTestUtils.invokeMethod(provider, "validateConfig"));
            assertTrue(ex.getMessage().contains("dev fallback"));
        }

        @Test
        void devFallbackInDevProfilePasses() {
            JwtTokenProvider provider = providerWith(DEV_DEFAULT_SECRET, "dev");
            assertDoesNotThrow(
                    () -> ReflectionTestUtils.invokeMethod(provider, "validateConfig"));
        }

        @Test
        void realLongSecretInNonDevProfilePasses() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            assertDoesNotThrow(
                    () -> ReflectionTestUtils.invokeMethod(provider, "validateConfig"));
        }

        @Test
        void realLongSecretInDevProfileAlsoPasses() {
            JwtTokenProvider provider = providerWith(VALID_SECRET, "dev");
            assertDoesNotThrow(
                    () -> ReflectionTestUtils.invokeMethod(provider, "validateConfig"));
        }
    }

    @Nested
    class TokenLifecycle {

        @Test
        void validateReturnsTrueForGoodToken() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            String token = mintToken(VALID_SECRET, "alice@example.com", "USER",
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

            assertTrue(provider.validateToken(token));
        }

        @Test
        void getEmailFromTokenReturnsSubject() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            String token = mintToken(VALID_SECRET, "bob@example.com", "RECRUITER",
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

            assertEquals("bob@example.com", provider.getEmailFromToken(token));
        }

        @Test
        void getRoleFromTokenReturnsRoleClaim() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            String token = mintToken(VALID_SECRET, "carol@example.com", "ADMIN",
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

            assertEquals("ADMIN", provider.getRoleFromToken(token));
        }

        @Test
        void validateReturnsFalseForTamperedSignature() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            String token = mintToken(VALID_SECRET, "dave@example.com", "USER",
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

            // Flip the last character of the signature
            String tampered = token.substring(0, token.length() - 1)
                    + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

            assertFalse(provider.validateToken(tampered));
        }

        @Test
        void validateReturnsFalseForTokenSignedWithDifferentKey() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            String foreignSecret =
                    "completely-different-secret-also-32+bytes-long-for-hmac";
            String token = mintToken(foreignSecret, "eve@example.com", "USER",
                    Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));

            assertFalse(provider.validateToken(token));
        }

        @Test
        void validateReturnsFalseForExpiredToken() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
            String token = mintToken(VALID_SECRET, "frank@example.com", "USER",
                    past, past.plus(1, ChronoUnit.HOURS)); // expired 1h ago

            assertFalse(provider.validateToken(token));
        }

        @Test
        void validateReturnsFalseForGarbage() {
            JwtTokenProvider provider = providerWith(VALID_SECRET);
            assertFalse(provider.validateToken("not-a-jwt"));
            assertFalse(provider.validateToken(""));
            assertFalse(provider.validateToken("a.b.c"));
        }
    }
}
