package ch.junto.moderation;

import ch.junto.moderation.entity.ModerationAction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {}
