package com.jobportal.authservice.security;

import com.jobportal.authservice.entity.Role;
import com.jobportal.authservice.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String DEV_DEFAULT_SECRET = "defaultSecretKeyForDevelopmentEnvironmentOnly";
    private static final String STRONG_SECRET = "this-is-a-very-strong-and-long-test-secret-1234567890";

    private MockEnvironment env;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        env = new MockEnvironment();
        provider = new JwtTokenProvider(env);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 10_800_000L);
    }

    /** Reflectively call the package-private @PostConstruct validator. */
    private void invokeValidate() throws Exception {
        Method m = JwtTokenProvider.class.getDeclaredMethod("validateConfig");
        m.setAccessible(true);
        try {
            m.invoke(provider);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // Unwrap so tests see the underlying IllegalStateException directly
            if (ite.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw ite;
        }
    }

    // ---------- validateConfig ----------

    @Test
    void validateConfig_blankSecret_throws() {
        ReflectionTestUtils.setField(provider, "jwtSecret", "   ");
        assertThatThrownBy(this::invokeValidate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret is missing");
    }

    @Test
    void validateConfig_nullSecret_throws() {
        ReflectionTestUtils.setField(provider, "jwtSecret", null);
        assertThatThrownBy(this::invokeValidate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret is missing");
    }

    @Test
    void validateConfig_secretShorterThan32Bytes_throws() {
        ReflectionTestUtils.setField(provider, "jwtSecret", "short-secret"); // <32 bytes
        assertThatThrownBy(this::invokeValidate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void validateConfig_devDefaultSecretInNonDevProfile_throws() {
        // No active profiles set -> "dev" is not active
        ReflectionTestUtils.setField(provider, "jwtSecret", DEV_DEFAULT_SECRET);
        assertThatThrownBy(this::invokeValidate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev fallback");
    }

    @Test
    void validateConfig_devDefaultSecretWithDevProfile_passes() {
        env.setActiveProfiles("dev");
        ReflectionTestUtils.setField(provider, "jwtSecret", DEV_DEFAULT_SECRET);

        // Should not throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::invokeValidate);
    }

    @Test
    void validateConfig_strongSecret_passes() {
        ReflectionTestUtils.setField(provider, "jwtSecret", STRONG_SECRET);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::invokeValidate);
    }

    // ---------- generateToken / round-trip ----------

    @Test
    void generateToken_roundTripsSubjectAndExpiry() {
        ReflectionTestUtils.setField(provider, "jwtSecret", STRONG_SECRET);

        User user = User.builder()
                .email("alice@example.com")
                .password("ignored")
                .role(Role.JOB_SEEKER)
                .isActive(true)
                .build();

        Instant before = Instant.now();
        String token = provider.generateToken(user);
        assertThat(token).isNotBlank();

        assertThat(provider.getEmailFromToken(token)).isEqualTo("alice@example.com");
        Instant exp = provider.getExpirationDate(token);
        assertThat(exp).isAfter(before);
        // Configured expiration is 86_400_000 ms = 24h; expiry must be within 24h+slack
        assertThat(exp).isBefore(before.plus(25, ChronoUnit.HOURS));
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_tamperedSignature_returnsFalse() {
        ReflectionTestUtils.setField(provider, "jwtSecret", STRONG_SECRET);

        User user = User.builder()
                .email("alice@example.com")
                .role(Role.JOB_SEEKER)
                .password("ignored")
                .isActive(true)
                .build();

        String token = provider.generateToken(user);
        // flip a character in the signature segment
        int lastDot = token.lastIndexOf('.');
        char flipped = token.charAt(lastDot + 1) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, lastDot + 1) + flipped + token.substring(lastDot + 2);

        assertThat(tampered).isNotEqualTo(token);
        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_garbageString_returnsFalse() {
        ReflectionTestUtils.setField(provider, "jwtSecret", STRONG_SECRET);
        assertThat(provider.validateToken("not-a-jwt")).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(provider, "jwtSecret", STRONG_SECRET);

        // Build an already-expired token signed with the same secret
        SecretKey key = Keys.hmacShaKeyFor(STRONG_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String expired = Jwts.builder()
                .subject("alice@example.com")
                .issuedAt(Date.from(now.minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(now.minus(1, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();

        assertThat(provider.validateToken(expired)).isFalse();
    }
}
