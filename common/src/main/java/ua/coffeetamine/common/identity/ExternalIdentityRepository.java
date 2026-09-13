package ua.coffeetamine.common.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

  Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject);
}
