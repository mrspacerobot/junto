package ch.junto.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketSessionListener {

  private static final Logger log = LoggerFactory.getLogger(WebSocketSessionListener.class);

  private final WebSocketSessionRegistry sessionRegistry;

  public WebSocketSessionListener(WebSocketSessionRegistry sessionRegistry) {
    this.sessionRegistry = sessionRegistry;
  }

  @EventListener
  public void onWebSocketDisconnect(SessionDisconnectEvent event) {
    sessionRegistry.removeWebSocketSession(event.getSessionId());
    log.debug("WebSocket session disconnected: {}", event.getSessionId());
  }

  @EventListener
  public void onHttpSessionDestroyed(SessionDestroyedEvent event) {
    sessionRegistry.revokeHttpSession(event.getSessionId());
    log.debug("Revoked WebSockets for HTTP session: {}", event.getSessionId());
  }
}
