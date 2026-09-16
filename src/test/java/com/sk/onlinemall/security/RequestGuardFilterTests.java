package com.sk.onlinemall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestGuardFilterTests {

    /**
     * 验证限流异常会转换为统一 JSON 响应且不会继续业务过滤链。
     *
     * @throws Exception 过滤器执行失败时抛出
     */
    @Test
    void shouldReturnUnifiedRateLimitResponse() throws Exception {
        RequestGuardService service = mock(RequestGuardService.class);
        RequestGuardFilter filter = new RequestGuardFilter(service, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(service.isGuardedRequest(request)).thenReturn(true);
        doThrow(new RequestGuardException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "too many requests"))
                .when(service).checkRequest(request, null);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMITED", "too many requests");
        verify(chain, never()).doFilter(request, response);
    }
}
