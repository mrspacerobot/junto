package ch.junto.invite;

import ch.junto.invite.entity.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {
    Optional<InviteCode> findByCode(String code);
    boolean existsByCodeAndUsedAtIsNullAndRevokedAtIsNull(String code);
    List<InviteCode> findAllByCreatedById(UUID userId);
    int countByUsedById(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE InviteCode i
        SET i.usedBy = :user, i.usedAt = :usedAt
        WHERE i.code = :code
          AND i.usedAt IS NULL
          AND i.revokedAt IS NULL
        """)
    int consumeIfAvailable(
        @Param("code") String code,
        @Param("user") ch.junto.user.entity.User user,
        @Param("usedAt") OffsetDateTime usedAt
    );
}
