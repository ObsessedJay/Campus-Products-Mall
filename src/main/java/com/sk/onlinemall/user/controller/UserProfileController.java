package com.sk.onlinemall.user.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.user.dto.UpdateUserProfileRequest;
import com.sk.onlinemall.user.model.UserProfileResponse;
import com.sk.onlinemall.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {
    private final UserProfileService userProfileService;

    /**
     * 创建 UserProfileController 实例。
     *
     * @param userProfileService 用户个人资料业务服务
     */
    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /**
     * 查询我的数据。
     *
     * @param authentication 当前登录身份
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<UserProfileResponse> findMine(Authentication authentication) {
        return ApiResponse.success(userProfileService.findMine(authentication.getName()));
    }

    /**
     * 更新我的数据。
     *
     * @param authentication 当前登录身份
     * @param request 请求参数
     * @return 方法执行结果
     */
    @PutMapping
    public ApiResponse<UserProfileResponse> updateMine(
            Authentication authentication,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        return ApiResponse.success(userProfileService.updateMine(authentication.getName(), request));
    }
}
