package ch.junto.moderation;

import ch.junto.auth.CurrentUser;
import ch.junto.moderation.entity.ModerationAction;
import ch.junto.report.entity.Report;
import ch.junto.user.entity.User;
import ch.junto.moderation.enums.ModerationActionType;
import ch.junto.report.enums.ReportStatus;
import ch.junto.user.enums.UserRole;
import ch.junto.core.exception.ForbiddenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    private void checkModerator(User user) {
        if (user.getRole() != UserRole.MODERATOR && user.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only moderators can perform this action");
        }
    }

    @GetMapping("/reports")
    public List<Report> listReports(@CurrentUser User user) {
        checkModerator(user);
        return moderationService.listPendingReports();
    }

    @PostMapping("/reports/{reportId}/resolve")
    public void resolveReport(@PathVariable UUID reportId, @RequestParam ReportStatus status, @CurrentUser User user) {
        checkModerator(user);
        moderationService.resolveReport(reportId, status);
    }

    public record TakeActionRequest(@NotNull UUID targetUserId, @NotNull ModerationActionType actionType, String reason) {}

    @PostMapping("/action")
    public ModerationAction takeAction(@RequestBody @Valid TakeActionRequest request, @CurrentUser User user) {
        checkModerator(user);
        return moderationService.takeAction(request.targetUserId(), request.actionType(), request.reason(), user);
    }
}
