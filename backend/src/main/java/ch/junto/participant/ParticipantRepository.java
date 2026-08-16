package ch.junto.participant;

import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipantRepository
    extends JpaRepository<ActivityParticipant, UUID>,
        JpaSpecificationExecutor<ActivityParticipant> {
  List<ActivityParticipant> findAllByActivityId(UUID activityId);

  List<ActivityParticipant> findAllByActivityIdAndStatus(UUID activityId, ParticipantStatus status);

  @Query(
      """
        SELECT p FROM ActivityParticipant p
        JOIN FETCH p.user
        WHERE p.activity.id IN :activityIds AND p.status = :status
        ORDER BY p.activity.id, p.createdAt, p.id
        """)
  List<ActivityParticipant> findAllByActivityIdsAndStatusWithUsers(
      @Param("activityIds") List<UUID> activityIds, @Param("status") ParticipantStatus status);

  Optional<ActivityParticipant> findByActivityIdAndUserId(UUID activityId, UUID userId);

  Optional<ActivityParticipant> findByIdAndActivityId(UUID id, UUID activityId);

  boolean existsByActivityIdAndUserIdAndStatusAndUserStatus(
      UUID activityId, UUID userId, ParticipantStatus status, UserStatus userStatus);

  long countByActivityIdAndStatus(UUID activityId, ParticipantStatus status);

  @Query(
      "SELECT p.activity.id FROM ActivityParticipant p WHERE p.user.id = :userId AND p.status = :status")
  List<UUID> findActivityIdsByUserIdAndStatus(
      @Param("userId") UUID userId, @Param("status") ParticipantStatus status);
}
