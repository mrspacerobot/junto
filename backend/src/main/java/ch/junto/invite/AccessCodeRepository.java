package ch.junto.invite;

import ch.junto.invite.entity.AccessCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessCodeRepository extends JpaRepository<AccessCode, UUID> {

  Optional<AccessCode> findByCode(String code);

  Optional<AccessCode> findByLabel(String label);

  boolean existsByCodeAndRevokedAtIsNull(String code);
}
