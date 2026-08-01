package ch.junto.websocket;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Tracks the HTTP session backing each STOMP connection for revocation checks. */
@Component
public class WebSocketSessionRegistry {

    private final SessionRepository<? extends Session> sessionRepository;
    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();

    public WebSocketSessionRegistry(SessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public void register(String webSocketSessionId, String httpSessionId, UUID userId) {
        if (webSocketSessionId == null || httpSessionId == null || userId == null) {
            throw new IllegalStateException("WebSocket requires an authenticated HTTP session");
        }
        connections.put(webSocketSessionId, new Connection(httpSessionId, userId));
    }

    public void removeWebSocketSession(String webSocketSessionId) {
        connections.remove(webSocketSessionId);
    }

    public void revokeHttpSession(String httpSessionId) {
        connections.entrySet().removeIf(entry -> entry.getValue().httpSessionId().equals(httpSessionId));
    }

    public boolean hasLiveHttpSession(String webSocketSessionId) {
        Connection connection = connections.get(webSocketSessionId);
        return connection != null && sessionRepository.findById(connection.httpSessionId()) != null;
    }

    public Optional<UUID> userId(String webSocketSessionId) {
        Connection connection = connections.get(webSocketSessionId);
        return connection == null ? Optional.empty() : Optional.of(connection.userId());
    }

    private record Connection(String httpSessionId, UUID userId) {}
}
