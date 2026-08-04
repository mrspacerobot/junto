package ch.junto.moderation;

import ch.junto.core.exception.NotFoundException;
import ch.junto.moderation.entity.ModerationAction;
import ch.junto.moderation.enums.ModerationActionType;
import ch.junto.report.ReportRepository;
import ch.junto.report.entity.Report;
import ch.junto.report.enums.ReportStatus;
import ch.junto.user.UserService;
import ch.junto.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModerationService {

  private final ReportRepository reportRepository;
  private final ModerationActionRepository moderationActionRepository;
  private final UserService userService;

  public ModerationService(
      ReportRepository reportRepository,
      ModerationActionRepository moderationActionRepository,
      UserService userService) {
    this.reportRepository = reportRepository;
    this.moderationActionRepository = moderationActionRepository;
    this.userService = userService;
  }

  public List<Report> listPendingReports() {
    return reportRepository.findAllByStatus(ReportStatus.PENDING);
  }

  public void resolveReport(UUID reportId, ReportStatus status) {
    Report report =
        reportRepository
            .findById(reportId)
            .orElseThrow(() -> new NotFoundException("Report not found"));
    report.setStatus(status);
    reportRepository.save(report);
  }

  public ModerationAction takeAction(
      UUID targetUserId, ModerationActionType actionType, String reason, User moderator) {
    User targetUser = userService.findByIdOrThrow(targetUserId);

    if (actionType == ModerationActionType.SUSPEND) {
      userService.suspendUser(targetUserId);
    }

    ModerationAction action = new ModerationAction();
    action.setModerator(moderator);
    action.setTargetUser(targetUser);
    action.setActionType(actionType);
    action.setReason(reason);

    return moderationActionRepository.save(action);
  }
}
