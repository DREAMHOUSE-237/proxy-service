package com.dreamhouse237.proxy_service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthGlobalFilterTest {

    private static final String KEY = "test-signing-key-that-is-long-enough-for-hs256-0123456789";

    private static String token(String key, Instant expiresAt) {
        return Jwts.builder()
                .claim("user_id", "uuid-alice")
                .claim("email", "alice@example.cm")
                .claim("role", "proprietaire")
                .expiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private static HttpHeaders forward(JwtAuthGlobalFilter filter, MockServerHttpRequest request) {
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        filter.filter(MockServerWebExchange.from(request), exchange -> {
            forwarded.set(exchange.getRequest());
            return reactor.core.publisher.Mono.empty();
        }).block();
        return forwarded.get().getHeaders();
    }

    private static MockServerHttpRequest.BaseBuilder<?> forgedRequest() {
        return MockServerHttpRequest.post("/COMMENTARY-SERVICE/comments")
                .header("X-User-Id", "uuid-mallory")
                .header("X-User-Email", "mallory@example.cm")
                .header("X-User-Roles", "admin");
    }

    @Test
    void validTokenReplacesForgedHeaders() {
        HttpHeaders headers = forward(new JwtAuthGlobalFilter(KEY), forgedRequest()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(KEY, Instant.now().plusSeconds(3600)))
                .build());

        assertEquals("uuid-alice", headers.getFirst("X-User-Id"));
        assertEquals("alice@example.cm", headers.getFirst("X-User-Email"));
        assertEquals("proprietaire", headers.getFirst("X-User-Roles"));
    }

    @Test
    void tokenSignedByPyJwtIsAccepted() {
        // Généré par PyJWT 2.10 (la bibliothèque de SimpleJWT dans auth-service)
        // avec KEY, exp en 2100 : garantit que les deux côtés encodent la clé pareil.
        String pyJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjo0MTAyNDQ0ODAwLCJqdGkiOiJ4IiwidXNlcl9pZCI6InV1aWQtYm9iIiwiZW1haWwiOiJib2JAZXhhbXBsZS5jbSIsInJvbGUiOiJjbGllbnQifQ."
                + "5ghRwfuGeyezwMHP9yvePb1LGAdttYZD3DqXHFm5JCM";
        HttpHeaders headers = forward(new JwtAuthGlobalFilter(KEY), forgedRequest()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pyJwtToken)
                .build());

        assertEquals("uuid-bob", headers.getFirst("X-User-Id"));
        assertEquals("bob@example.cm", headers.getFirst("X-User-Email"));
        assertEquals("client", headers.getFirst("X-User-Roles"));
    }

    @Test
    void forgedHeadersWithoutTokenAreStripped() {
        HttpHeaders headers = forward(new JwtAuthGlobalFilter(KEY), forgedRequest().build());

        assertNull(headers.getFirst("X-User-Id"));
        assertNull(headers.getFirst("X-User-Email"));
        assertNull(headers.getFirst("X-User-Roles"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsIgnored() {
        String otherKey = "another-key-that-is-also-long-enough-for-hs256-9876543210";
        HttpHeaders headers = forward(new JwtAuthGlobalFilter(KEY), forgedRequest()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(otherKey, Instant.now().plusSeconds(3600)))
                .build());

        assertNull(headers.getFirst("X-User-Id"));
        assertNull(headers.getFirst("X-User-Email"));
    }

    @Test
    void expiredTokenIsIgnored() {
        HttpHeaders headers = forward(new JwtAuthGlobalFilter(KEY), forgedRequest()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(KEY, Instant.now().minusSeconds(60)))
                .build());

        assertNull(headers.getFirst("X-User-Id"));
    }

    @Test
    void missingKeyOnlyStripsHeaders() {
        HttpHeaders headers = forward(new JwtAuthGlobalFilter(""), forgedRequest()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(KEY, Instant.now().plusSeconds(3600)))
                .build());

        assertNull(headers.getFirst("X-User-Id"));
        assertNull(headers.getFirst("X-User-Email"));
    }
}
