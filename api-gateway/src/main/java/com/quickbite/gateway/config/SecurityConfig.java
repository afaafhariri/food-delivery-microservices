package com.quickbite.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Authentication at the edge. Every {@code /api} request needs a valid Keycloak access token,
 * except browsing restaurants and menus ({@code GET} on a restaurant or its menu items; a
 * restaurant's orders are not public). Invalid or missing
 * tokens are rejected here with {@code 401} before any service is called.
 * <p>
 * The gateway checks <i>who</i> you are; each service checks <i>what you may do</i> (roles and
 * ownership), using the same token, which the gateway forwards unchanged.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain gatewaySecurity(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)              // no cookies: bearer tokens only
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())  // stateless
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()   // CORS preflight
                        .pathMatchers("/actuator/**", "/fallback/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/restaurants", "/api/restaurants/*",
                                "/api/restaurants/*/menu-items/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
