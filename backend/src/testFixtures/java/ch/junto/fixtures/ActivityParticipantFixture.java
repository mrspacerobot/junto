package ch.junto.fixtures;

import ch.junto.activity.entity.Activity;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;

public final class ActivityParticipantFixture {

    private ActivityParticipantFixture() {}

    public static ActivityParticipant approvedCreator(Activity activity, User user) {
        return participant(activity, user, ParticipantRole.CREATOR, ParticipantStatus.APPROVED);
    }

    public static ActivityParticipant approvedParticipant(Activity activity, User user) {
        return participant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.APPROVED);
    }

    public static ActivityParticipant pendingParticipant(Activity activity, User user) {
        return participant(activity, user, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING);
    }

    public static ActivityParticipant participant(
        Activity activity,
        User user,
        ParticipantRole role,
        ParticipantStatus status
    ) {
        ActivityParticipant participant = new ActivityParticipant();
        participant.setActivity(activity);
        participant.setUser(user);
        participant.setRole(role);
        participant.setStatus(status);
        return participant;
    }
}
