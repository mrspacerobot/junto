package ch.junto.chat;

import ch.junto.activity.ActivityRepository;
import ch.junto.chat.dto.ChatMessageResponse;
import ch.junto.chat.event.ChatMessageCommittedEvent;
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.ChatMessageFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.participant.ParticipantRepository;
import ch.junto.activity.entity.Activity;
import ch.junto.chat.entity.ChatMessage;
import ch.junto.user.entity.User;
import ch.junto.chat.enums.MessageType;
import ch.junto.core.exception.ForbiddenException;
import ch.junto.core.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ActivityRepository activityRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ChatRateLimiter rateLimiter;

    private ChatService chatService;

    private User user;
    private User otherUser;
    private Activity activity;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(
            chatMessageRepository, activityRepository, participantRepository, eventPublisher, rateLimiter);
        user = UserFixture.defaultUser();
        otherUser = UserFixture.userWithEmail("other@junto.app");
        activity = ActivityFixture.defaultActivity(UserFixture.userWithEmail("host@ex.com"), "climbing");
    }

    @Test
    void sendMessage_approvedParticipant_savesAndBroadcasts() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ChatMessageResponse result = chatService.sendMessage(activity.getId(), "Hello", user);

        assertThat(result.content()).isEqualTo("Hello");
        verify(chatMessageRepository).save(any());
        verify(eventPublisher).publishEvent(any(ChatMessageCommittedEvent.class));
    }

    @Test
    void sendMessage_twoApprovedParticipants_preservesSenderIdentityAndBroadcastsBothMessages() {
        List<ChatMessage> savedMessages = new ArrayList<>();
        List<ChatMessageResponse> broadcasts = new ArrayList<>();

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(eq(activity.getId()), any()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.save(any())).thenAnswer(i -> {
            ChatMessage message = i.getArgument(0);
            message.setId(UUID.randomUUID());
            message.setCreatedAt(OffsetDateTime.now());
            message.setUpdatedAt(OffsetDateTime.now());
            savedMessages.add(message);
            return message;
        });
        doAnswer(invocation -> {
            ChatMessageCommittedEvent event = invocation.getArgument(0);
            broadcasts.add(event.message());
            return null;
        }).when(eventPublisher).publishEvent(any(ChatMessageCommittedEvent.class));

        ChatMessageResponse first = chatService.sendMessage(activity.getId(), "Can you bring the rope?", user);
        ChatMessageResponse second = chatService.sendMessage(activity.getId(), "Yes, I have it packed.", otherUser);

        assertThat(List.of(first, second))
            .extracting(ChatMessageResponse::userId, ChatMessageResponse::displayName, ChatMessageResponse::content)
            .containsExactly(
                tuple(user.getId(), user.getDisplayName(), "Can you bring the rope?"),
                tuple(otherUser.getId(), otherUser.getDisplayName(), "Yes, I have it packed.")
            );

        assertThat(savedMessages)
            .extracting(message -> message.getUser().getId(), ChatMessage::getContent, ChatMessage::getType)
            .containsExactly(
                tuple(user.getId(), "Can you bring the rope?", MessageType.USER),
                tuple(otherUser.getId(), "Yes, I have it packed.", MessageType.USER)
            );

        assertThat(broadcasts)
            .extracting(ChatMessageResponse::userId, ChatMessageResponse::content)
            .containsExactly(
                tuple(user.getId(), "Can you bring the rope?"),
                tuple(otherUser.getId(), "Yes, I have it packed.")
            );
    }

    @Test
    void sendMessage_pendingParticipant_throwsForbidden() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.pendingParticipant(activity, user)));

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
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));

        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), "   ", user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_contentOver2000Chars_throwsIllegalArgument() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));

        String longContent = "a".repeat(2001);
        assertThatThrownBy(() -> chatService.sendMessage(activity.getId(), longContent, user))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendMessage_contentExactly2000Chars_succeeds() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String content = "a".repeat(2000);
        ChatMessageResponse result = chatService.sendMessage(activity.getId(), content, user);

        assertThat(result.content()).hasSize(2000);
    }

    @Test
    void sendMessage_trimsContentBeforePersisting() {
        List<ChatMessage> savedMessages = new ArrayList<>();

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.save(any())).thenAnswer(i -> {
            ChatMessage message = i.getArgument(0);
            savedMessages.add(message);
            return message;
        });

        ChatMessageResponse result = chatService.sendMessage(activity.getId(), "  Hello there  ", user);

        assertThat(result.content()).isEqualTo("Hello there");
        assertThat(savedMessages).extracting(ChatMessage::getContent).containsExactly("Hello there");
    }

    @Test
    void sendSystemMessage_insertsAndBroadcasts() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(chatMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        chatService.sendSystemMessage(activity.getId(), "User joined the activity");

        verify(chatMessageRepository).save(argThat(msg ->
            msg.getType() == MessageType.SYSTEM && msg.getUser() == null
        ));
        verify(eventPublisher).publishEvent(any(ChatMessageCommittedEvent.class));
    }

    @Test
    void getMessages_nonParticipant_throwsForbidden() {
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(activity.getId(), user, 50, null))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMessages_withCursor_fetchesBeforeCursor() {
        UUID cursorId = UUID.randomUUID();
        ChatMessage cursorMsg = ChatMessageFixture.cursorMessage(activity, OffsetDateTime.now());
        cursorMsg.setId(cursorId);

        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.findById(cursorId)).thenReturn(Optional.of(cursorMsg));
        when(chatMessageRepository.findByActivityIdBeforeCursor(any(), any(), any(), anyInt())).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 50, cursorId);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findByActivityIdBeforeCursor(eq(activity.getId()), eq(cursorMsg.getCreatedAt()), eq(cursorId), eq(50));
    }

    @Test
    void getMessages_cursorFromDifferentActivity_throwsNotFound() {
        Activity otherActivity = ActivityFixture.defaultActivity(UserFixture.userWithEmail("other-host@ex.com"), "cycling");
        ChatMessage cursorMsg = ChatMessageFixture.cursorMessage(otherActivity, OffsetDateTime.now());

        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.findById(cursorMsg.getId())).thenReturn(Optional.of(cursorMsg));

        assertThatThrownBy(() -> chatService.getMessages(activity.getId(), user, 50, cursorMsg.getId()))
            .isInstanceOf(NotFoundException.class);

        verify(chatMessageRepository, never()).findByActivityIdBeforeCursor(any(), any(), any(), anyInt());
    }

    @Test
    void getMessages_noCursor_fetchesLatest() {
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.findLatestByActivityId(any(), anyInt())).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 50, null);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findLatestByActivityId(eq(activity.getId()), eq(50));
    }

    @Test
    void getMessages_cursorNotFound_throwsNotFoundException() {
        UUID cursorId = UUID.randomUUID();

        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.findById(cursorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getMessages(activity.getId(), user, 50, cursorId))
            .isInstanceOf(NotFoundException.class);

        verify(chatMessageRepository, never()).findByActivityIdBeforeCursor(any(), any(), any(), anyInt());
    }

    @Test
    void getMessages_limitBelowOne_clampsToOne() {
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.findLatestByActivityId(activity.getId(), 1)).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 0, null);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findLatestByActivityId(activity.getId(), 1);
    }

    @Test
    void getMessages_limitAboveOneHundred_clampsToOneHundred() {
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(ActivityParticipantFixture.approvedParticipant(activity, user)));
        when(chatMessageRepository.findLatestByActivityId(activity.getId(), 100)).thenReturn(List.of());

        List<ChatMessageResponse> result = chatService.getMessages(activity.getId(), user, 500, null);

        assertThat(result).isEmpty();
        verify(chatMessageRepository).findLatestByActivityId(activity.getId(), 100);
    }
}
