package ch.junto.chat;

import ch.junto.activity.ActivityRepository;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.participant.ParticipantRepository;
import ch.junto.activity.entity.Activity;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.user.entity.User;
import ch.junto.chat.enums.MessageType;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import ch.junto.core.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatService chatService;

    private User user;
    private Activity activity;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatMessageRepository, activityRepository, participantRepository, messagingTemplate);
        user = UserFixture.defaultUser();
        activity = ActivityFixture.defaultActivity(UserFixture.userWithEmail("host@ex.com"), "climbing");
    }

    @Test
    void sendMessage_approvedParticipant_savesAndBroadcasts() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatMessageResponse result = chatService.sendMessage(activity.getId(), "Hello", user);

        assertThat(result.content()).isEqualTo("Hello");
        verify(chatMessageRepository).save(any());
        verify(messagingTemplate).convertAndSend(eq("/topic/activity/" + activity.getId()), any(ChatMessageResponse.class));
    }

    @Test
    void sendMessage_pendingParticipant_throwsForbidden() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.PENDING);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "Hello", user))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessage_nonParticipant_throwsForbidden() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "Hello", user))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sendMessage_activityNotFound_throwsNotFoundException() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "Hello", user))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void sendMessage_blankContent_throwsIllegalArgument() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "   ", user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_contentOver2000Chars_throwsIllegalArgument() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));

        String longContent = "a".repeat(2001);
        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), longContent, user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_contentExactly2000Chars_succeeds() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String content = "a".repeat(2000);
        ChatMessageResponse result = chatService.sendMessage(activity.getId(), content, user);

        assertThat(result.content()).hasSize(2000);
    }

    @Test
    void sendSystemMessage_insertsAndBroadcasts() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        chatService.sendSystemMessage(activity.getId(), "User joined the activity");

        verify(chatMessageRepository).save(argThat(msg ->
            msg.getType() == MessageType.SYSTEM && msg.getUser() == null
        ));
        verify(messagingTemplate).convertAndSend(eq("/topic/activity/" + activity.getId()), any(ChatMessageResponse.class));
    }

    @Test
    void getMessages_nonParticipant_throwsForbidden() {
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(activity.getId(), user, 50, null))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMessages_withCursor_fetchesBeforeCursor() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        UUID cursorId = UUID.randomUUID();
        ChatMessage cursorMsg = new ChatMessage();
        cursorMsg.setId(cursorId);
        cursorMsg.setCreatedAt(OffsetDateTime.now());

        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.findById(cursorId)).thenReturn(Optional.of(cursorMsg));
        when(chatMessageRepository.findByActivityIdBeforeCursor(any(), any(), any(), anyInt())).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 50, cursorId);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findByActivityIdBeforeCursor(eq(activity.getId()), eq(cursorMsg.getCreatedAt()), eq(cursorId), eq(50));
    }

    @Test
    void getMessages_noCursor_fetchesLatest() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setStatus(ParticipantStatus.APPROVED);

        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.of(participant));
        when(chatMessageRepository.findLatestByActivityId(any(), anyInt())).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 50, null);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findLatestByActivityId(eq(activity.getId()), eq(50));
    }
}
