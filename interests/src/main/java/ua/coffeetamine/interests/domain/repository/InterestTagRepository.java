package ua.coffeetamine.interests.domain.repository;

import static ua.coffeetamine.common.domain.repository.GenericSpecification.byId;
import static ua.coffeetamine.common.domain.repository.GenericSpecification.idIn;
import static ua.coffeetamine.common.domain.repository.GenericSpecification.isActive;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.common.domain.repository.SortableRepository;
import ua.coffeetamine.interests.domain.model.InterestTag;

@Repository
@Transactional(readOnly = true)
public interface InterestTagRepository
    extends JpaRepository<InterestTag, UUID>, SortableRepository<InterestTag> {

  boolean existsByCode(String code);

  default Optional<InterestTag> findActiveById(UUID id) {
    return findOne(Specification.allOf(byId(id), isActive()));
  }

  default List<InterestTag> findAllActiveByIds(Collection<UUID> ids) {
    return findAll(Specification.allOf(idIn(ids), isActive()));
  }

  default Page<InterestTag> findCatalog(
      String search, String category, Boolean activeOnly, Pageable pageable) {
    return findAll(InterestTagSpec.catalogFilter(search, category, activeOnly), pageable);
  }
}
