package ch.junto.core.health;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadinessController {

  private static final Map<String, String> READY_BODY = Map.of("status", "ok");
  private static final Map<String, String> UNAVAILABLE_BODY = Map.of("status", "unavailable");

  private final ReadinessCheck readinessCheck;

  public ReadinessController(ReadinessCheck readinessCheck) {
    this.readinessCheck = readinessCheck;
  }

  @GetMapping("/healthz")
  public ResponseEntity<Map<String, String>> healthz() {
    return readinessCheck.isReady()
        ? ResponseEntity.ok(READY_BODY)
        : ResponseEntity.status(503).body(UNAVAILABLE_BODY);
  }
}
