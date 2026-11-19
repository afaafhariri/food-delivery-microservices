package com.quickbite.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Objects;

/**
 * The authenticated caller of the current request, for ownership checks in service code.
 * <p>
 * Ownership is stored as the Keycloak user id ({@link #id()}, the token's {@code sub}). Admins
 * pass every ownership check. Failed checks throw {@link AccessDeniedException}, which each
 * service's exception handler turns into {@code 403}.
 */
public class CurrentUser {

    /** Keycloak user id of the caller. */
    public String id() {
        return authentication().getName();
    }

    public boolean hasRole(String role) {
        String authority = "ROLE_" + role;
        return authentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public boolean isAdmin() {
        return hasRole(Roles.ADMIN);
    }

    /** Passes if the caller has at least one of the roles (or is an admin). */
    public void requireAnyRole(String... roles) {
        if (isAdmin() || Arrays.stream(roles).anyMatch(this::hasRole)) {
            return;
        }
        throw new AccessDeniedException("requires role " + String.join(" or ", roles));
    }

    /** True if the caller owns the resource or is an admin. A resource without an owner belongs to admins only. */
    public boolean ownsOrAdmin(String ownerId) {
        return isAdmin() || (ownerId != null && Objects.equals(ownerId, id()));
    }

    public void requireOwnerOrAdmin(String ownerId) {
        if (!ownsOrAdmin(ownerId)) {
            throw new AccessDeniedException("not yours");
        }
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("no authenticated user");
        }
        return authentication;
    }
}
