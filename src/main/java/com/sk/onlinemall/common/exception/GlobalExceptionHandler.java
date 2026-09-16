package com.sk.onlinemall.common.exception;

import com.sk.onlinemall.common.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 将业务异常转换为统一错误响应。
     *
     * @param exception 业务异常
     * @return 与业务错误语义对应的 HTTP 错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case "REFRESH_TOKEN_INVALID" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN", "PICKUP_POINT_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /**
     * 将请求风控异常转换为对应 HTTP 状态的统一响应。
     *
     * @param exception 请求风控异常
     * @return 限流或黑名单错误响应
     */
    @ExceptionHandler(com.sk.onlinemall.security.RequestGuardException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestGuard(
            com.sk.onlinemall.security.RequestGuardException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    /**
     * 将参数校验异常转换为包含首个字段错误的统一响应。
     *
     * @param exception 参数校验异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("request validation failed");
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", message));
    }

    /**
     * 记录未处理异常并隐藏服务端内部细节。
     *
     * @param exception 未处理异常
     * @return HTTP 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "internal server error"));
    }
}
