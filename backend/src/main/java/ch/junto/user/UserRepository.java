package ch.junto.user;

import ch.junto.user.entity.User;
import ch.junto.user.enums.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  List<User> findAllByRole(UserRole role);

  @Modifying
  @Query(
      """
        UPDATE User u
        SET u.inviteCodesRemaining = u.inviteCodesRemaining - 1
        WHERE u.id = :userId AND u.inviteCodesRemaining > 0
        """)
  int decrementInviteQuota(@Param("userId") UUID userId);
}
