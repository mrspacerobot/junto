package ch.junto.participant;

import ch.junto.activity.entity.Activity;
import ch.junto.activity.enums.ActivityStatus;
import ch.junto.base.BaseIntegrationTest;
import ch.junto.chat.ChatMessageRepository;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.helpers.DatabaseHelper;
import ch.junto.helpers.MockMvcTestHelper;
import ch.junto.participant.entity.ActivityParticipant;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ParticipantLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired DatabaseHelper database;
    @Autowired ParticipantRepository participantRepository;
    @Autowired ChatMessageRepository chatMessageRepository;

    @Test
    void declinedParticipantCannotDeleteDecisionAndRejoin() throws Exception {
        User host = saveUser("host-decline");
        User participant = saveUser("declined");
        Activity activity = database.save(ActivityFixture.newActivity(host, "cycling"));
        ActivityParticipant row = database.save(ActivityParticipantFixture.participant(
            activity, participant, ParticipantRole.PARTICIPANT, ParticipantStatus.DECLINED));

        mockMvc.perform(post(url(activity, "/leave"))
                .session(MockMvcTestHelper.sessionFor(participant)))
            .andExpect(status().isConflict());
        mockMvc.perform(post(url(activity, "/join"))
                .session(MockMvcTestHelper.sessionFor(participant)))
            .andExpect(status().isConflict());

        assertThat(participantRepository.findById(row.getId())).isPresent();
    }

    @Test
    void pendingWithdrawalDeletesRequestWithoutFalseChatEvent() throws Exception {
        User host = saveUser("host-pending");
        User participant = saveUser("pending");
        Activity activity = database.save(ActivityFixture.newActivity(host, "cycling"));
        database.save(ActivityParticipantFixture.participant(
            activity, participant, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING));

        mockMvc.perform(post(url(activity, "/leave"))
                .session(MockMvcTestHelper.sessionFor(participant)))
            .andExpect(status().isOk());

        assertThat(participantRepository.findByActivityIdAndUserId(activity.getId(), participant.getId()))
            .isEmpty();
        assertThat(chatMessageRepository.findLatestByActivityId(activity.getId(), 10)).isEmpty();
    }

    @Test
    void approvalRequiresMatchingActivityActiveStateAndOpenDeadline() throws Exception {
        User host = saveUser("host-manage");
        User requester = saveUser("requester");
        Activity cancelled = ActivityFixture.newActivity(host, "cycling");
        cancelled.setStatus(ActivityStatus.CANCELLED);
        cancelled = database.save(cancelled);
        ActivityParticipant cancelledRequest = database.save(ActivityParticipantFixture.participant(
            cancelled, requester, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING));

        mockMvc.perform(post(url(cancelled, "/" + cancelledRequest.getId() + "/approve"))
                .session(MockMvcTestHelper.sessionFor(host)))
            .andExpect(status().isConflict());

        Activity expired = ActivityFixture.newActivity(host, "running");
        expired.setSignupDeadline(OffsetDateTime.now().minusMinutes(1));
        expired = database.save(expired);
        ActivityParticipant expiredRequest = database.save(ActivityParticipantFixture.participant(
            expired, requester, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING));

        mockMvc.perform(post(url(expired, "/" + expiredRequest.getId() + "/decline"))
                .session(MockMvcTestHelper.sessionFor(host)))
            .andExpect(status().isConflict());
    }

    @Test
    void participantIdFromAnotherActivityIsRejectedByNestedResourceContract() throws Exception {
        User host = saveUser("host-route");
        User requester = saveUser("requester-route");
        Activity first = database.save(ActivityFixture.newActivity(host, "cycling"));
        Activity second = database.save(ActivityFixture.newActivity(host, "running"));
        ActivityParticipant request = database.save(ActivityParticipantFixture.participant(
            first, requester, ParticipantRole.PARTICIPANT, ParticipantStatus.PENDING));

        mockMvc.perform(post(url(second, "/" + request.getId() + "/approve"))
                .session(MockMvcTestHelper.sessionFor(host)))
            .andExpect(status().isNotFound());
    }

    private User saveUser(String prefix) {
        return database.save(UserFixture.newUser(prefix + "-" + UUID.randomUUID() + "@junto.app"));
    }

    private String url(Activity activity, String suffix) {
        return "/api/activities/" + activity.getId() + "/participants" + suffix;
    }
}
