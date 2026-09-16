package com.sk.onlinemall.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;

    /**
     * 创建包含稳定错误码的业务异常。
     *
     * @param code 业务错误码
     * @param message 错误信息
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
