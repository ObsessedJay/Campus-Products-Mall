package com.sk.onlinemall.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.onlinemall.activity.model.ActivityStatus;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import com.sk.onlinemall.order.model.FlashSaleRequestStatus;
import com.sk.onlinemall.order.model.OrderStatus;
import com.sk.onlinemall.order.model.TradeOrderEntity;
import com.sk.onlinemall.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlashSaleRealtimeTests {
    private final JwtTokenService tokenService = new JwtTokenService(
            "realtime-test-secret-key-with-at-least-32-bytes", 3600);
    private final FlashSaleWebSocketAuthInterceptor interceptor =
            new FlashSaleWebSocketAuthInterceptor(tokenService);

    /**
     * 验证可从 WebSocket 子协议中恢复学生身份。
     */
    @Test
    void shouldAuthenticateStudentFromWebSocketSubProtocol() {
        String token = tokenService.issue(801L, "realtime-student", "STUDENT");
        ServerHttpRequest request = request("campus-flash-sale, bearer." + token);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, mock(org.springframework.web.socket.WebSocketHandler.class), attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(FlashSaleWebSocketAuthInterceptor.USER_ID_ATTRIBUTE, 801L);
    }

    /**
     * 验证 WebSocket 握手拒绝无效令牌和非学生角色。
     */
    @Test
    void shouldRejectInvalidTokenAndNonStudentRole() {
        ServerHttpResponse invalidResponse = mock(ServerHttpResponse.class);
        boolean invalidAccepted = interceptor.beforeHandshake(
                request("campus-flash-sale, bearer.invalid"),
                invalidResponse, mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>());

        String operatorToken = tokenService.issue(802L, "realtime-operator", "OPERATOR");
        ServerHttpResponse operatorResponse = mock(ServerHttpResponse.class);
        boolean operatorAccepted = interceptor.beforeHandshake(
                request("campus-flash-sale, bearer." + operatorToken),
                operatorResponse, mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>());

        assertThat(invalidAccepted).isFalse();
        verify(invalidResponse).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(operatorAccepted).isFalse();
        verify(operatorResponse).setStatusCode(HttpStatus.FORBIDDEN);
    }

    /**
     * 验证抢购状态仅推送给所属学生。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldPublishStatusOnlyToOwningStudent() throws Exception {
        FlashSaleStatusWebSocketHandler handler = new FlashSaleStatusWebSocketHandler(
                new ObjectMapper().findAndRegisterModules());
        WebSocketSession owner = session("owner-session", 811L);
        WebSocketSession other = session("other-session", 812L);
        handler.afterConnectionEstablished(owner);
        handler.afterConnectionEstablished(other);

        FlashSaleRequestEntity request = new FlashSaleRequestEntity();
        request.setRequestNo("realtime-request");
        request.setUserId(811L);
        request.setStatus(FlashSaleRequestStatus.SUCCEEDED);
        request.setOrderId(901L);
        handler.publish(request);

        ArgumentCaptor<TextMessage> ownerMessages = ArgumentCaptor.forClass(TextMessage.class);
        verify(owner, times(2)).sendMessage(ownerMessages.capture());
        verify(other, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
        assertThat(ownerMessages.getAllValues().get(1).getPayload())
                .contains("realtime-request", "SUCCEEDED", "901");
    }

    /**
     * 验证订单状态仅在事务提交后推送给订单用户。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldPublishOrderToOwnerOnlyAfterTransactionCommit() throws Exception {
        FlashSaleStatusWebSocketHandler handler = handler();
        WebSocketSession owner = session("order-owner-session", 821L);
        WebSocketSession other = session("order-other-session", 822L);
        handler.afterConnectionEstablished(owner);
        handler.afterConnectionEstablished(other);

        TradeOrderEntity order = new TradeOrderEntity();
        order.setId(931L);
        order.setOrderNo("ORDER-REALTIME-931");
        order.setUserId(821L);
        order.setStatus(OrderStatus.PAID);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            handler.publishOrderAfterCommit(order);
            order.setStatus(OrderStatus.CANCELLED);
            verify(owner, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(owner, times(2)).sendMessage(messages.capture());
        verify(other, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
        assertThat(messages.getAllValues().get(1).getPayload())
                .contains("ORDER_STATUS", "ORDER-REALTIME-931", "PAID");
        assertThat(messages.getAllValues().get(1).getPayload()).doesNotContain("CANCELLED");
    }

    /**
     * 验证活动状态会广播给已连接学生。
     *
     * @throws Exception 测试执行异常时抛出
     */
    @Test
    void shouldBroadcastActivityStatusToConnectedStudents() throws Exception {
        FlashSaleStatusWebSocketHandler handler = handler();
        WebSocketSession first = session("activity-first-session", 831L);
        WebSocketSession second = session("activity-second-session", 832L);
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);

        FlashActivityEntity activity = new FlashActivityEntity();
        activity.setId(941L);
        activity.setName("Realtime activity");
        activity.setStatus(ActivityStatus.RUNNING);
        handler.publishActivityAfterCommit(activity);

        ArgumentCaptor<TextMessage> firstMessages = ArgumentCaptor.forClass(TextMessage.class);
        ArgumentCaptor<TextMessage> secondMessages = ArgumentCaptor.forClass(TextMessage.class);
        verify(first, times(2)).sendMessage(firstMessages.capture());
        verify(second, times(2)).sendMessage(secondMessages.capture());
        assertThat(firstMessages.getAllValues().get(1).getPayload())
                .contains("ACTIVITY_STATUS", "Realtime activity", "RUNNING");
        assertThat(secondMessages.getAllValues().get(1).getPayload())
                .contains("ACTIVITY_STATUS", "941");
    }

    /**
     * 创建测试使用的 WebSocket 状态处理器。
     *
     * @return 方法执行结果
     */
    private FlashSaleStatusWebSocketHandler handler() {
        return new FlashSaleStatusWebSocketHandler(new ObjectMapper().findAndRegisterModules());
    }

    /**
     * 创建测试使用的 WebSocket 会话。
     *
     * @param id 记录主键
     * @param userId 用户主键
     * @return 方法执行结果
     */
    private WebSocketSession session(String id, long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(
                FlashSaleWebSocketAuthInterceptor.USER_ID_ATTRIBUTE, userId));
        return session;
    }

    /**
     * 创建携带指定 WebSocket 子协议的握手请求。
     *
     * @param protocols WebSocket 子协议列表
     * @return 处理后的业务数据
     */
    private ServerHttpRequest request(String protocols) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Sec-WebSocket-Protocol", protocols);
        when(request.getHeaders()).thenReturn(headers);
        return request;
    }
}
