package ch.junto.invite;

import static org.assertj.core.api.Assertions.assertThat;

import ch.junto.base.TestContainerConfig;
import ch.junto.fixtures.UserFixture;
import ch.junto.invite.entity.AccessCode;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AccessCodeConcurrencyIntegrationTest extends TestContainerConfig {

  @Autowired AccessCodeService accessCodeService;
  @Autowired AccessCodeRepository accessCodeRepository;
  @Autowired AccessCodeRedemptionRepository accessCodeRedemptionRepository;
  @Autowired UserRepository userRepository;
  @Autowired DataSource dataSource;

  @Test
  void oneSharedCodeAdmitsEveryConcurrentRedeemer() throws Exception {
    AccessCode accessCode = sharedCode();
    List<User> newcomers =
        IntStream.range(0, 8)
            .mapToObj(
                index -> userRepository.save(UserFixture.persistableWithUniqueEmail("member")))
            .toList();

    List<Boolean> outcomes =
        runConcurrently(
            newcomers, user -> accessCodeService.redeemIfAvailable(accessCode.getCode(), user));

    assertThat(outcomes).containsOnly(true);
    assertThat(accessCodeRedemptionRepository.countByAccessCodeId(accessCode.getId())).isEqualTo(8);
  }

  @Test
  void revocationIsTheOnlyThingThatStopsRedemption() throws Exception {
    AccessCode accessCode = sharedCode();
    User before = userRepository.save(UserFixture.persistableWithUniqueEmail("before"));
    User after = userRepository.save(UserFixture.persistableWithUniqueEmail("after"));

    assertThat(accessCodeService.redeemIfAvailable(accessCode.getCode(), before)).isTrue();
    assertThat(accessCodeService.revoke(accessCode.getCode())).isTrue();
    assertThat(accessCodeService.redeemIfAvailable(accessCode.getCode(), after)).isFalse();

    assertThat(accessCodeRedemptionRepository.countByAccessCodeId(accessCode.getId())).isEqualTo(1);
    assertThat(
            accessCodeRedemptionRepository.existsByAccessCodeIdAndUserId(
                accessCode.getId(), before.getId()))
        .isTrue();
  }

  @Test
  void concurrentRedeemersOfARevokedCodeAreAllRejected() throws Exception {
    AccessCode accessCode = sharedCode();
    accessCodeService.revoke(accessCode.getCode());
    List<User> newcomers =
        IntStream.range(0, 4)
            .mapToObj(index -> userRepository.save(UserFixture.persistableWithUniqueEmail("late")))
            .toList();

    List<Boolean> outcomes =
        runConcurrently(
            newcomers, user -> accessCodeService.redeemIfAvailable(accessCode.getCode(), user));

    assertThat(outcomes).containsOnly(false);
    assertThat(accessCodeRedemptionRepository.countByAccessCodeId(accessCode.getId())).isZero();
  }

  @Test
  void anInFlightOperatorRevocationSerializesBeforeRedemption() throws Exception {
    AccessCode accessCode = sharedCode();
    User newcomer = userRepository.save(UserFixture.persistableWithUniqueEmail("late"));
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try (var connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement(
                "UPDATE access_codes SET revoked_at = now() WHERE id = ?")) {
      connection.setAutoCommit(false);
      statement.setObject(1, accessCode.getId());
      assertThat(statement.executeUpdate()).isEqualTo(1);

      Future<Boolean> redemption =
          executor.submit(
              () -> accessCodeService.redeemIfAvailable(accessCode.getCode(), newcomer));
      awaitBlockedRedemption();

      connection.commit();

      assertThat(redemption.get(5, TimeUnit.SECONDS)).isFalse();
      assertThat(accessCodeRedemptionRepository.countByAccessCodeId(accessCode.getId())).isZero();
    } finally {
      executor.shutdownNow();
    }
  }

  private AccessCode sharedCode() {
    User administrator = userRepository.save(UserFixture.persistableWithUniqueEmail("admin"));
    return accessCodeService.create(
        "JUNTO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
        "pilot",
        administrator);
  }

  private <T> List<Boolean> runConcurrently(List<T> values, ThrowingFunction<T, Boolean> action)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(values.size());
    CountDownLatch ready = new CountDownLatch(values.size());
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> futures = new ArrayList<>();
    for (T value : values) {
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return action.apply(value);
              }));
    }
    ready.await();
    start.countDown();
    List<Boolean> results = new ArrayList<>();
    for (Future<Boolean> future : futures) results.add(future.get());
    executor.shutdown();
    return results;
  }

  private void awaitBlockedRedemption() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      try (var connection = dataSource.getConnection();
          var statement = connection.createStatement();
          var result =
              statement.executeQuery(
                  """
                  SELECT count(*)
                  FROM pg_stat_activity
                  WHERE datname = current_database()
                    AND wait_event_type = 'Lock'
                    AND query LIKE '%access_code_redemptions%'
                  """)) {
        result.next();
        if (result.getLong(1) >= 1) {
          return;
        }
      }
      Thread.sleep(25);
    }
    throw new AssertionError("redemption did not block behind the in-flight revocation");
  }

  @FunctionalInterface
  private interface ThrowingFunction<T, R> {
    R apply(T value) throws Exception;
  }
}
