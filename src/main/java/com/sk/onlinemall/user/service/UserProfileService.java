package com.sk.onlinemall.user.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.user.dto.UpdateUserProfileRequest;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserProfileResponse;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private static final String UNIVERSITY_NAME = "成都信息工程大学";
    private final UserMapper userMapper;

    /**
     * 创建 UserProfileService 实例。
     *
     * @param userMapper 用户数据访问组件
     */
    public UserProfileService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 查询我的数据。
     *
     * @param username 当前用户名
     * @return 查询结果
     */
    public UserProfileResponse findMine(String username) {
        return toResponse(requireActiveUser(username));
    }

    /**
     * 更新我的数据。
     *
     * @param username 当前用户名
     * @param request 请求参数
     * @return 方法执行结果
     */
    @Transactional
    public UserProfileResponse updateMine(String username, UpdateUserProfileRequest request) {
        UserEntity user = requireActiveUser(username);
        userMapper.updateProfile(user.getId(), request.nickname().trim());
        return toResponse(requireActiveUser(username));
    }

    /**
     * 将用户实体转换为个人资料响应。
     *
     * @param user 用户信息
     * @return 方法执行结果
     */
    private UserProfileResponse toResponse(UserEntity user) {
        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getNickname(), UNIVERSITY_NAME,
                user.getRole().name());
    }

    /**
     * 查询并校验启用的用户。
     *
     * @param username 当前用户名
     * @return 方法执行结果
     */
    private UserEntity requireActiveUser(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("USER_NOT_AVAILABLE", "user account is not available");
        }
        return user;
    }

}
