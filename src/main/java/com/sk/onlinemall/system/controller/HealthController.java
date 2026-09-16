package com.sk.onlinemall.system.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class HealthController {

    /**
     * 返回应用健康状态。
     *
     * @return 方法执行结果
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("service", "campus-creative-flash-sale", "status", "UP"));
    }
}
