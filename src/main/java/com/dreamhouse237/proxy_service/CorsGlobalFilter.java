package com.dreamhouse237.proxy_service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class CorsGlobalFilter {

    private static final List<String> ALLOWED_ORIGINS = List.of(
        "https://dreamhouse237.onrender.com",
        "http://localhost:5173"
    );

    @Bean
    public WebFilter corsFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();

            String origin = request.getHeaders().getOrigin();

            // Only set CORS headers if the origin is in our allowlist
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS,HEAD,PATCH");
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
                headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
            }

            // Handle preflight: respond immediately without forwarding
            if (request.getMethod() == HttpMethod.OPTIONS) {
                response.setStatusCode(HttpStatus.OK);
                return response.setComplete();
            }

            return chain.filter(exchange);
        };
    }
}