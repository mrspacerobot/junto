package ch.junto.chat;

import ch.junto.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query(value = """
        SELECT * FROM chat_messages
        WHERE activity_id = :activityId
          AND (created_at, id) < (:cursorCreatedAt, :cursorId)
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChatMessage> findByActivityIdBeforeCursor(
        @Param("activityId") UUID activityId,
        @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
        @Param("cursorId") UUID cursorId,
        @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM chat_messages
        WHERE activity_id = :activityId
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChatMessage> findLatestByActivityId(
        @Param("activityId") UUID activityId,
        @Param("limit") int limit
    );
}
