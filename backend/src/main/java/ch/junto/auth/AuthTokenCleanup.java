package ch.junto.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenCleanup {

  private static final Logger log = LoggerFactory.getLogger(AuthTokenCleanup.class);
  private final AuthService authService;

  public AuthTokenCleanup(AuthService authService) {
    this.authService = authService;
  }

  @Scheduled(fixedRate = 60 * 60 * 1000)
  public void cleanExpiredTokens() {
    int deleted = authService.cleanExpiredTokens();
    if (deleted > 0) {
      log.info("Cleaned up {} expired auth tokens", deleted);
    }
  }
}
