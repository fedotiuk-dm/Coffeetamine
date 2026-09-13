package ua.coffeetamine.interests.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.interests.domain.model.InterestTag;
import ua.coffeetamine.interests.domain.model.UserInterestSelection;
import ua.coffeetamine.interests.domain.model.UserInterestSelection_;

@Repository
@Transactional(readOnly = true)
public interface UserInterestSelectionRepository
    extends JpaRepository<UserInterestSelection, UUID> {

  Sort SELECTION_ORDER = Sort.by(UserInterestSelection_.SELECTION_ORDER);

  @EntityGraph(attributePaths = "interest")
  List<UserInterestSelection> findByUserId(UUID userId, Sort sort);

  @Query(
      """
      select selection.interest
      from UserInterestSelection selection
      where selection.userId = :userId
      order by selection.selectionOrder
      """)
  List<InterestTag> findInterestsByUserId(UUID userId);

  @EntityGraph(attributePaths = "interest")
  List<UserInterestSelection> findByUserIdIn(Collection<UUID> userIds);

  @Modifying
  @Transactional
  void deleteByUserId(UUID userId);
}
