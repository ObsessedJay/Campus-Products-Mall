package com.sk.onlinemall.review.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.review.dto.CreateReportRequest;
import com.sk.onlinemall.review.dto.HandleReportRequest;
import com.sk.onlinemall.review.model.ReportRecordEntity;
import com.sk.onlinemall.review.model.ReportStatus;
import com.sk.onlinemall.review.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reportService;

    /**
     * 创建举报控制器。
     *
     * @param reportService 举报治理服务
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 创建商品或评价举报。
     *
     * @param request 举报内容
     * @param authentication 当前登录身份
     * @return 举报记录
     */
    @PostMapping("/reports")
    public ApiResponse<ReportRecordEntity> create(@Valid @RequestBody CreateReportRequest request,
                                                   Authentication authentication) {
        return ApiResponse.success(reportService.create(request, authentication.getName()));
    }

    /**
     * 查询管理员举报队列。
     *
     * @param status 可选处理状态
     * @return 举报列表
     */
    @GetMapping("/admin/reports")
    public ApiResponse<List<ReportRecordEntity>> list(@RequestParam(required = false) ReportStatus status) {
        return ApiResponse.success(reportService.findAll(status));
    }

    /**
     * 确认举报成立。
     *
     * @param id 举报主键
     * @param request 处理说明
     * @param authentication 当前登录身份
     * @return 更新后的举报记录
     */
    @PostMapping("/admin/reports/{id}/resolve")
    public ApiResponse<ReportRecordEntity> resolve(@PathVariable Long id,
                                                    @Valid @RequestBody HandleReportRequest request,
                                                    Authentication authentication) {
        return ApiResponse.success(reportService.resolve(id, request.result(), authentication.getName()));
    }

    /**
     * 驳回举报。
     *
     * @param id 举报主键
     * @param request 处理说明
     * @param authentication 当前登录身份
     * @return 更新后的举报记录
     */
    @PostMapping("/admin/reports/{id}/reject")
    public ApiResponse<ReportRecordEntity> reject(@PathVariable Long id,
                                                   @Valid @RequestBody HandleReportRequest request,
                                                   Authentication authentication) {
        return ApiResponse.success(reportService.reject(id, request.result(), authentication.getName()));
    }
}
