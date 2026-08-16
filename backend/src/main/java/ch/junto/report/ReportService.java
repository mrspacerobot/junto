package ch.junto.report;

import ch.junto.report.entity.Report;
import ch.junto.report.enums.ReportStatus;
import ch.junto.report.enums.ReportTargetType;
import ch.junto.user.entity.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

  private final ReportRepository reportRepository;

  public ReportService(ReportRepository reportRepository) {
    this.reportRepository = reportRepository;
  }

  public record ReportCreateRequest(
      ReportTargetType targetType, UUID targetId, String reason, String content) {}

  @Transactional
  public Report submitReport(ReportCreateRequest request, User reporter) {
    Report report = new Report();
    report.setReporter(reporter);
    report.setTargetType(request.targetType());
    report.setTargetId(request.targetId());
    report.setReason(request.reason());
    report.setContent(request.content());
    report.setStatus(ReportStatus.PENDING);
    return reportRepository.save(report);
  }
}
