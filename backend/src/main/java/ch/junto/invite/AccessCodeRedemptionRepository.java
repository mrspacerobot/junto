package ch.junto.invite;

import ch.junto.invite.entity.AccessCodeRedemption;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessCodeRedemptionRepository extends JpaRepository<AccessCodeRedemption, UUID> {

  /**
   * Records one admission in a single statement whose {@code revoked_at IS NULL} guard is evaluated
   * with the insert, so a revoked code can never gain a redemption. Zero rows means the code is
   * unknown or revoked; the caller must not distinguish the two.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
            INSERT INTO access_code_redemptions (id, access_code_id, user_id, redeemed_at)
            SELECT gen_random_uuid(), a.id, :userId, :redeemedAt
            FROM access_codes a
            WHERE a.code = :code
              AND a.revoked_at IS NULL
            FOR UPDATE OF a
            """,
      nativeQuery = true)
  int redeemIfAvailable(
      @Param("code") String code,
      @Param("userId") UUID userId,
      @Param("redeemedAt") OffsetDateTime redeemedAt);

  long countByAccessCodeId(UUID accessCodeId);

  boolean existsByAccessCodeIdAndUserId(UUID accessCodeId, UUID userId);
}
