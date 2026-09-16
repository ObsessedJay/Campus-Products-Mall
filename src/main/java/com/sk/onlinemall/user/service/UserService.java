package com.sk.onlinemall.user.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.common.util.TransactionCallbackUtil;
import com.sk.onlinemall.security.CaptchaService;
import com.sk.onlinemall.security.EmailVerificationService;
import com.sk.onlinemall.security.JwtTokenService;
import com.sk.onlinemall.security.RefreshTokenService;
import com.sk.onlinemall.storage.ImageStorageService;
import com.sk.onlinemall.storage.UploadedImageResponse;
import com.sk.onlinemall.user.dto.AuthTokenResponse;
import com.sk.onlinemall.user.dto.LoginRequest;
import com.sk.onlinemall.user.dto.RegisterRequest;
import com.sk.onlinemall.user.dto.ResetPasswordRequest;
import com.sk.onlinemall.user.dto.MerchantRegisterRequest;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.mapper.MerchantProfileMapper;
import com.sk.onlinemall.user.model.AuthSession;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import com.sk.onlinemall.user.model.MerchantProfileEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
    private static final String UNIVERSITY_NAME = "成都信息工程大学";
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final CaptchaService captchaService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final MerchantProfileMapper merchantProfileMapper;
    private final ImageStorageService imageStorageService;

    /**
     * 创建用户注册与登录服务。
     *
     * @param userMapper 用户数据访问器
     * @param passwordEncoder 密码编码器
     * @param tokenService JWT 服务
     * @param captchaService 图形验证码服务
     * @param emailVerificationService 邮箱验证码服务
     * @param refreshTokenService 刷新令牌服务
     * @param merchantProfileMapper 商家资料数据访问组件
     * @param imageStorageService 图片存储服务
     */
    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenService tokenService,
                       CaptchaService captchaService, EmailVerificationService emailVerificationService,
                       RefreshTokenService refreshTokenService, MerchantProfileMapper merchantProfileMapper,
                       ImageStorageService imageStorageService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
        this.emailVerificationService = emailVerificationService;
        this.refreshTokenService = refreshTokenService;
        this.merchantProfileMapper = merchantProfileMapper;
        this.imageStorageService = imageStorageService;
    }

    /**
     * 校验邮箱验证码并注册新的学生账号。
     *
     * @param request 注册请求
     * @return 新账号的认证信息
     */
    @Transactional
    public AuthSession register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException("PASSWORD_CONFIRMATION_MISMATCH", "passwords do not match");
        }
        String email = TextUtil.normalizeLowercase(request.email());
        if (userMapper.findByEmailAndRole(email, UserRole.STUDENT) != null) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }
        emailVerificationService.verifyRegistrationCode(email, request.emailCode());
        UserEntity user = new UserEntity();
        user.setUsername(AccountIdentity.fromEmailAndRole(email, UserRole.STUDENT));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setSchool(UNIVERSITY_NAME);
        user.setRole(UserRole.STUDENT);
        user.setStatus(UserStatus.ACTIVE);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }
        return toAuthSession(user);
    }

    /**
     * 校验邮箱验证码并注册商家账号及资料。
     *
     * @param request 商家注册请求
     * @return 新商家账号的认证信息
     */
    @Transactional
    public AuthSession registerMerchant(MerchantRegisterRequest request) {
        return registerMerchant(request, null);
    }

    /**
     * 校验邮箱验证码并注册带商家头像的账号及资料。
     *
     * @param request 商家注册请求
     * @param logoFile 可选商家头像或门店标识图
     * @return 新商家账号的认证信息
     */
    @Transactional
    public AuthSession registerMerchant(MerchantRegisterRequest request, MultipartFile logoFile) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException("PASSWORD_CONFIRMATION_MISMATCH", "passwords do not match");
        }
        String email = TextUtil.normalizeLowercase(request.email());
        if (findPortalAccount(email, "MERCHANT") != null) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }
        emailVerificationService.verifyMerchantRegistrationCode(email, request.emailCode());
        UserEntity user = new UserEntity();
        user.setUsername(AccountIdentity.fromEmailAndRole(email, UserRole.MERCHANT));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.displayName() == null ? "" : request.displayName().trim());
        user.setRole(UserRole.MERCHANT);
        user.setStatus(UserStatus.ACTIVE);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }
        MerchantProfileEntity profile = new MerchantProfileEntity();
        profile.setAccountId(user.getId());
        profile.setMerchantName(request.merchantName().trim());
        if (logoFile != null && !logoFile.isEmpty()) {
            UploadedImageResponse logo = imageStorageService.upload(logoFile);
            profile.setLogoUrl(logo.url());
            deleteUploadedLogoAfterRollback(logo.objectName());
        }
        merchantProfileMapper.insert(profile);
        return toAuthSession(user);
    }

    /**
     * 注册事务回滚后删除已上传但未持久化引用的商家图片。
     *
     * @param objectName 图片对象名称
     */
    private void deleteUploadedLogoAfterRollback(String objectName) {
        TransactionCallbackUtil.afterCompletionUnlessCommitted(() -> imageStorageService.delete(objectName));
    }

    /**
     * 校验图形验证码和账号密码并签发 JWT。
     *
     * @param request 登录请求
     * @return 登录账号的认证信息
     */
    public AuthSession login(LoginRequest request) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        String email = TextUtil.normalizeLowercase(request.email());
        UserEntity user = findPortalAccount(email, request.portalRole());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "email or password is incorrect");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("USER_DISABLED", "user account is disabled");
        }
        return toAuthSession(user);
    }

    /**
     * 消费并轮换刷新令牌，为有效账号签发新会话。
     *
     * @param refreshToken 原始刷新令牌
     * @return 轮换后的认证会话
     */
    public AuthSession refresh(String refreshToken) {
        long userId = refreshTokenService.consume(refreshToken);
        UserEntity user = userMapper.findById(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("USER_DISABLED", "user account is unavailable");
        }
        return toAuthSession(user);
    }

    /**
     * 撤销当前浏览器持有的刷新会话。
     *
     * @param refreshToken 原始刷新令牌
     */
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * 校验邮箱验证码后更新密码并撤销全部刷新会话。
     *
     * @param request 重置密码请求
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = TextUtil.normalizeLowercase(request.email());
        emailVerificationService.verifyPasswordResetCode(email, request.portalRole(), request.emailCode());
        UserEntity user = findPortalAccount(email, request.portalRole());
        if (user == null) {
            throw new BusinessException("EMAIL_CODE_INVALID", "email verification code is incorrect");
        }
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(user.getId());
    }

    /**
     * 按登录入口查询对应账号，商家入口兼容旧版运营角色。
     *
     * @param email 规范化邮箱地址
     * @param portalRole 登录入口角色
     * @return 对应入口账号，不存在时返回空
     */
    private UserEntity findPortalAccount(String email, String portalRole) {
        if ("MERCHANT".equals(portalRole)) {
            UserEntity merchant = userMapper.findByEmailAndRole(email, UserRole.MERCHANT);
            return merchant != null ? merchant : userMapper.findByEmailAndRole(email, UserRole.OPERATOR);
        }
        if ("ADMIN".equals(portalRole)) {
            return userMapper.findByEmailAndRole(email, UserRole.ADMIN);
        }
        return userMapper.findByEmailAndRole(email, UserRole.STUDENT);
    }

    /**
     * 将用户实体转换为包含新 JWT 的认证响应。
     *
     * @param user 用户实体
     * @return 认证响应
     */
    private AuthSession toAuthSession(UserEntity user) {
        String token = tokenService.issue(user.getId(), user.getUsername(), user.getRole().name());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user.getId());
        AuthTokenResponse response = new AuthTokenResponse(
                token, user.getId(), user.getEmail(), user.getNickname(), user.getRole().name());
        return new AuthSession(response, refreshToken.token(), refreshToken.expiresInSeconds());
    }

}
