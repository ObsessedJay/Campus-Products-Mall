package com.sk.onlinemall.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk.onlinemall.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RequestGuardFilter extends OncePerRequestFilter {
    private final RequestGuardService requestGuardService;
    private final ObjectMapper objectMapper;

    /**
     * 创建请求风控过滤器。
     *
     * @param requestGuardService 请求风控服务
     * @param objectMapper JSON 序列化器
     */
    public RequestGuardFilter(RequestGuardService requestGuardService, ObjectMapper objectMapper) {
        this.requestGuardService = requestGuardService;
        this.objectMapper = objectMapper;
    }

    /**
     * 对需要保护的请求执行限流和黑名单校验。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException 过滤器链处理异常
     * @throws IOException 响应写入异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null || !authentication.isAuthenticated()
                ? null : authentication.getName();
        try {
            requestGuardService.checkRequest(request, username);
            filterChain.doFilter(request, response);
        } catch (RequestGuardException exception) {
            response.setStatus(exception.getStatus().value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiResponse.failure(exception.getCode(), exception.getMessage()));
        }
    }

    /**
     * 跳过不属于风控范围的只读业务请求。
     *
     * @param request HTTP 请求
     * @return 应跳过过滤器时返回 true
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !requestGuardService.isGuardedRequest(request);
    }
}
