package ua.coffeetamine.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires an authenticated user, with no specific role. Use on controllers/methods whose access
 * control beyond authentication is enforced in the service layer (e.g. ownership / IDOR checks via
 * {@code SecurityUtils.verifyOwnerOrAdmin}). Composable on type or method.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated()")
public @interface RequiresAuthenticated {}
