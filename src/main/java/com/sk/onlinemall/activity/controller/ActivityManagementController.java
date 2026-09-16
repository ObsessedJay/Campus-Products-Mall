package com.sk.onlinemall.activity.controller;

import com.sk.onlinemall.activity.dto.CreateActivityRequest;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.model.ReservationRosterItem;
import com.sk.onlinemall.activity.service.FlashActivityService;
import com.sk.onlinemall.activity.service.ReservationRosterService;
import com.sk.onlinemall.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/activities")
public class ActivityManagementController {
    private final FlashActivityService activityService;
    private final ReservationRosterService reservationRosterService;

    /**
     * 创建运营活动控制器。
     *
     * @param activityService 活动业务服务
     * @param reservationRosterService 预约名单服务
     */
    public ActivityManagementController(FlashActivityService activityService,
                                        ReservationRosterService reservationRosterService) {
        this.activityService = activityService;
        this.reservationRosterService = reservationRosterService;
    }

    /**
     * 查询全部状态活动。
     *
     * @return 全部活动
     */
    @GetMapping
    public ApiResponse<List<FlashActivityEntity>> list() {
        return ApiResponse.success(activityService.findForManagement());
    }

    /**
     * 更新可编辑活动并重新送审。
     *
     * @param activityId 活动主键
     * @param request 更新请求
     * @return 更新后的活动
     */
    @PutMapping("/{activityId}")
    public ApiResponse<FlashActivityEntity> update(@PathVariable Long activityId,
                                                    @Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.success(activityService.update(activityId, request));
    }

    /**
     * 查询活动预约名单。
     *
     * @param activityId 活动主键
     * @param status 可选预约状态
     * @return 包含学生和抽签信息的预约名单
     */
    @GetMapping("/{activityId}/reservations")
    public ApiResponse<List<ReservationRosterItem>> reservations(
            @PathVariable Long activityId,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(reservationRosterService.find(activityId, status));
    }

    /**
     * 导出活动预约名单工作簿。
     *
     * @param activityId 活动主键
     * @param status 可选预约状态
     * @return XLSX 文件响应
     */
    @GetMapping("/{activityId}/reservations/export")
    public ResponseEntity<byte[]> exportReservations(
            @PathVariable Long activityId,
            @RequestParam(required = false) String status) {
        byte[] content = reservationRosterService.export(activityId, status);
        String filename = "activity-" + activityId + "-reservations.xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentLength(content.length)
                .body(content);
    }
}
