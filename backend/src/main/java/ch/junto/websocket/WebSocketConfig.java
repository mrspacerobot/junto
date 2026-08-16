package ch.junto.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final WebSocketAuthInterceptor authInterceptor;
  private final WebSocketOutboundAuthInterceptor outboundAuthInterceptor;
  private final String allowedOrigin;

  public WebSocketConfig(
      WebSocketAuthInterceptor authInterceptor,
      WebSocketOutboundAuthInterceptor outboundAuthInterceptor,
      @Value("${app.allowed-origin}") String allowedOrigin) {
    this.authInterceptor = authInterceptor;
    this.outboundAuthInterceptor = outboundAuthInterceptor;
    this.allowedOrigin = allowedOrigin;
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        .setAllowedOrigins(allowedOrigin)
        .addInterceptors(new HttpSessionHandshakeInterceptor());
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authInterceptor);
  }

  @Override
  public void configureClientOutboundChannel(ChannelRegistration registration) {
    registration.interceptors(outboundAuthInterceptor);
  }
}
