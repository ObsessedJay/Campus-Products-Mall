package com.sk.onlinemall.security;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RequestGuardException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    /**
     * 创建请求风控异常。
     *
     * @param status HTTP 状态
     * @param code 稳定业务错误码
     * @param message 面向客户端的错误信息
     */
    public RequestGuardException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
