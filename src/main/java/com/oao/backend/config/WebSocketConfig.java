package com.oao.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private final WebSocketSecurity security;
  private final String origins;

  public WebSocketConfig(
      WebSocketSecurity security,
      @org.springframework.beans.factory.annotation.Value("${oao.cors.allowed-origins}")
          String origins) {
    this.security = security;
    this.origins = origins;
  }

  @Override
  public void configureClientInboundChannel(
      org.springframework.messaging.simp.config.ChannelRegistration channel) {
    channel.interceptors(security.inbound());
  }

  @Override
  public void configureClientOutboundChannel(
      org.springframework.messaging.simp.config.ChannelRegistration channel) {
    channel.interceptors(security.outbound());
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/chat").setAllowedOrigins(origins.split(","));
  }
}
