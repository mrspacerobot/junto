package ch.junto.chat;

import static org.assertj.core.api.Assertions.assertThat;

import ch.junto.activity.ActivityRepository;
import ch.junto.activity.entity.Activity;
import ch.junto.base.TestContainerConfig;
import ch.junto.core.exception.RateLimitException;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ChatRateLimiterIntegrationTest extends TestContainerConfig {

  @Autowired ChatRateLimiter rateLimiter;
  @Autowired UserRepository userRepository;
  @Autowired ActivityRepository activityRepository;

  @Test
  void concurrentBurstCannotExceedSharedLimit() throws Exception {
    User user = userRepository.save(UserFixture.persistableWithUniqueEmail("chat-rate-user"));
    User host = userRepository.save(UserFixture.persistableWithUniqueEmail("chat-rate-host"));
    Activity activity = activityRepository.save(ActivityFixture.newActivity(host, "cycling"));

    int attempts = 20;
    ExecutorService executor = Executors.newFixedThreadPool(attempts);
    CountDownLatch ready = new CountDownLatch(attempts);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();
    for (int index = 0; index < attempts; index++) {
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                try {
                  rateLimiter.check(activity.getId(), user.getId());
                  return true;
                } catch (RateLimitException expected) {
                  return false;
                }
              }));
    }
    ready.await();
    start.countDown();

    int accepted = 0;
    for (Future<Boolean> future : futures) if (future.get()) accepted++;
    executor.shutdown();
    assertThat(accepted).isEqualTo(ChatRateLimiter.MAX_MESSAGES);
  }
}
