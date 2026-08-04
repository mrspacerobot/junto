package ch.junto.user;

import ch.junto.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @Modifying
  @Query(
      """
        UPDATE User u
        SET u.inviteCodesRemaining = u.inviteCodesRemaining - 1
        WHERE u.id = :userId AND u.inviteCodesRemaining > 0
        """)
  int decrementInviteQuota(@Param("userId") UUID userId);
}
