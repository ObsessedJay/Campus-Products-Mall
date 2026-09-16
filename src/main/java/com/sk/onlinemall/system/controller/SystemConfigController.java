package com.sk.onlinemall.system.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.system.dto.UpdateSystemConfigRequest;
import com.sk.onlinemall.system.model.SystemConfigItem;
import com.sk.onlinemall.system.model.SystemConfigLog;
import com.sk.onlinemall.system.service.SystemConfigService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/system-configs")
public class SystemConfigController {
    private final SystemConfigService configService;

    /**
     * 创建系统配置控制器。
     *
     * @param configService 系统配置服务
     */
    public SystemConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    /**
     * 查询全部系统配置。
     *
     * @return 配置列表
     */
    @GetMapping
    public ApiResponse<List<SystemConfigItem>> list() {
        return ApiResponse.success(configService.findAll());
    }

    /**
     * 更新指定配置。
     *
     * @param key 配置键
     * @param request 更新请求
     * @param authentication 当前身份
     * @return 更新后的配置
     */
    @PutMapping("/{key}")
    public ApiResponse<SystemConfigItem> update(@PathVariable String key,
                                                 @Valid @RequestBody UpdateSystemConfigRequest request,
                                                 Authentication authentication) {
        return ApiResponse.success(configService.update(
                key, request.value(), request.reason(), authentication.getName()));
    }

    /**
     * 查询配置变更日志。
     *
     * @param key 可选配置键
     * @return 变更记录
     */
    @GetMapping("/logs")
    public ApiResponse<List<SystemConfigLog>> logs(@RequestParam(required = false) String key) {
        return ApiResponse.success(configService.findLogs(key));
    }
}
