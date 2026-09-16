package com.sk.onlinemall.common.api;

public record ApiResponse<T>(boolean success, String code, String message, T data) {

    /**
     * 创建默认提示语的成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", "success", data);
    }

    /**
     * 创建自定义提示语的成功响应。
     *
     * @param message 成功提示语
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 统一成功响应
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "OK", message, data);
    }

    /**
     * 创建不包含业务数据的失败响应。
     *
     * @param code 稳定业务错误码
     * @param message 错误提示语
     * @param <T> 响应数据类型
     * @return 统一失败响应
     */
    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
