package ua.coffeetamine.common.identity;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Creates the immutable external-identity mapping in its own transaction. This keeps first-login
 * provisioning independent from feature-service transactions, many of which are read-only.
 */
@Component
@RequiredArgsConstructor
public class ExternalIdentityProvisioner {

  private final ExternalIdentityRepository repository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID provision(String issuer, String subject, String email) {
    UUID newAppUserId = UUID.randomUUID();
    ExternalIdentity row =
        ExternalIdentity.builder()
            .appUserId(newAppUserId)
            .issuer(issuer)
            .subject(subject)
            .email(email)
            .build();
    repository.saveAndFlush(row);
    return newAppUserId;
  }
}
