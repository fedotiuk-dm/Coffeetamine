package ua.coffeetamine.ping.domain.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.ping.domain.model.PingInteraction;
import ua.coffeetamine.ping.domain.model.PingState;

@Repository
@Transactional(readOnly = true)
public interface PingInteractionRepository
    extends JpaRepository<PingInteraction, UUID>, JpaSpecificationExecutor<PingInteraction> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PingInteraction p where p.id = :id")
  Optional<PingInteraction> findByIdForUpdate(UUID id);

  default Page<PingInteraction> findSent(UUID fromUserId, PingState status, Pageable pageable) {
    return findAll(PingInteractionSpec.sent(fromUserId, status), pageable);
  }

  default Page<PingInteraction> findReceived(UUID toUserId, PingState status, Pageable pageable) {
    return findAll(PingInteractionSpec.received(toUserId, status), pageable);
  }
}
