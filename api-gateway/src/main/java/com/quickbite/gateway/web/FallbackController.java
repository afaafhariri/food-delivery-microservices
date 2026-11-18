package com.quickbite.gateway.web;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

/**
 * Answers for a route whose service is down or whose circuit breaker is open.
 * Same error shape as the services: {@code {"error": "...", "status": 503}}.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    ResponseEntity<Map<String, Object>> unavailable(@PathVariable String service, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String reason = cause instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException
                ? "circuit open"
                : "not responding";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "10")
                .body(Map.of(
                        "error", service + " is temporarily unavailable (" + reason + ")",
                        "status", HttpStatus.SERVICE_UNAVAILABLE.value()));
    }
}
