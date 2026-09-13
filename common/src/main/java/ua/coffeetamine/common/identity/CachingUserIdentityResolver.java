package ua.coffeetamine.common.identity;

import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Default {@link UserIdentityResolver}. Cache lookup + population is delegated to Spring's {@link
 * Cacheable @Cacheable} (Caffeine under the hood, configured in {@link IdentityCacheConfig}); cache
 * writes are transaction-aware when an outer transaction exists.
 *
 * <p>First-time provisioning is delegated to {@link ExternalIdentityProvisioner}, which writes in a
 * separate transaction so callers from read-only feature-service methods can still resolve a new
 * identity safely. The {@code (issuer, subject)} unique constraint remains the source of truth
 * under concurrent first requests.
 */
@Component
@RequiredArgsConstructor
public class CachingUserIdentityResolver implements UserIdentityResolver {

  public static final String CACHE_NAME = "externalIdentity";

  private final ExternalIdentityRepository repository;
  private final ExternalIdentityProvisioner provisioner;

  @Override
  @Cacheable(value = CACHE_NAME, key = "#issuer + '|' + #subject")
  public UUID resolve(String issuer, String subject, String email) {
    return repository
        .findByIssuerAndSubject(issuer, subject)
        .map(ExternalIdentity::getAppUserId)
        .orElseGet(() -> provisionOrReadWinner(issuer, subject, email));
  }

  private UUID provisionOrReadWinner(String issuer, String subject, String email) {
    try {
      return provisioner.provision(issuer, subject, email);
    } catch (DataIntegrityViolationException race) {
      return repository
          .findByIssuerAndSubject(issuer, subject)
          .map(ExternalIdentity::getAppUserId)
          .orElseThrow(() -> race);
    }
  }
}
