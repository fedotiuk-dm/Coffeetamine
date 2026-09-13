package ua.coffeetamine.notification.service;

import ua.coffeetamine.notification.domain.model.Notification;

/**
 * Outbound push delivery — best-effort fanout to every {@code Device} the recipient has registered.
 * Failures are logged but never propagate to the listener: a failed push must not roll back the
 * in-app notification, and a redelivered event must not trigger duplicate pushes (handled upstream
 * by the listener's existsBy de-dup check).
 */
public interface PushDispatcher {

  void dispatch(Notification notification);
}
