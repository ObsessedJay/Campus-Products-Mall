package com.sk.onlinemall.activity.service;

import com.sk.onlinemall.activity.mapper.FlashActivityMapper;
import com.sk.onlinemall.activity.model.FlashActivityEntity;
import com.sk.onlinemall.activity.model.ReservationRosterItem;
import com.sk.onlinemall.activity.model.ReservationStatus;
import com.sk.onlinemall.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class ReservationRosterService {
    private static final String[] HEADERS = {
            "序号", "预约编号", "学生昵称", "学生邮箱", "资格状态",
            "抽签批次", "抽签名次", "预约时间", "更新时间"
    };
    private final FlashActivityMapper activityMapper;

    /**
     * 创建活动预约名单服务。
     *
     * @param activityMapper 活动数据访问组件
     */
    public ReservationRosterService(FlashActivityMapper activityMapper) {
        this.activityMapper = activityMapper;
    }

    /**
     * 查询活动预约名单并按状态筛选。
     *
     * @param activityId 活动主键
     * @param status 可选预约状态文本
     * @return 活动预约名单
     */
    @Transactional(readOnly = true)
    public List<ReservationRosterItem> find(Long activityId, String status) {
        requireActivity(activityId);
        return activityMapper.findReservationRoster(activityId, parseStatus(status));
    }

    /**
     * 生成活动预约名单工作簿。
     *
     * @param activityId 活动主键
     * @param status 可选预约状态文本
     * @return XLSX 文件字节
     */
    @Transactional(readOnly = true)
    public byte[] export(Long activityId, String status) {
        FlashActivityEntity activity = requireActivity(activityId);
        List<ReservationRosterItem> roster = activityMapper.findReservationRoster(activityId, parseStatus(status));
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeWorkbook(workbook, activity, roster);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to generate reservation roster", exception);
        }
    }

    /**
     * 写入预约名单工作表及格式。
     *
     * @param workbook 工作簿
     * @param activity 活动信息
     * @param roster 预约名单
     */
    private void writeWorkbook(Workbook workbook, FlashActivityEntity activity,
                               List<ReservationRosterItem> roster) {
        Sheet sheet = workbook.createSheet("预约名单");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle bodyStyle = bodyStyle(workbook);
        CellStyle dateStyle = dateStyle(workbook);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        title.createCell(0).setCellValue(activity.getName() + " - 预约名单");
        title.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

        Row summary = sheet.createRow(1);
        summary.createCell(0).setCellValue("活动编号：" + activity.getId() + "    导出记录：" + roster.size());
        summary.getCell(0).setCellStyle(bodyStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, HEADERS.length - 1));

        Row header = sheet.createRow(2);
        header.setHeightInPoints(24);
        for (int column = 0; column < HEADERS.length; column++) {
            header.createCell(column).setCellValue(HEADERS[column]);
            header.getCell(column).setCellStyle(headerStyle);
        }

        for (int index = 0; index < roster.size(); index++) {
            ReservationRosterItem item = roster.get(index);
            Row row = sheet.createRow(index + 3);
            row.createCell(0).setCellValue(index + 1);
            row.createCell(1).setCellValue(item.reservationNo());
            row.createCell(2).setCellValue(item.nickname() == null ? "" : item.nickname());
            row.createCell(3).setCellValue(item.email() == null ? "" : item.email());
            row.createCell(4).setCellValue(statusLabel(item.status()));
            row.createCell(5).setCellValue(item.lotteryBatchNo() == null ? "" : item.lotteryBatchNo());
            if (item.drawRank() != null) {
                row.createCell(6).setCellValue(item.drawRank());
            } else {
                row.createCell(6).setCellValue("");
            }
            setDateCell(row, 7, item.createdAt(), dateStyle);
            setDateCell(row, 8, item.updatedAt(), dateStyle);
            for (int column = 0; column < HEADERS.length; column++) {
                if (column != 7 && column != 8) {
                    row.getCell(column).setCellStyle(bodyStyle);
                }
            }
        }

        int[] widths = {8, 38, 18, 34, 16, 36, 12, 22, 22};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, roster.size() + 2), 0, HEADERS.length - 1));
    }

    /**
     * 创建标题样式。
     *
     * @param workbook 工作簿
     * @return 标题样式
     */
    private CellStyle titleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /**
     * 创建表头样式。
     *
     * @param workbook 工作簿
     * @return 表头样式
     */
    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    /**
     * 创建普通数据样式。
     *
     * @param workbook 工作簿
     * @return 数据样式
     */
    private CellStyle bodyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style);
        return style;
    }

    /**
     * 创建日期数据样式。
     *
     * @param workbook 工作簿
     * @return 日期样式
     */
    private CellStyle dateStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        return style;
    }

    /**
     * 为单元格样式增加细边框。
     *
     * @param style 单元格样式
     */
    private void applyBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /**
     * 写入可为空的日期单元格。
     *
     * @param row 数据行
     * @param column 列下标
     * @param value 日期值
     * @param style 日期样式
     */
    private void setDateCell(Row row, int column, LocalDateTime value, CellStyle style) {
        if (value == null) {
            row.createCell(column).setCellValue("");
        } else {
            row.createCell(column).setCellValue(value);
        }
        row.getCell(column).setCellStyle(style);
    }

    /**
     * 将预约状态转换为运营侧中文标签。
     *
     * @param status 预约状态
     * @return 中文状态标签
     */
    private String statusLabel(ReservationStatus status) {
        return switch (status) {
            case PENDING -> "待抽签";
            case QUALIFIED -> "获得资格";
            case NOT_QUALIFIED -> "未中签";
            case CANCELLED -> "已取消";
        };
    }

    /**
     * 解析可选预约状态。
     *
     * @param status 状态文本
     * @return 预约状态，未指定时返回空
     */
    private ReservationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReservationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_RESERVATION_STATUS", "reservation status is not supported");
        }
    }

    /**
     * 查询并确认活动存在。
     *
     * @param activityId 活动主键
     * @return 活动信息
     */
    private FlashActivityEntity requireActivity(Long activityId) {
        FlashActivityEntity activity = activityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "activity does not exist");
        }
        return activity;
    }
}
