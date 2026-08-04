package ch.junto.job;

import ch.junto.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(AuthTokenCleanupJob.class);
  private final AuthService authService;

  public AuthTokenCleanupJob(AuthService authService) {
    this.authService = authService;
  }

  @Scheduled(cron = "0 0 * * * *") // Hourly
  public void cleanup() {
    int deleted = authService.cleanExpiredTokens();
    if (deleted > 0) {
      log.info("Cleaned up {} expired auth tokens", deleted);
    }
  }
}
