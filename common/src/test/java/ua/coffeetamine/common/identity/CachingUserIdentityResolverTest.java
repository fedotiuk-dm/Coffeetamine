package ua.coffeetamine.common.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the orchestration logic inside {@link CachingUserIdentityResolver}: repository
 * lookup and delegation to the provisioning boundary. Cache behaviour itself is provided by
 * Spring's {@code @Cacheable} AOP proxy and is intentionally not exercised here.
 */
@ExtendWith(MockitoExtension.class)
class CachingUserIdentityResolverTest {

  @Mock private ExternalIdentityRepository repository;
  @Mock private ExternalIdentityProvisioner provisioner;

  @InjectMocks private CachingUserIdentityResolver resolver;

  @Test
  void resolves_existing_identity_to_stored_app_user_id() {
    UUID appUserId = UUID.randomUUID();
    ExternalIdentity row =
        ExternalIdentity.builder()
            .appUserId(appUserId)
            .issuer("https://auth.example.com")
            .subject("opaque-subject-123")
            .build();
    when(repository.findByIssuerAndSubject("https://auth.example.com", "opaque-subject-123"))
        .thenReturn(Optional.of(row));

    UUID result = resolver.resolve("https://auth.example.com", "opaque-subject-123", null);

    assertThat(result).isEqualTo(appUserId);
  }

  @Test
  void delegates_unknown_identity_to_provisioner() {
    UUID provisioned = UUID.randomUUID();
    when(repository.findByIssuerAndSubject("https://auth.example.com", "new-user-sub"))
        .thenReturn(Optional.empty());
    when(provisioner.provision("https://auth.example.com", "new-user-sub", "u@example.com"))
        .thenReturn(provisioned);

    UUID result = resolver.resolve("https://auth.example.com", "new-user-sub", "u@example.com");

    assertThat(result).isEqualTo(provisioned);
    verify(provisioner).provision("https://auth.example.com", "new-user-sub", "u@example.com");
  }

  @Test
  void loser_of_concurrent_provisioning_reads_winning_row_after_inner_transaction_rollback() {
    UUID winnerAppUserId = UUID.randomUUID();
    ExternalIdentity winningRow =
        ExternalIdentity.builder()
            .appUserId(winnerAppUserId)
            .issuer("https://auth.example.com")
            .subject("new-user-sub")
            .build();
    when(repository.findByIssuerAndSubject("https://auth.example.com", "new-user-sub"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winningRow));
    when(provisioner.provision("https://auth.example.com", "new-user-sub", null))
        .thenThrow(new DataIntegrityViolationException("uk violation"));

    UUID result = resolver.resolve("https://auth.example.com", "new-user-sub", null);

    assertThat(result).isEqualTo(winnerAppUserId);
  }
}
