package ua.coffeetamine.notification.domain.repository;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import ua.coffeetamine.common.domain.model.BaseEntity_;
import ua.coffeetamine.common.domain.repository.GenericSpecification;
import ua.coffeetamine.notification.domain.model.Notification;
import ua.coffeetamine.notification.domain.model.Notification_;

import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationSpec {

  public static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, BaseEntity_.CREATED_AT);

  public static Specification<Notification> owned(UUID userId, Boolean isRead) {
    return GenericSpecification.<Notification>spec()
        .and(GenericSpecification.eq(Notification_.USER_ID, userId))
        .and(isRead, r -> GenericSpecification.eq(Notification_.READ, r))
        .build();
  }
}
