package ch.junto.invite;

import ch.junto.invite.entity.AccessCode;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {

  Optional<AccessCode> findByCode(String code);

  Optional<AccessCode> findByLabel(String label);

  boolean existsByCodeAndRevokedAtIsNull(String code);

  /** Operator revocation: the same is-revoked column that disables an invite code. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
        UPDATE AccessCode a
        SET a.revokedAt = :revokedAt
        WHERE a.code = :code
          AND a.revokedAt IS NULL
        """)
  int revokeIfActive(@Param("code") String code, @Param("revokedAt") OffsetDateTime revokedAt);
}
