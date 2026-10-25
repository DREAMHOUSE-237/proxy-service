package com.dreamhouse237.proxy_service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorsGlobalFilterTest {

    private static HttpHeaders preflight(String requestedHeaders) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.options("/COMMENTARY-SERVICE/comments")
                .header(HttpHeaders.ORIGIN, "https://dreamhouse237.onrender.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        if (requestedHeaders != null) {
            request.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, requestedHeaders);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(request.build());
        new CorsGlobalFilter().corsFilter().filter(exchange, e -> Mono.empty()).block();
        return exchange.getResponse().getHeaders();
    }

    @Test
    void preflightAllowsTheRequestedHeadersIncludingAuthorization() {
        assertEquals("authorization,content-type",
                preflight("authorization,content-type").getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
    }

    @Test
    void preflightWithoutRequestedHeadersStillAllowsAuthorization() {
        assertEquals("Authorization, Content-Type",
                preflight(null).getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
    }
}
