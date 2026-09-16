package com.sk.onlinemall.user.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.common.api.PageResponse;
import com.sk.onlinemall.user.dto.AdjustCreditRequest;
import com.sk.onlinemall.user.dto.ChangeUserStatusRequest;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserGovernanceDetail;
import com.sk.onlinemall.user.model.UserStatus;
import com.sk.onlinemall.user.service.UserGovernanceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserGovernanceController {
    private final UserGovernanceService governanceService;

    /**
     * 创建用户治理控制器。
     *
     * @param governanceService 用户治理服务
     */
    public UserGovernanceController(UserGovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * 分页搜索用户。
     *
     * @param keyword 可选关键词
     * @param status 可选状态
     * @param page 页码
     * @param size 每页数量
     * @return 用户分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<UserEntity>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(governanceService.search(keyword, status, page, size));
    }

    /**
     * 查询用户治理详情。
     *
     * @param userId 用户主键
     * @return 治理详情
     */
    @GetMapping("/{userId}")
    public ApiResponse<UserGovernanceDetail> detail(@PathVariable Long userId) {
        return ApiResponse.success(governanceService.detail(userId));
    }

    /**
     * 禁用用户账号。
     *
     * @param userId 用户主键
     * @param request 操作原因
     * @param authentication 当前身份
     * @return 更新后的用户
     */
    @PostMapping("/{userId}/disable")
    public ApiResponse<UserEntity> disable(@PathVariable Long userId,
                                            @Valid @RequestBody ChangeUserStatusRequest request,
                                            Authentication authentication) {
        return ApiResponse.success(governanceService.changeStatus(
                userId, UserStatus.DISABLED, request.reason(), authentication.getName()));
    }

    /**
     * 启用用户账号。
     *
     * @param userId 用户主键
     * @param request 操作原因
     * @param authentication 当前身份
     * @return 更新后的用户
     */
    @PostMapping("/{userId}/enable")
    public ApiResponse<UserEntity> enable(@PathVariable Long userId,
                                           @Valid @RequestBody ChangeUserStatusRequest request,
                                           Authentication authentication) {
        return ApiResponse.success(governanceService.changeStatus(
                userId, UserStatus.ACTIVE, request.reason(), authentication.getName()));
    }

    /**
     * 调整用户信用分。
     *
     * @param userId 用户主键
     * @param request 信用分请求
     * @param authentication 当前身份
     * @return 更新后的用户
     */
    @PostMapping("/{userId}/credit")
    public ApiResponse<UserEntity> adjustCredit(@PathVariable Long userId,
                                                 @Valid @RequestBody AdjustCreditRequest request,
                                                 Authentication authentication) {
        return ApiResponse.success(governanceService.adjustCredit(
                userId, request.score(), request.reason(), authentication.getName()));
    }
}
