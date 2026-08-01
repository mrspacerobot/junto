package ch.junto.participant;

import ch.junto.activity.ActivityRepository;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.participant.dto.ParticipantResponse;
import ch.junto.activity.entity.Activity;
import ch.junto.user.entity.User;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.activity.enums.JoinPolicy;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.core.exception.ConflictException;
import ch.junto.core.exception.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock
    private ParticipantRepository participantRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ParticipantService participantService;

    private User user;
    private Activity activity;

    @BeforeEach
    void setUp() {
        user = UserFixture.defaultUser();
        activity = ActivityFixture.defaultActivity(UserFixture.userWithEmail("host@example.com"), "climbing");
    }

    @Test
    void join_openActivity_approvesImmediately() {
        activity.setJoinPolicy(JoinPolicy.OPEN);
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ParticipantResponse result = participantService.join(activity.getId(), user);

        assertThat(result.status()).isEqualTo(ParticipantStatus.APPROVED);
        verify(participantRepository).save(any());
    }

    @Test
    void join_approvalRequired_setsToPending() {
        activity.setJoinPolicy(JoinPolicy.APPROVAL_REQUIRED);
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ParticipantResponse result = participantService.join(activity.getId(), user);

        assertThat(result.status()).isEqualTo(ParticipantStatus.PENDING);
    }

    @Test
    void join_afterDeadline_throwsException() {
        activity.setSignupDeadline(OffsetDateTime.now().minusHours(1));
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> participantService.join(activity.getId(), user))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void join_alreadyParticipant_throwsConflict() {
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(new ActivityParticipant()));

        assertThatThrownBy(() -> participantService.join(activity.getId(), user))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void join_activityFull_throwsConflict() {
        activity.setMaxParticipants(5);
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId())).thenReturn(Optional.empty());
        when(participantRepository.countByActivityIdAndStatus(activity.getId(), ParticipantStatus.APPROVED)).thenReturn(5L);

        assertThatThrownBy(() -> participantService.join(activity.getId(), user))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void join_inactiveActivity_throwsConflict() {
        activity.setStatus(ActivityStatus.CANCELLED);
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> participantService.join(activity.getId(), user))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void join_activityNotFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(activityRepository.findByIdWithLock(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> participantService.join(id, user))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void leave_approvedParticipant_removesParticipantAndPublishesEvent() {
        ActivityParticipant participant = participant(ParticipantRole.PARTICIPANT, ParticipantStatus.APPROVED);
        stubLeave(participant);

        participantService.leave(activity.getId(), user);

        verify(participantRepository).delete(participant);
        verify(eventPublisher).publishEvent(any(ch.junto.chat.event.ParticipantLeftEvent.class));
    }

    @Test
    void leave_pendingParticipant_removesWithoutChatEvent() {
        ActivityParticipant participant = participant(ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING);
        stubLeave(participant);

        participantService.leave(activity.getId(), user);

        verify(participantRepository).delete(participant);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void leave_creator_throwsConflict() {
        ActivityParticipant participant = participant(ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
        stubLeave(participant);

        assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void leave_declinedParticipant_preservesDecision() {
        ActivityParticipant participant = participant(ParticipantRole.PARTICIPANT, ParticipantStatus.DECLINED);
        stubLeave(participant);

        assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
            .isInstanceOf(ConflictException.class);
        verify(participantRepository, never()).delete(any(ActivityParticipant.class));
    }

    @Test
    void leave_notParticipant_throwsNotFoundException() {
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
            .isInstanceOf(NotFoundException.class);
    }

    private ActivityParticipant participant(ParticipantRole role, ParticipantStatus status) {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setRole(role);
        participant.setStatus(status);
        participant.setActivity(activity);
        return participant;
    }

    private void stubLeave(ActivityParticipant participant) {
        when(activityRepository.findByIdWithLock(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(participant));
    }
}
