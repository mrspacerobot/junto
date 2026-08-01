package ch.junto.report;

import ch.junto.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    java.util.List<Report> findAllByStatus(ch.junto.report.enums.ReportStatus status);
}
