package com.quickbite.security;

/**
 * Keycloak realm roles of the {@code quickbite} realm. Spring sees them as authorities
 * {@code ROLE_<name>}, so {@code hasRole("admin")} matches the realm role {@code admin}.
 */
public final class Roles {

    /** Everything, including other users' data. */
    public static final String ADMIN = "admin";
    /** Their own customer profile, addresses and orders. */
    public static final String CUSTOMER = "customer";
    /** Their own restaurants and menus. */
    public static final String RESTAURANT = "restaurant";
    /** Their own driver profile and the deliveries assigned to them. */
    public static final String DRIVER = "driver";

    private Roles() {
    }
}
