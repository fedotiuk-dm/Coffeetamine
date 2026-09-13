package ua.coffeetamine.ping.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.ping.domain.model.UserMatch;

@Repository
@Transactional(readOnly = true)
public interface UserMatchRepository extends JpaRepository<UserMatch, UUID> {

  /** A match by its unordered pair — either direction matches. */
  @Query(
      """
      select m from UserMatch m
      where (m.initiatorId = :a and m.recipientId = :b)
         or (m.initiatorId = :b and m.recipientId = :a)
      """)
  Optional<UserMatch> findByPair(UUID a, UUID b);

  /** All matches the user participates in. */
  @Query("select m from UserMatch m where m.initiatorId = :userId or m.recipientId = :userId")
  Page<UserMatch> findByParticipant(UUID userId, Pageable pageable);
}
