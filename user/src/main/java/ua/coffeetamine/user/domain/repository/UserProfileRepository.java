package ua.coffeetamine.user.domain.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ua.coffeetamine.user.domain.model.UserProfile;

@Repository
@Transactional(readOnly = true)
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {}
