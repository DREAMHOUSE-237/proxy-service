package com.dreamhouse237.proxy_service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Identifie l'utilisateur à partir du JWT émis par auth-service.
 *
 * Les services en aval (commentary-service, publication-service...) font
 * confiance aux en-têtes X-User-*. Ce filtre supprime donc toujours ceux
 * envoyés par le client, puis les remplit à partir du token s'il est valide.
 * Sans token valide, la requête continue en anonyme : chaque service décide
 * lui-même si la route exige un utilisateur.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    static final String USER_ID = "X-User-Id";
    static final String USER_EMAIL = "X-User-Email";
    static final String USER_ROLES = "X-User-Roles";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    private final SecretKey key;

    public JwtAuthGlobalFilter(@Value("${jwt.signing-key:}") String signingKey) {
        if (signingKey == null || signingKey.isBlank()) {
            log.error("JWT_SIGNING_KEY absent : aucun utilisateur ne sera identifié par le gateway");
            this.key = null;
        } else {
            // Même encodage que PyJWT côté auth-service : les octets UTF-8 de la chaîne
            this.key = Keys.hmacShaKeyFor(signingKey.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Claims claims = readClaims(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID);
                    headers.remove(USER_EMAIL);
                    headers.remove(USER_ROLES);
                    if (claims != null) {
                        setIfPresent(headers, USER_ID, claims.get("user_id", String.class));
                        setIfPresent(headers, USER_EMAIL, claims.get("email", String.class));
                        setIfPresent(headers, USER_ROLES, claims.get("role", String.class));
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private Claims readClaims(String authorization) {
        if (key == null || authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(authorization.substring("Bearer ".length()).trim())
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // Token expiré, mal signé ou illisible : la requête continue en anonyme
            log.debug("JWT rejeté : {}", e.getMessage());
            return null;
        }
    }

    private static void setIfPresent(HttpHeaders headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.set(name, value);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
