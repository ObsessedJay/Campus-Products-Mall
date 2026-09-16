package com.sk.onlinemall.realtime;

import com.sk.onlinemall.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class FlashSaleWebSocketAuthInterceptor implements HandshakeInterceptor {
    static final String USER_ID_ATTRIBUTE = "flashSaleUserId";
    private static final String AUTH_PROTOCOL_PREFIX = "bearer.";

    private final JwtTokenService tokenService;

    /**
     * 创建 FlashSaleWebSocketAuthInterceptor 实例。
     *
     * @param tokenService 令牌业务服务
     */
    public FlashSaleWebSocketAuthInterceptor(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * 在 WebSocket 握手前校验 JWT 和学生身份。
     *
     * @param request 请求参数
     * @param response 响应对象
     * @param wsHandler WebSocket 处理器
     * @param attributes WebSocket 握手属性
     * @return 是否满足业务条件
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = bearerProtocol(request.getHeaders().getFirst("Sec-WebSocket-Protocol"));
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = tokenService.parse(token);
            Number userId = claims.get("uid", Number.class);
            String role = claims.get("role", String.class);
            if (userId == null || !"STUDENT".equals(role)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(USER_ID_ATTRIBUTE, userId.longValue());
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    /**
     * 处理 WebSocket 握手完成事件。
     *
     * @param request 请求参数
     * @param response 响应对象
     * @param wsHandler WebSocket 处理器
     * @param exception 捕获的异常
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    /**
     * 从 WebSocket 子协议中提取 Bearer 令牌。
     *
     * @param protocols WebSocket 子协议列表
     * @return 方法执行结果
     */
    private String bearerProtocol(String protocols) {
        if (protocols == null) return null;
        for (String protocol : protocols.split(",")) {
            String value = protocol.trim();
            if (value.startsWith(AUTH_PROTOCOL_PREFIX) && value.length() > AUTH_PROTOCOL_PREFIX.length()) {
                return value.substring(AUTH_PROTOCOL_PREFIX.length());
            }
        }
        return null;
    }
}
