package ch.junto.invite;

import ch.junto.base.TestContainerConfig;
import ch.junto.core.exception.ApiException;
import ch.junto.fixtures.UserFixture;
import ch.junto.invite.entity.InviteCode;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InviteConcurrencyIntegrationTest extends TestContainerConfig {

    @Autowired InviteService inviteService;
    @Autowired InviteCodeRepository inviteCodeRepository;
    @Autowired UserRepository userRepository;

    @Test
    void oneInviteCanHaveOnlyOneConcurrentConsumer() throws Exception {
        User inviter = userRepository.save(UserFixture.persistableWithUniqueEmail("inviter"));
        User first = userRepository.save(UserFixture.persistableWithUniqueEmail("consumer-a"));
        User second = userRepository.save(UserFixture.persistableWithUniqueEmail("consumer-b"));
        String code = "JUNTO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        InviteCode invite = new InviteCode();
        invite.setCode(code);
        invite.setCreatedBy(inviter);
        inviteCodeRepository.save(invite);

        List<Boolean> outcomes = runConcurrently(List.of(first, second), user -> {
            try {
                inviteService.consumeCode(code, user);
                return true;
            } catch (ApiException expected) {
                return false;
            }
        });

        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        InviteCode consumed = inviteCodeRepository.findByCode(code).orElseThrow();
        assertThat(consumed.getUsedBy().getId()).isIn(first.getId(), second.getId());
    }

    @Test
    void concurrentGenerationCannotOverIssueQuota() throws Exception {
        User inviter = UserFixture.persistableWithUniqueEmail("quota");
        inviter.setInviteCodesRemaining(1);
        User savedInviter = userRepository.save(inviter);

        List<User> attempts = java.util.stream.IntStream.range(0, 8)
            .mapToObj(ignored -> savedInviter)
            .toList();
        List<Boolean> outcomes = runConcurrently(attempts, user -> {
            try {
                inviteService.generateCode(user.getId());
                return true;
            } catch (ApiException expected) {
                return false;
            }
        });

        assertThat(outcomes.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        assertThat(userRepository.findById(savedInviter.getId()).orElseThrow().getInviteCodesRemaining()).isZero();
        assertThat(inviteCodeRepository.findAllByCreatedById(savedInviter.getId())).hasSize(1);
    }

    private <T> List<Boolean> runConcurrently(
        List<T> values,
        ThrowingFunction<T, Boolean> action
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(values.size());
        CountDownLatch ready = new CountDownLatch(values.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (T value : values) {
            futures.add(executor.submit(() -> {
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

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T value) throws Exception;
    }
}
