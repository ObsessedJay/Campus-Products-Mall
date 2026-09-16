package com.sk.onlinemall.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final FlashSaleStatusWebSocketHandler statusHandler;
    private final FlashSaleWebSocketAuthInterceptor authInterceptor;
    private final String[] allowedOriginPatterns;

    /**
     * 创建 WebSocketConfig 实例。
     *
     * @param statusHandler 状态Handler参数
     * @param authInterceptor WebSocket 握手鉴权拦截器
     * @param allowedOriginPatterns 允许连接的来源模式
     */
    public WebSocketConfig(
            FlashSaleStatusWebSocketHandler statusHandler,
            FlashSaleWebSocketAuthInterceptor authInterceptor,
            @Value("${app.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
            String allowedOriginPatterns) {
        this.statusHandler = statusHandler;
        this.authInterceptor = authInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns.split("\\s*,\\s*");
    }

    /**
     * 注册抢购状态 WebSocket 处理器和握手拦截器。
     *
     * @param registry WebSocket 处理器注册表
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(statusHandler, "/ws/flash-sale")
                .addInterceptors(authInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
