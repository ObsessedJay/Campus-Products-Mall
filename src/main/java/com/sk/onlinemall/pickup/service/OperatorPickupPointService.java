package com.sk.onlinemall.pickup.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.pickup.mapper.OperatorPickupPointMapper;
import com.sk.onlinemall.pickup.mapper.PickupPointMapper;
import com.sk.onlinemall.pickup.model.PickupPointEntity;
import com.sk.onlinemall.pickup.model.PickupPointStatus;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OperatorPickupPointService {
    private final OperatorPickupPointMapper authorizationMapper;
    private final PickupPointMapper pickupPointMapper;
    private final UserMapper userMapper;

    /**
     * 创建运营人员自提点授权服务。
     *
     * @param authorizationMapper 点位授权数据访问组件
     * @param pickupPointMapper 自提点数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public OperatorPickupPointService(OperatorPickupPointMapper authorizationMapper,
                                      PickupPointMapper pickupPointMapper, UserMapper userMapper) {
        this.authorizationMapper = authorizationMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.userMapper = userMapper;
    }

    /**
     * 查询运营人员的自提点授权。
     *
     * @param operatorId 运营人员主键
     * @return 已授权自提点
     */
    public List<PickupPointEntity> findByOperator(Long operatorId) {
        requireOperator(operatorId);
        return authorizationMapper.findByOperatorId(operatorId);
    }

    /**
     * 替换运营人员的自提点授权。
     *
     * @param operatorId 运营人员主键
     * @param pickupPointIds 自提点主键列表
     * @return 更新后的授权自提点
     */
    @Transactional
    public List<PickupPointEntity> replace(Long operatorId, List<Long> pickupPointIds) {
        requireOperator(operatorId);
        List<Long> distinctIds = pickupPointIds.stream().distinct().toList();
        for (Long pickupPointId : distinctIds) {
            PickupPointEntity point = pickupPointMapper.findById(pickupPointId);
            if (point == null || point.getStatus() != PickupPointStatus.ACTIVE) {
                throw new BusinessException("PICKUP_POINT_NOT_AVAILABLE", "pickup point is not active");
            }
        }
        authorizationMapper.deleteByOperatorId(operatorId);
        if (!distinctIds.isEmpty()) {
            authorizationMapper.insertAll(operatorId, distinctIds);
        }
        return authorizationMapper.findByOperatorId(operatorId);
    }

    /**
     * 查询并校验运营人员账号。
     *
     * @param operatorId 运营人员主键
     * @return 运营人员信息
     */
    private UserEntity requireOperator(Long operatorId) {
        UserEntity operator = userMapper.findById(operatorId);
        if (operator == null || operator.getStatus() != UserStatus.ACTIVE
                || !operator.getRole().isMerchant()) {
            throw new BusinessException("OPERATOR_NOT_FOUND", "active operator does not exist");
        }
        return operator;
    }
}
