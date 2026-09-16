package com.sk.onlinemall.user.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.user.dto.CreateMerchantAccountRequest;
import com.sk.onlinemall.user.mapper.AccountOperationLogMapper;
import com.sk.onlinemall.user.mapper.MerchantProfileMapper;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.MerchantAccountResponse;
import com.sk.onlinemall.user.model.MerchantProfileEntity;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantAccountAdminService {
    private static final String CREATE_MERCHANT_ACTION = "CREATE_MERCHANT_ACCOUNT";
    private final UserMapper userMapper;
    private final MerchantProfileMapper merchantProfileMapper;
    private final AccountOperationLogMapper operationLogMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建商家账号管理服务。
     *
     * @param userMapper 账号数据访问组件
     * @param merchantProfileMapper 商家资料数据访问组件
     * @param operationLogMapper 账号操作日志数据访问组件
     * @param passwordEncoder 密码编码器
     */
    public MerchantAccountAdminService(UserMapper userMapper,
                                       MerchantProfileMapper merchantProfileMapper,
                                       AccountOperationLogMapper operationLogMapper,
                                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.merchantProfileMapper = merchantProfileMapper;
        this.operationLogMapper = operationLogMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 由管理员创建商家账号、资料和审计记录。
     *
     * @param administratorUsername 当前管理员内部标识
     * @param request 商家账号创建请求
     * @return 已创建的商家账号
     */
    @Transactional
    public MerchantAccountResponse create(String administratorUsername, CreateMerchantAccountRequest request) {
        UserEntity administrator = requireAdministrator(administratorUsername);
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException("PASSWORD_CONFIRMATION_MISMATCH", "passwords do not match");
        }

        String email = TextUtil.normalizeLowercase(request.email());
        if (userMapper.findByEmailAndRole(email, UserRole.MERCHANT) != null
                || userMapper.findByEmailAndRole(email, UserRole.OPERATOR) != null) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }

        UserEntity account = new UserEntity();
        account.setUsername(AccountIdentity.fromEmailAndRole(email, UserRole.MERCHANT));
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setNickname(request.displayName().trim());
        account.setRole(UserRole.MERCHANT);
        account.setStatus(UserStatus.ACTIVE);
        try {
            userMapper.insert(account);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("EMAIL_EXISTS", "email is already registered");
        }

        MerchantProfileEntity profile = new MerchantProfileEntity();
        profile.setAccountId(account.getId());
        profile.setMerchantName(request.merchantName().trim());
        profile.setContactName(TextUtil.trimToNull(request.contactName()));
        profile.setContactPhone(TextUtil.trimToNull(request.contactPhone()));
        merchantProfileMapper.insert(profile);
        operationLogMapper.insert(administrator.getId(), account.getId(), CREATE_MERCHANT_ACTION,
                "created merchant account for " + email);

        UserEntity savedAccount = userMapper.findById(account.getId());
        MerchantProfileEntity savedProfile = merchantProfileMapper.findByAccountId(account.getId());
        return toResponse(savedAccount, savedProfile);
    }

    /**
     * 查询并校验当前管理员账号。
     *
     * @param username 当前账号内部标识
     * @return 有效管理员账号
     */
    private UserEntity requireAdministrator(String username) {
        UserEntity administrator = userMapper.findByUsername(username);
        if (administrator == null || administrator.getStatus() != UserStatus.ACTIVE
                || administrator.getRole() != UserRole.ADMIN) {
            throw new BusinessException("FORBIDDEN", "administrator account is required");
        }
        return administrator;
    }

    /**
     * 组装商家账号响应。
     *
     * @param account 账号信息
     * @param profile 商家资料
     * @return 商家账号响应
     */
    private MerchantAccountResponse toResponse(UserEntity account, MerchantProfileEntity profile) {
        return new MerchantAccountResponse(
                account.getId(), account.getEmail(), account.getNickname(), account.getRole().name(),
                account.getStatus().name(), profile.getMerchantName(), profile.getContactName(),
                profile.getContactPhone(), account.getCreatedAt());
    }
}
