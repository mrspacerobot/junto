package ch.junto.report;

import ch.junto.report.entity.Report;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {
  java.util.List<Report> findAllByStatus(ch.junto.report.enums.ReportStatus status);
}
