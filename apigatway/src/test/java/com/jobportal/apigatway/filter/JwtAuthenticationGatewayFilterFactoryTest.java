package com.jobportal.apigatway.filter;

import com.jobportal.apigatway.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationGatewayFilterFactory}.
 *
 * <p>Verifies the reactive gateway filter behaviour: rejects unauthenticated
 * requests with 401 and short-circuits the chain, and on success injects
 * the {@code X-User-Email} / {@code X-User-Role} identity headers downstream
 * services rely on (per docs/CONVENTIONS.md).</p>
 */
class JwtAuthenticationGatewayFilterFactoryTest {

    private JwtTokenProvider jwtTokenProvider;
    private GatewayFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        JwtAuthenticationGatewayFilterFactory factory =
                new JwtAuthenticationGatewayFilterFactory(jwtTokenProvider);
        filter = factory.apply(new JwtAuthenticationGatewayFilterFactory.Config());

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private static MockServerWebExchange exchangeWithHeader(String headerValue) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.get("http://localhost/api/users/me");
        if (headerValue != null) {
            builder.header(HttpHeaders.AUTHORIZATION, headerValue);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void missingAuthorizationHeader_returnsUnauthorized_andDoesNotInvokeChain() {
        MockServerWebExchange exchange = exchangeWithHeader(null);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void nonBearerHeader_returnsUnauthorized_andDoesNotInvokeChain() {
        MockServerWebExchange exchange = exchangeWithHeader("Basic dXNlcjpwYXNz");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void bearerWithLowercasePrefix_returnsUnauthorized() {
        // Filter strictly checks startsWith("Bearer ") — case-sensitive
        MockServerWebExchange exchange = exchangeWithHeader("bearer some-token");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void invalidToken_returnsUnauthorized_andDoesNotInvokeChain() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);
        MockServerWebExchange exchange = exchangeWithHeader("Bearer bad-token");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void validToken_invokesChainOnce_withIdentityHeadersInjected() {
        String token = "good-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("alice@example.com");
        when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("USER");

        MockServerWebExchange exchange = exchangeWithHeader("Bearer " + token);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Response status must NOT have been set to 401 on the success path
        assertNotNull(exchange.getResponse());
        assertEquals(null, exchange.getResponse().getStatusCode(),
                "Status should remain unset on the success path");

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(captor.capture());

        ServerWebExchange forwarded = captor.getValue();
        HttpHeaders forwardedHeaders = forwarded.getRequest().getHeaders();

        assertEquals("alice@example.com", forwardedHeaders.getFirst("X-User-Email"));
        assertEquals("USER", forwardedHeaders.getFirst("X-User-Role"));
        // Original Authorization header should still be present on the mutated request
        assertEquals("Bearer " + token,
                forwardedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void validToken_propagatesAdminRole() {
        String token = "admin-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getEmailFromToken(token)).thenReturn("admin@example.com");
        when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("ADMIN");

        MockServerWebExchange exchange = exchangeWithHeader("Bearer " + token);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain, times(1)).filter(captor.capture());

        HttpHeaders forwardedHeaders = captor.getValue().getRequest().getHeaders();
        assertEquals("admin@example.com", forwardedHeaders.getFirst("X-User-Email"));
        assertEquals("ADMIN", forwardedHeaders.getFirst("X-User-Role"));
    }
}
