package ch.junto.report;

import ch.junto.auth.CurrentUser;
import ch.junto.report.entity.Report;
import ch.junto.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public Report submit(@RequestBody @Valid ReportService.ReportCreateRequest request, @CurrentUser User user) {
        return reportService.submitReport(request, user);
    }
}
