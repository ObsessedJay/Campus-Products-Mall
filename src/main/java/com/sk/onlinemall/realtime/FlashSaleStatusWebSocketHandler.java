package com.sk.onlinemall.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.onlinemall.common.util.TransactionCallbackUtil;
import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import com.sk.onlinemall.order.model.TradeOrderEntity;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FlashSaleStatusWebSocketHandler extends TextWebSocketHandler implements SubProtocolCapable {
    private static final Logger log = LoggerFactory.getLogger(FlashSaleStatusWebSocketHandler.class);
    private static final List<String> SUB_PROTOCOLS = List.of("campus-flash-sale");

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /**
     * 创建 FlashSaleStatusWebSocketHandler 实例。
     *
     * @param objectMapper JSON 序列化组件
     */
    public FlashSaleStatusWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取服务端支持的 WebSocket 子协议。
     *
     * @return 查询结果
     */
    @Override
    public List<String> getSubProtocols() {
        return SUB_PROTOCOLS;
    }

    /**
     * 登记已通过认证的 WebSocket 会话。
     *
     * @param session WebSocket 会话
     * @throws Exception 处理失败时抛出
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userId(session);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("authenticated student is required"));
            return;
        }
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024);
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(safeSession);
        send(safeSession, FlashSaleStatusEvent.ready());
    }

    /**
     * 移除已经关闭的 WebSocket 会话。
     *
     * @param session WebSocket 会话
     * @param status 业务状态或连接关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session.getId(), userId(session));
    }

    /**
     * 在传输异常时关闭并清理 WebSocket 会话。
     *
     * @param session WebSocket 会话
     * @param exception 捕获的异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        remove(session.getId(), userId(session));
        try {
            if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException closeException) {
            log.debug("Failed to close flash-sale WebSocket session {}", session.getId(), closeException);
        }
    }

    /**
     * 向抢购请求所属学生推送最新状态。
     *
     * @param request 请求参数
     */
    public void publish(FlashSaleRequestEntity request) {
        if (request == null || request.getUserId() == null) return;
        Set<WebSocketSession> sessions = sessionsByUser.get(request.getUserId());
        if (sessions == null || sessions.isEmpty()) return;
        publishToSessions(sessions, FlashSaleStatusEvent.from(request), request.getRequestNo());
    }

    /**
     * 在事务提交后发布订单状态。
     *
     * @param order 订单信息
     */
    public void publishOrderAfterCommit(TradeOrderEntity order) {
        if (order == null || order.getUserId() == null) return;
        Long userId = order.getUserId();
        String payload = serialize(OrderStatusEvent.from(order), order.getOrderNo());
        if (payload == null) return;
        TransactionCallbackUtil.afterCommit(() -> {
            Set<WebSocketSession> sessions = sessionsByUser.get(userId);
            if (sessions != null) publishPayload(sessions, payload);
        });
    }

    /**
     * 在事务提交后发布活动状态。
     *
     * @param activity 活动信息
     */
    public void publishActivityAfterCommit(FlashActivityEntity activity) {
        if (activity == null) return;
        String payload = serialize(ActivityStatusEvent.from(activity), String.valueOf(activity.getId()));
        if (payload == null) return;
        TransactionCallbackUtil.afterCommit(() -> publishPayload(allSessions(), payload));
    }

    /**
     * 获取全部有效 WebSocket 会话。
     *
     * @return 方法执行结果
     */
    private Set<WebSocketSession> allSessions() {
        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
        sessionsByUser.values().forEach(sessions::addAll);
        return sessions;
    }

    /**
     * 向指定 WebSocket 会话集合推送事件。
     *
     * @param sessions 目标 WebSocket 会话集合
     * @param event 实时状态事件
     * @param reference 日志关联标识
     */
    private void publishToSessions(Set<WebSocketSession> sessions, Object event, String reference) {
        if (sessions == null || sessions.isEmpty()) return;
        String payload = serialize(event, reference);
        if (payload == null) return;
        publishPayload(sessions, payload);
    }

    /**
     * 将实时事件序列化为 JSON 文本。
     *
     * @param event 实时状态事件
     * @param reference 日志关联标识
     * @return 方法执行结果
     */
    private String serialize(Object event, String reference) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize realtime status event for {}", reference, exception);
            return null;
        }
    }

    /**
     * 向单个 WebSocket 会话发送事件载荷。
     *
     * @param sessions 目标 WebSocket 会话集合
     * @param payload 待推送的事件载荷
     */
    private void publishPayload(Set<WebSocketSession> sessions, String payload) {
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                removeFromAll(session.getId());
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException | IllegalStateException exception) {
                removeFromAll(session.getId());
                log.debug("Failed to send flash-sale status to session {}", session.getId(), exception);
            }
        }
    }

    /**
     * 向 WebSocket 会话发送状态事件。
     *
     * @param session WebSocket 会话
     * @param event 实时状态事件
     * @throws IOException 处理失败时抛出
     */
    private void send(WebSocketSession session, FlashSaleStatusEvent event) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
    }

    /**
     * 获取 WebSocket 会话所属用户主键。
     *
     * @param session WebSocket 会话
     * @return 方法执行结果
     */
    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(FlashSaleWebSocketAuthInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 从指定用户的连接集合中移除会话。
     *
     * @param sessionId WebSocket 会话标识
     * @param userId 用户主键
     */
    private void remove(String sessionId, Long userId) {
        if (userId == null) return;
        sessionsByUser.computeIfPresent(userId, (ignored, sessions) -> {
            sessions.removeIf(session -> session.getId().equals(sessionId));
            return sessions.isEmpty() ? null : sessions;
        });
    }

    /**
     * 移除来源于全部。
     *
     * @param sessionId WebSocket 会话标识
     */
    private void removeFromAll(String sessionId) {
        sessionsByUser.keySet().forEach(userId -> remove(sessionId, userId));
    }
}
