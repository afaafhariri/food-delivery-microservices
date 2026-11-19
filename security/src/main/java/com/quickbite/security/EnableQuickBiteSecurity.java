package com.quickbite.security;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on a service's {@code @SpringBootApplication} class to require Keycloak JWTs
 * ({@link QuickBiteSecurityConfiguration}). An explicit import, rather than an auto-configuration,
 * so the service's filter chain always replaces Spring Boot's default one.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(QuickBiteSecurityConfiguration.class)
public @interface EnableQuickBiteSecurity {
}
