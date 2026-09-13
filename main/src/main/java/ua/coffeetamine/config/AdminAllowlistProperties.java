package ua.coffeetamine.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Lower-cased email allowlist that elevates a JWT-authenticated user to {@code ROLE_ADMIN}. The IdP
 * is NOT trusted for ADMIN — moderation access is gated by this backend-owned list so provider
 * configuration mistakes (or a compromised user in the IdP's "admin group") cannot grant moderation
 * rights. {@code app.admin-emails: []} (the default) means no admins.
 *
 * <p>The canonical constructor normalises every entry to lowercase ({@link Locale#ROOT}) at binding
 * time, so consumers can do a plain {@code adminEmails().contains(email.toLowerCase(ROOT))} check.
 */
@ConfigurationProperties("app")
public record AdminAllowlistProperties(@DefaultValue List<String> adminEmails) {

  public AdminAllowlistProperties {
    adminEmails = adminEmails.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
  }
}
