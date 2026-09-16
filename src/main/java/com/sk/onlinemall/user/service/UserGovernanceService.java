package com.sk.onlinemall.user.service;

import com.sk.onlinemall.common.api.PageResponse;
import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.common.util.TextUtil;
import com.sk.onlinemall.user.mapper.AccountOperationLogMapper;
import com.sk.onlinemall.user.mapper.UserGovernanceMapper;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserGovernanceDetail;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserGovernanceService {
    private final UserMapper userMapper;
    private final UserGovernanceMapper governanceMapper;
    private final AccountOperationLogMapper operationLogMapper;

    /**
     * 创建用户治理服务。
     *
     * @param userMapper 用户数据访问组件
     * @param governanceMapper 治理数据访问组件
     * @param operationLogMapper 操作日志组件
     */
    public UserGovernanceService(UserMapper userMapper, UserGovernanceMapper governanceMapper,
                                 AccountOperationLogMapper operationLogMapper) {
        this.userMapper = userMapper;
        this.governanceMapper = governanceMapper;
        this.operationLogMapper = operationLogMapper;
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
    public PageResponse<UserEntity> search(String keyword, String status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException("INVALID_PAGINATION", "page and size are invalid");
        }
        String normalizedKeyword = TextUtil.trimToNull(keyword);
        String normalizedStatus = TextUtil.normalizeUppercaseToNull(status);
        if (normalizedStatus != null) {
            try {
                UserStatus.valueOf(normalizedStatus);
            } catch (IllegalArgumentException exception) {
                throw new BusinessException("INVALID_USER_STATUS", "user status is not supported");
            }
        }
        return PageResponse.of(userMapper.search(normalizedKeyword, normalizedStatus, (page - 1) * size, size),
                userMapper.countSearch(normalizedKeyword, normalizedStatus), page, size);
    }

    /**
     * 查询用户治理详情。
     *
     * @param userId 用户主键
     * @return 用户治理详情
     */
    public UserGovernanceDetail detail(Long userId) {
        UserEntity user = requireUser(userId);
        return new UserGovernanceDetail(user, governanceMapper.countReservations(userId),
                governanceMapper.countOrders(userId), governanceMapper.countReports(userId),
                governanceMapper.findCreditLogs(userId));
    }

    /**
     * 禁用或启用用户并写入审计日志。
     *
     * @param userId 用户主键
     * @param target 目标状态
     * @param reason 操作原因
     * @param username 当前管理员
     * @return 更新后的用户
     */
    @Transactional
    public UserEntity changeStatus(Long userId, UserStatus target, String reason, String username) {
        UserEntity operator = requireAdmin(username);
        UserEntity targetUser = requireUser(userId);
        if (targetUser.getRole() == UserRole.ADMIN) {
            throw new BusinessException("ADMIN_STATUS_PROTECTED", "administrator status cannot be changed here");
        }
        if (targetUser.getStatus() == target) {
            return targetUser;
        }
        if (userMapper.updateStatus(userId, targetUser.getStatus(), target) != 1) {
            throw new BusinessException("USER_STATE_CHANGED", "user status changed, please retry");
        }
        operationLogMapper.insert(operator.getId(), userId,
                target == UserStatus.DISABLED ? "DISABLE_USER" : "ENABLE_USER", reason.trim());
        return userMapper.findById(userId);
    }

    /**
     * 调整用户信用分并写入变更日志。
     *
     * @param userId 用户主键
     * @param score 新信用分
     * @param reason 调整原因
     * @param username 当前管理员
     * @return 更新后的用户
     */
    @Transactional
    public UserEntity adjustCredit(Long userId, int score, String reason, String username) {
        UserEntity operator = requireAdmin(username);
        UserEntity user = requireUser(userId);
        int previous = user.getCreditScore() == null ? 100 : user.getCreditScore();
        if (previous == score) {
            return user;
        }
        if (userMapper.updateCredit(userId, previous, score) != 1) {
            throw new BusinessException("USER_CREDIT_CHANGED", "user credit changed, please retry");
        }
        governanceMapper.insertCreditLog(userId, previous, score, reason.trim(), operator.getId());
        operationLogMapper.insert(operator.getId(), userId, "ADJUST_CREDIT",
                "score=" + previous + "->" + score + "; reason=" + reason.trim());
        return userMapper.findById(userId);
    }

    /**
     * 查询并校验管理员。
     *
     * @param username 当前用户名
     * @return 管理员用户
     */
    private UserEntity requireAdmin(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getRole() != UserRole.ADMIN || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("ADMIN_REQUIRED", "active administrator account is required");
        }
        return user;
    }

    /**
     * 查询并校验目标用户存在。
     *
     * @param userId 用户主键
     * @return 用户信息
     */
    private UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "user does not exist");
        }
        return user;
    }
}
