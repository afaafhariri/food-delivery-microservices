package com.quickbite.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private final CurrentUser user = new CurrentUser();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    static void signIn(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new KeycloakJwtConverter().convert(jwt));
    }

    @Test
    void realmRolesBecomeSpringRoles() {
        signIn("user-1", "customer", "default-roles-quickbite");
        assertThat(user.id()).isEqualTo("user-1");
        assertThat(user.hasRole(Roles.CUSTOMER)).isTrue();
        assertThat(user.hasRole(Roles.DRIVER)).isFalse();
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void ownersAndAdminsPassOwnershipChecks() {
        signIn("user-1", "customer");
        user.requireOwnerOrAdmin("user-1");
        assertThatThrownBy(() -> user.requireOwnerOrAdmin("user-2")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> user.requireOwnerOrAdmin(null)).isInstanceOf(AccessDeniedException.class);

        signIn("admin-1", "admin");
        user.requireOwnerOrAdmin("user-2");
        user.requireOwnerOrAdmin(null);
    }

    @Test
    void roleChecksAcceptAdmins() {
        signIn("driver-1", "driver");
        user.requireAnyRole(Roles.DRIVER);
        assertThatThrownBy(() -> user.requireAnyRole(Roles.RESTAURANT)).isInstanceOf(AccessDeniedException.class);

        signIn("admin-1", "admin");
        user.requireAnyRole(Roles.RESTAURANT);
    }

    @Test
    void tokenWithoutRolesHasNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("x").claim("scope", "openid").build();
        assertThat(new KeycloakJwtConverter().convert(jwt).getAuthorities()).isEmpty();
    }
}
