package ua.coffeetamine.common.identity;

import java.util.UUID;

/**
 * Resolves an OIDC {@code (issuer, subject)} tuple to the internal {@code app_user_id} UUID used as
 * the primary key across all feature tables.
 *
 * <p>Resolution is just-in-time: on the first authenticated request from a given external identity,
 * a fresh {@link ExternalIdentity} row is inserted with a newly generated {@code app_user_id}. The
 * mapping is immutable once written.
 *
 * <p>Implementations must be safe under concurrent first-time requests for the same identity — the
 * DB unique constraint on {@code (issuer, subject)} is the source of truth.
 */
public interface UserIdentityResolver {

  /**
   * Returns the internal app user id for the given external identity, provisioning a fresh mapping
   * if none exists yet. {@code email} is best-effort metadata stored on the row; pass {@code null}
   * if unavailable.
   */
  UUID resolve(String issuer, String subject, String email);
}
