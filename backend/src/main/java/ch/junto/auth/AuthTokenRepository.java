package ch.junto.auth;

import ch.junto.shared.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    // --- Atomic consume operations ---

    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.usedAt = :now
        WHERE t.token = :token
          AND t.usedAt IS NULL
          AND t.expiresAt >= :now
        """)
    int consumeByToken(@Param("token") UUID token, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.usedAt = :now
        WHERE t.email = :email
          AND t.code = :code
          AND t.usedAt IS NULL
          AND t.expiresAt >= :now
        """)
    int consumeByEmailAndCode(@Param("email") String email, @Param("code") String code, @Param("now") OffsetDateTime now);

    // --- Attempt tracking ---

    @Modifying
    @Query("""
        UPDATE AuthToken t
        SET t.attemptCount = t.attemptCount + 1
        WHERE t.email = :email
          AND t.code = :code
          AND t.usedAt IS NULL
        """)
    int incrementAttemptCount(@Param("email") String email, @Param("code") String code);

    // --- Lookup (with usedAt filter) ---

    Optional<AuthToken> findByEmailAndCodeAndUsedAtIsNull(String email, String code);
    Optional<AuthToken> findByTokenAndUsedAtIsNull(UUID token);

    // --- Lookup (without usedAt filter — for post-consume metadata) ---

    Optional<AuthToken> findByToken(UUID token);
    Optional<AuthToken> findByEmailAndCode(String email, String code);

    // --- Cleanup ---

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.email = :email AND t.usedAt IS NULL")
    void deleteUnusedByEmail(@Param("email") String email);

    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
