package ua.coffeetamine.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Marks an endpoint as intentionally public — no authentication required. Use at method level to
 * open a single handler inside an otherwise authenticated controller, or at type level for fully
 * public controllers.
 *
 * <p>Note: this annotation alone is NOT sufficient — the {@code SecurityFilterChain} declares the
 * public/authenticated boundary first, and method-level security runs only after the chain admits
 * the request. Genuinely public endpoints must also be permitted in {@link
 * ua.coffeetamine.config.SecurityConfig}'s {@code PUBLIC_ENDPOINTS}. This annotation makes "public"
 * a greppable, explicit decision rather than the absence of an annotation.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("permitAll()")
public @interface PublicEndpoint {}
