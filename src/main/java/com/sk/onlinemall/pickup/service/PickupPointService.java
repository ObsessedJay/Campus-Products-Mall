package com.sk.onlinemall.pickup.service;

import com.sk.onlinemall.pickup.mapper.PickupPointMapper;
import com.sk.onlinemall.pickup.model.PickupPointEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PickupPointService {
    private final PickupPointMapper pickupPointMapper;

    /**
     * 创建 PickupPointService 实例。
     *
     * @param pickupPointMapper 自提核销自提点数据访问组件
     */
    public PickupPointService(PickupPointMapper pickupPointMapper) {
        this.pickupPointMapper = pickupPointMapper;
    }

    /**
     * 查询启用的。
     *
     * @return 查询结果
     */
    public List<PickupPointEntity> findActive() {
        return pickupPointMapper.findActive();
    }
}
