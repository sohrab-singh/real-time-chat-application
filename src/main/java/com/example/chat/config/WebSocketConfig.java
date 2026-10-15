package com.example.chat.config;

import com.example.chat.ws.ChatWebSocketHandler;
import com.example.chat.ws.SessionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SessionRegistry registry;

    public WebSocketConfig(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry handlers) {
        // No auth in this app, so an origin check would protect nothing; allow any origin (e.g. Codespaces forwarding)
        handlers.addHandler(new ChatWebSocketHandler(registry), "/ws").setAllowedOriginPatterns("*");
    }
}
