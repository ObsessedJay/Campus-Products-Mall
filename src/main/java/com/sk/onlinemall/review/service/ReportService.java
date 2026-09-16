package com.sk.onlinemall.review.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.product.mapper.ProductMapper;
import com.sk.onlinemall.review.dto.CreateReportRequest;
import com.sk.onlinemall.review.mapper.ProductReviewMapper;
import com.sk.onlinemall.review.mapper.ReportRecordMapper;
import com.sk.onlinemall.review.model.ProductReviewEntity;
import com.sk.onlinemall.review.model.ReportRecordEntity;
import com.sk.onlinemall.review.model.ReportStatus;
import com.sk.onlinemall.review.model.ReportTargetType;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReportService {
    private final ReportRecordMapper reportMapper;
    private final ProductReviewMapper reviewMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    /**
     * 创建举报治理服务。
     *
     * @param reportMapper 举报数据访问组件
     * @param reviewMapper 评价数据访问组件
     * @param productMapper 商品数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public ReportService(ReportRecordMapper reportMapper, ProductReviewMapper reviewMapper,
                         ProductMapper productMapper, UserMapper userMapper) {
        this.reportMapper = reportMapper;
        this.reviewMapper = reviewMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    /**
     * 创建商品或评价举报，同一用户重复提交时返回已有记录。
     *
     * @param request 举报内容
     * @param username 当前用户名
     * @return 举报记录
     */
    @Transactional
    public ReportRecordEntity create(CreateReportRequest request, String username) {
        UserEntity reporter = requireUser(username);
        requireTarget(request.targetType(), request.targetId());
        ReportRecordEntity existing = reportMapper.findExisting(reporter.getId(), request.targetType(), request.targetId());
        if (existing != null) {
            return existing;
        }
        ReportRecordEntity report = new ReportRecordEntity();
        report.setReporterId(reporter.getId());
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReason(request.reason().trim());
        report.setStatus(ReportStatus.PENDING);
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException exception) {
            return reportMapper.findExisting(reporter.getId(), request.targetType(), request.targetId());
        }
        return reportMapper.findById(report.getId());
    }

    /**
     * 查询举报治理队列。
     *
     * @param status 可选处理状态
     * @return 举报列表
     */
    public List<ReportRecordEntity> findAll(ReportStatus status) {
        return reportMapper.findAll(status);
    }

    /**
     * 确认举报成立并隐藏违规评价。
     *
     * @param id 举报主键
     * @param result 处理说明
     * @param username 管理员用户名
     * @return 更新后的举报记录
     */
    @Transactional
    public ReportRecordEntity resolve(Long id, String result, String username) {
        return handle(id, ReportStatus.RESOLVED, result, username);
    }

    /**
     * 驳回不成立的举报。
     *
     * @param id 举报主键
     * @param result 处理说明
     * @param username 管理员用户名
     * @return 更新后的举报记录
     */
    @Transactional
    public ReportRecordEntity reject(Long id, String result, String username) {
        return handle(id, ReportStatus.REJECTED, result, username);
    }

    /**
     * 按预期状态完成举报处理。
     *
     * @param id 举报主键
     * @param status 目标状态
     * @param result 处理说明
     * @param username 管理员用户名
     * @return 更新后的举报记录
     */
    private ReportRecordEntity handle(Long id, ReportStatus status, String result, String username) {
        ReportRecordEntity report = reportMapper.findById(id);
        if (report == null) {
            throw new BusinessException("REPORT_NOT_FOUND", "report not found");
        }
        UserEntity handler = requireUser(username);
        if (reportMapper.handle(id, status, handler.getId(), result.trim()) != 1) {
            throw new BusinessException("REPORT_ALREADY_HANDLED", "report has already been handled");
        }
        if (status == ReportStatus.RESOLVED && report.getTargetType() == ReportTargetType.REVIEW) {
            reviewMapper.hide(report.getTargetId());
        }
        return reportMapper.findById(id);
    }

    /**
     * 校验举报目标存在。
     *
     * @param type 目标类型
     * @param id 目标主键
     */
    private void requireTarget(ReportTargetType type, Long id) {
        boolean exists = switch (type) {
            case PRODUCT -> productMapper.findById(id) != null;
            case REVIEW -> reviewMapper.findById(id) != null;
            case USER -> userMapper.findById(id) != null;
        };
        if (!exists) {
            throw new BusinessException("REPORT_TARGET_NOT_FOUND", "report target not found");
        }
    }

    /**
     * 查询当前用户实体。
     *
     * @param username 当前用户名
     * @return 用户实体
     */
    private UserEntity requireUser(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null) {
            throw new BusinessException("USER_NOT_FOUND", "user not found");
        }
        return user;
    }
}
