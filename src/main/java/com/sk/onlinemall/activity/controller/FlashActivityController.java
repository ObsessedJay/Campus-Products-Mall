package com.sk.onlinemall.activity.controller;

import com.sk.onlinemall.activity.dto.CreateActivityRequest;
import com.sk.onlinemall.activity.dto.RunLotteryRequest;
import com.sk.onlinemall.activity.dto.TerminateActivityRequest;
import com.sk.onlinemall.activity.model.ActivityReservationEntity;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.model.LotteryDrawResult;
import com.sk.onlinemall.activity.service.FlashActivityService;
import com.sk.onlinemall.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
public class FlashActivityController {
    private final FlashActivityService activityService;

    /**
     * 创建 FlashActivityController 实例。
     *
     * @param activityService 活动业务服务
     */
    public FlashActivityController(FlashActivityService activityService) {
        this.activityService = activityService;
    }

    /**
     * 查询业务数据列表。
     *
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<List<FlashActivityEntity>> list() {
        return ApiResponse.success(activityService.findPublic());
    }

    /**
     * 查询业务详情。
     *
     * @param id 记录主键
     * @return 查询结果
     */
    @GetMapping("/{id}")
    public ApiResponse<FlashActivityEntity> detail(@PathVariable Long id) {
        return ApiResponse.success(activityService.findPublicDetail(id));
    }

    /**
     * 创建抢购活动。
     *
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping
    public ResponseEntity<ApiResponse<FlashActivityEntity>> create(
            @Valid @RequestBody CreateActivityRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(activityService.create(request,
                        authentication == null ? null : authentication.getName())));
    }

    /**
     * 发布审核通过的抢购活动。
     *
     * @param id 记录主键
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<FlashActivityEntity> publish(@PathVariable Long id) {
        return ApiResponse.success(activityService.publish(id));
    }

    /**
     * 提交当前用户的活动预约。
     *
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/reservations")
    public ResponseEntity<ApiResponse<ActivityReservationEntity>> reserve(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(activityService.reserve(id, authentication.getName())));
    }

    /**
     * 查询当前用户的活动预约。
     *
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @GetMapping("/{id}/reservation")
    public ApiResponse<ActivityReservationEntity> myReservation(
            @PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(activityService.findMyReservation(id, authentication.getName()));
    }

    /**
     * 执行活动抽签并保存可追溯结果。
     *
     * @param id 记录主键
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @PostMapping("/{id}/lottery")
    public ApiResponse<LotteryDrawResult> runLottery(
            @PathVariable Long id, @Valid @RequestBody RunLotteryRequest request,
            Authentication authentication) {
        return ApiResponse.success(activityService.runLottery(id, request, authentication.getName()));
    }

    /**
     * 抽签。
     *
     * @param id 记录主键
     * @return 方法执行结果
     */
    @GetMapping("/{id}/lottery")
    public ApiResponse<LotteryDrawResult> lottery(@PathVariable Long id) {
        return ApiResponse.success(activityService.findLottery(id));
    }

    /**
     * 终止。
     *
     * @param id 记录主键
     * @param request 请求参数
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/terminate")
    public ApiResponse<FlashActivityEntity> terminate(
            @PathVariable Long id, @Valid @RequestBody TerminateActivityRequest request) {
        return ApiResponse.success(activityService.terminate(id, request.reason()));
    }
}
