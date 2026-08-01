package ch.junto.websocket;

import ch.junto.activity.ActivityRepository;
import ch.junto.activity.entity.Activity;
import ch.junto.auth.SessionPrincipal;
import ch.junto.base.TestContainerConfig;
import ch.junto.chat.ChatMessageRepository;
import ch.junto.chat.ChatService;
import ch.junto.fixtures.ActivityFixture;
import ch.junto.fixtures.ActivityParticipantFixture;
import ch.junto.fixtures.UserFixture;
import ch.junto.participant.ParticipantRepository;
import ch.junto.participant.ParticipantService;
import ch.junto.participant.enums.ParticipantRole;
import ch.junto.participant.enums.ParticipantStatus;
import ch.junto.user.UserRepository;
import ch.junto.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketRevocationIntegrationTest extends TestContainerConfig {

    @LocalServerPort int port;
    @Autowired UserRepository userRepository;
    @Autowired ActivityRepository activityRepository;
    @Autowired ParticipantRepository participantRepository;
    @Autowired ParticipantService participantService;
    @Autowired ChatService chatService;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired SessionRepository<? extends Session> sessionRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void connectedParticipantStopsReceivingImmediatelyAfterLeave() throws Exception {
        User host = userRepository.save(UserFixture.persistableWithUniqueEmail("ws-host"));
        User participant = userRepository.save(UserFixture.persistableWithUniqueEmail("ws-participant"));
        Activity activity = activityRepository.save(ActivityFixture.newActivity(host, "cycling"));
        participantRepository.saveAll(List.of(
            ActivityParticipantFixture.participant(
                activity, host, ParticipantRole.CREATOR, ParticipantStatus.APPROVED),
            ActivityParticipantFixture.participant(
                activity, participant, ParticipantRole.PARTICIPANT, ParticipantStatus.APPROVED)
        ));

        String httpSessionId = createSession(participant);
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add(HttpHeaders.COOKIE, "SESSION=" + Base64.getEncoder().encodeToString(
            httpSessionId.getBytes(StandardCharsets.UTF_8)));
        handshakeHeaders.setOrigin("http://localhost:5173");

        StompSession stomp = client.connectAsync(
            "ws://localhost:" + port + "/ws",
            handshakeHeaders,
            new StompSessionHandlerAdapter() {}
        ).get(10, TimeUnit.SECONDS);

        AtomicInteger received = new AtomicInteger();
        CountDownLatch firstMessage = new CountDownLatch(1);
        stomp.subscribe(
            "/topic/activity/" + activity.getId(), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.incrementAndGet();
                firstMessage.countDown();
            }
        });
        Thread.sleep(250);

        assertThat(chatService.sendMessage(activity.getId(), "before leave", host)).isNotNull();
        assertThat(chatMessageRepository.count()).isEqualTo(1);
        assertThat(firstMessage.await(5, TimeUnit.SECONDS)).isTrue();
        int beforeLeaveCount = received.get();

        transactionTemplate.executeWithoutResult(status -> {
            chatService.sendMessage(activity.getId(), "rolled back", host);
            status.setRollbackOnly();
        });
        Thread.sleep(500);
        assertThat(received.get()).isEqualTo(beforeLeaveCount);

        participantService.leave(activity.getId(), participant);
        chatService.sendMessage(activity.getId(), "after leave", host);
        Thread.sleep(750);

        assertThat(received.get()).isEqualTo(beforeLeaveCount);
        stomp.disconnect();
        client.stop();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String createSession(User user) {
        SessionRepository repository = sessionRepository;
        Session session = (Session) repository.createSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
            new SessionPrincipal(user.getId()), null, List.of()));
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            context);
        repository.save(session);
        return session.getId();
    }
}
