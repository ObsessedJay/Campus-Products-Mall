package com.sk.onlinemall.user.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.user.dto.CreateMerchantAccountRequest;
import com.sk.onlinemall.user.model.MerchantAccountResponse;
import com.sk.onlinemall.user.service.MerchantAccountAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accounts/merchants")
public class MerchantAccountAdminController {
    private final MerchantAccountAdminService merchantAccountAdminService;

    /**
     * 创建商家账号管理控制器。
     *
     * @param merchantAccountAdminService 商家账号管理服务
     */
    public MerchantAccountAdminController(MerchantAccountAdminService merchantAccountAdminService) {
        this.merchantAccountAdminService = merchantAccountAdminService;
    }

    /**
     * 创建商家端登录账号。
     *
     * @param authentication 当前管理员身份
     * @param request 商家账号创建请求
     * @return 已创建的商家账号
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MerchantAccountResponse>> create(
            Authentication authentication,
            @Valid @RequestBody CreateMerchantAccountRequest request) {
        MerchantAccountResponse response = merchantAccountAdminService.create(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
