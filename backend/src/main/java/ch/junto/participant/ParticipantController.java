package ch.junto.participant;

import ch.junto.auth.CurrentUser;
import ch.junto.participant.dto.ParticipantResponse;
import ch.junto.user.entity.User;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities/{activityId}/participants")
public class ParticipantController {

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    @GetMapping
    public List<ParticipantResponse> list(@PathVariable UUID activityId) {
        return participantService.listParticipants(activityId);
    }

    @PostMapping("/join")
    public ParticipantResponse join(@PathVariable UUID activityId, @CurrentUser User user) {
        return participantService.join(activityId, user);
    }

    @PostMapping("/leave")
    public void leave(@PathVariable UUID activityId, @CurrentUser User user) {
        participantService.leave(activityId, user);
    }

    @PostMapping("/{participantId}/approve")
    public void approve(@PathVariable UUID activityId, @PathVariable UUID participantId, @CurrentUser User user) {
        participantService.approve(activityId, participantId, user);
    }

    @PostMapping("/{participantId}/decline")
    public void decline(@PathVariable UUID activityId, @PathVariable UUID participantId, @CurrentUser User user) {
        participantService.decline(activityId, participantId, user);
    }
}
