package com.sk.onlinemall.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    /**
     * 创建 BCrypt 密码编码器。
     *
     * @return 方法执行结果
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置无状态接口鉴权、角色权限和请求风控过滤器。
     *
     * @param http Spring Security 配置入口
     * @param jwtAuthenticationFilter JWT 鉴权过滤器
     * @param requestGuardFilter 请求风控过滤器
     * @return 安全过滤器链
     * @throws Exception 安全配置构建失败时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RequestGuardFilter requestGuardFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/merchants/register",
                                "/api/v1/auth/merchants/email-codes",
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset",
                                "/api/v1/auth/password-reset/email-code",
                                "/api/v1/auth/captcha",
                                "/api/v1/auth/email-codes",
                                "/api/v1/system/health",
                                "/ws/**",
                        "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/files/images/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/files/images")
                            .hasAnyRole("STUDENT", "MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/files/images/**").hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/*/reservation").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities/*/lottery")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/activities", "/api/v1/activities/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/favorites").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/verification")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/refund/approve",
                                "/api/v1/orders/*/refund/reject")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/activities/**")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/flash-sale/**")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/orders/**")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/products/**")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/categories/**")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/*/favorite")
                            .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*/favorite")
                            .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/categories/**")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/reviews/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/reports/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/reports").hasRole("STUDENT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/reviews").hasRole("STUDENT")
                        .requestMatchers("/api/v1/admin/operators/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/accounts/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/system-configs/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/dashboard/**").hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/v1/admin/search-index/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/activities/*/reservations").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/activities", "/api/v1/activities/*/publish",
                                "/api/v1/activities/*/terminate", "/api/v1/activities/*/lottery")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/products/**")
                            .hasAnyRole("MERCHANT", "OPERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestGuardFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
