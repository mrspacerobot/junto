package com.onda.auth;

import com.onda.shared.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {
    Optional<AuthToken> findByEmailAndCodeAndUsedAtIsNull(String email, String code);
    Optional<AuthToken> findByTokenAndUsedAtIsNull(UUID token);
    int deleteByExpiresAtBefore(OffsetDateTime expiresAt);
}
