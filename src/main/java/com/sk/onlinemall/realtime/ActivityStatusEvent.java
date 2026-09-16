package com.sk.onlinemall.realtime;

import com.sk.onlinemall.activity.model.FlashActivityEntity;

import java.time.LocalDateTime;

public record ActivityStatusEvent(String type, FlashActivityEntity activity, LocalDateTime updatedAt) {
    /**
     * 来源于。
     *
     * @param activity 活动信息
     * @return 方法执行结果
     */
    public static ActivityStatusEvent from(FlashActivityEntity activity) {
        return new ActivityStatusEvent("ACTIVITY_STATUS", activity, LocalDateTime.now());
    }
}
