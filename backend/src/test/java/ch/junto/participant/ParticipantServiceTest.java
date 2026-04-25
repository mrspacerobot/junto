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
    void leave_participant_removesParticipant() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setRole(ParticipantRole.PARTICIPANT);
        participant.setActivity(activity);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(participant));

        participantService.leave(activity.getId(), user);

        verify(participantRepository).delete(participant);
    }

    @Test
    void leave_creator_throwsConflict() {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setRole(ParticipantRole.CREATOR);
        participant.setActivity(activity);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void leave_notParticipant_throwsNotFoundException() {
        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void leave_beforeDeadline_deletesParticipant() {
        activity.setSignupDeadline(OffsetDateTime.now().plusHours(1));
        ActivityParticipant participant = new ActivityParticipant();
        participant.setRole(ParticipantRole.PARTICIPANT);
        participant.setActivity(activity);

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));
        when(participantRepository.findByActivityIdAndUserId(activity.getId(), user.getId()))
            .thenReturn(Optional.of(participant));

        participantService.leave(activity.getId(), user);

        verify(participantRepository).delete(participant);
    }

    @Test
    void leave_afterDeadline_throwsConflict() {
        activity.setSignupDeadline(OffsetDateTime.now().minusHours(1));

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Signup deadline has passed");
    }

    @Test
    void leave_noDeadline_afterStartsAt_throwsConflict() {
        activity.setSignupDeadline(null);
        activity.setStartsAt(OffsetDateTime.now().minusHours(1));

        when(activityRepository.findById(activity.getId())).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> participantService.leave(activity.getId(), user))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Signup deadline has passed");
    }
}
