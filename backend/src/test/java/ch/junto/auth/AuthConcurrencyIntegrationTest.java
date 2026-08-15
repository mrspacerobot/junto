package ch.junto.auth;

import ch.junto.base.TestContainerConfig;
import ch.junto.core.exception.ApiException;
import ch.junto.core.exception.RateLimitException;
import ch.junto.email.EmailService;
import ch.junto.fixtures.UserFixture;
import ch.junto.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AuthConcurrencyIntegrationTest extends TestContainerConfig {

    @Autowired AuthService authService;
    @Autowired AuthRateLimiter rateLimiter;
    @Autowired AuthTokenRepository authTokenRepository;
    @Autowired UserRepository userRepository;
    @MockitoBean EmailService emailService;

    @Test
    void concurrentWrongGuessesAtomicallyStopAtFiveAttempts() throws Exception {
        String email = "otp-race-" + UUID.randomUUID() + "@junto.app";
        userRepository.save(UserFixture.newMember(email));
        authService.requestSignInCode(email);

        runConcurrently(10, index -> {
            String guess = String.format("%06d", 800000 + index);
            try {
                authService.verifyCode(email, guess);
            } catch (ApiException expected) {
                // Every call is rejected; only five may increment the row.
            }
        });

        assertThat(authTokenRepository
            .findFirstByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)
            .orElseThrow()
            .getAttemptCount()).isEqualTo(5);
    }

    @Test
    void concurrentCodeRequestsSerializeTokenReplacement() throws Exception {
        String email = "code-request-race-" + UUID.randomUUID() + "@junto.app";
        userRepository.save(UserFixture.newMember(email));

        runConcurrently(5, index -> authService.requestSignInCode(email));

        assertThat(authTokenRepository.findAll())
            .filteredOn(token -> token.getEmail().equals(email) && !token.isUsed())
            .hasSize(1);
        verify(emailService, times(5)).sendAuthCode(eq(email), anyString(), anyString());
    }

    @Test
    void concurrentAccountThrottleAllowsOnlyConfiguredRequestBurst() throws Exception {
        String identity = "rate-" + UUID.randomUUID() + "@junto.app";
        List<Boolean> accepted = java.util.Collections.synchronizedList(new ArrayList<>());

        runConcurrently(10, index -> {
            try {
                rateLimiter.checkCodeRequest(identity, "source-" + index);
                accepted.add(true);
            } catch (RateLimitException expected) {
                accepted.add(false);
            }
        });

        assertThat(accepted).hasSize(10);
        assertThat(accepted.stream().filter(Boolean::booleanValue).count()).isEqualTo(3);
    }

    private void runConcurrently(int count, ThrowingIntConsumer action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int captured = index;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                action.accept(captured);
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> future : futures) future.get();
        executor.shutdown();
    }

    @FunctionalInterface
    private interface ThrowingIntConsumer {
        void accept(int value) throws Exception;
    }
}
