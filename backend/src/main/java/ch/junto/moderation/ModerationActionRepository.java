package ch.junto.moderation;

import ch.junto.moderation.entity.ModerationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {
}
