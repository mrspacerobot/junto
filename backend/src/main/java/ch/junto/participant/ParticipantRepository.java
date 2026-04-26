package ch.junto.participant;

import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<ActivityParticipant, UUID>, JpaSpecificationExecutor<ActivityParticipant> {
    List<ActivityParticipant> findAllByActivityId(UUID activityId);
    List<ActivityParticipant> findAllByActivityIdAndStatus(UUID activityId, ParticipantStatus status);
    Optional<ActivityParticipant> findByActivityIdAndUserId(UUID activityId, UUID userId);
    long countByActivityIdAndStatus(UUID activityId, ParticipantStatus status);

    @Query("SELECT p.activity.id FROM ActivityParticipant p WHERE p.user.id = :userId AND p.status = :status")
    List<UUID> findActivityIdsByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") ParticipantStatus status);
}
