package com.quickbite.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Security for a QuickBite servlet service, imported with {@link EnableQuickBiteSecurity}.
 * <ul>
 *   <li>Every {@code /api/**} request needs a valid Keycloak JWT ({@code Authorization: Bearer ...}),
 *       except {@code GET} requests on the paths listed in {@code quickbite.security.public-get-paths}.</li>
 *   <li>Actuator (health probes, Prometheus) and the API docs stay open: they are not routed through
 *       the gateway, and the production Ingress exposes only {@code /api} and Keycloak's login pages.</li>
 *   <li>Stateless: no session, no cookies, so no CSRF token is needed.</li>
 *   <li>Roles and ownership are checked in service code with {@link CurrentUser}.</li>
 * </ul>
 * Token validation is configured by {@code spring.security.oauth2.resourceserver.jwt.*}: keys from
 * the realm's JWKS endpoint (inside the cluster), issuer checked against the public Keycloak URL.
 */
@Configuration(proxyBeanMethods = false)
public class QuickBiteSecurityConfiguration {

    @Bean
    SecurityFilterChain quickbiteSecurityFilterChain(
            HttpSecurity http,
            @Value("${quickbite.security.public-get-paths:}") List<String> publicGetPaths) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                            .permitAll();
                    publicGetPaths.stream()
                            .filter(path -> !path.isBlank())
                            .forEach(path -> auth.requestMatchers(HttpMethod.GET, path.strip()).permitAll());
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter())));
        return http.build();
    }

    @Bean
    CurrentUser currentUser() {
        return new CurrentUser();
    }
}
