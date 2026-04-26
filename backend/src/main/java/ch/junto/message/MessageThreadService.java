package ch.junto.message;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.ActivityRepository;
import ch.junto.chat.ChatMessageRepository;
import ch.junto.chat.ChatMessageRepository.LastMessageProjection;
import ch.junto.message.dto.MessageThreadResponse;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MessageThreadService {

    private final ParticipantRepository participantRepository;
    private final ActivityRepository activityRepository;
    private final ChatMessageRepository chatMessageRepository;

    public MessageThreadService(
            ParticipantRepository participantRepository,
            ActivityRepository activityRepository,
            ChatMessageRepository chatMessageRepository) {
        this.participantRepository = participantRepository;
        this.activityRepository = activityRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<MessageThreadResponse> getThreads(User user) {
        List<UUID> activityIds = participantRepository
            .findActivityIdsByUserIdAndStatus(user.getId(), ParticipantStatus.APPROVED);

        if (activityIds.isEmpty()) {
            return List.of();
        }

        List<Activity> activities = activityRepository.findAllById(activityIds);
        Map<UUID, Activity> activityMap = activities.stream()
            .collect(Collectors.toMap(Activity::getId, a -> a));

        Map<UUID, LastMessageProjection> lastMessageMap = chatMessageRepository
            .findLatestMessagesByActivityIds(activityIds)
            .stream()
            .collect(Collectors.toMap(LastMessageProjection::getActivityId, m -> m));

        Map<UUID, Long> countMap = new HashMap<>();
        for (UUID actId : activityIds) {
            countMap.put(actId, participantRepository.countByActivityIdAndStatus(actId, ParticipantStatus.APPROVED));
        }

        List<MessageThreadResponse> threads = new ArrayList<>();
        for (UUID actId : activityIds) {
            Activity a = activityMap.get(actId);
            if (a == null) continue;

            LastMessageProjection lastMsg = lastMessageMap.get(actId);

            threads.add(new MessageThreadResponse(
                actId,
                a.getTitle(),
                a.getTypeSlug(),
                a.getTypeSlug(),
                a.getImageUrl(),
                countMap.getOrDefault(actId, 0L).intValue(),
                List.of(),
                lastMsg != null ? lastMsg.getLastContent() : null,
                lastMsg != null ? lastMsg.getLastSender() : null,
                lastMsg != null ? lastMsg.getLastAt() : null
            ));
        }

        threads.sort((a, b) -> {
            if (a.lastMessageAt() == null && b.lastMessageAt() == null) return 0;
            if (a.lastMessageAt() == null) return 1;
            if (b.lastMessageAt() == null) return -1;
            return b.lastMessageAt().compareTo(a.lastMessageAt());
        });

        return threads;
    }
}
