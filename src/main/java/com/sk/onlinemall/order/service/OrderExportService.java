package com.sk.onlinemall.order.service;

import com.sk.onlinemall.common.exception.BusinessException;
import com.sk.onlinemall.order.mapper.TradeOrderMapper;
import com.sk.onlinemall.order.model.OrderExportRow;
import com.sk.onlinemall.order.model.OrderStatus;
import com.sk.onlinemall.user.mapper.UserMapper;
import com.sk.onlinemall.user.model.UserEntity;
import com.sk.onlinemall.user.model.UserRole;
import com.sk.onlinemall.user.model.UserStatus;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class OrderExportService {
    private static final String[] HEADERS = {
            "序号", "订单编号", "活动编号", "学生昵称", "学生邮箱", "订单状态", "订单金额",
            "商品", "规格编码", "规格名称", "单价", "数量", "明细金额", "自提点", "自提地址",
            "核销状态", "提货码", "核销人", "核销时间", "下单时间"
    };
    private final TradeOrderMapper orderMapper;
    private final UserMapper userMapper;

    /**
     * 创建订单导出服务。
     *
     * @param orderMapper 订单数据访问组件
     * @param userMapper 用户数据访问组件
     */
    public OrderExportService(TradeOrderMapper orderMapper, UserMapper userMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
    }

    /**
     * 按运营筛选条件生成订单与核销结果工作簿。
     *
     * @param activityId 可选活动主键
     * @param status 可选订单状态文本
     * @param pickupPointId 可选自提点主键
     * @param verificationStatus 可选核销状态文本
     * @param username 当前运营账号标识
     * @return XLSX 文件字节
     */
    @Transactional(readOnly = true)
    public byte[] export(Long activityId, String status, Long pickupPointId, String verificationStatus,
                         String username) {
        UserEntity operator = requireOperator(username);
        OrderStatus parsedStatus = parseOrderStatus(status);
        Boolean verified = parseVerificationStatus(verificationStatus);
        Long operatorId = operator.getRole() == UserRole.ADMIN ? null : operator.getId();
        List<OrderExportRow> rows = orderMapper.findForExport(
                activityId, parsedStatus, pickupPointId, verified, operatorId);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeWorkbook(workbook, rows, activityId, parsedStatus, pickupPointId, verified);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to generate order export", exception);
        }
    }

    /**
     * 查询并校验运营操作人。
     *
     * @param username 当前账号标识
     * @return 有效运营账号
     */
    private UserEntity requireOperator(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("USER_NOT_FOUND", "user does not exist or is disabled");
        }
        if (!user.getRole().isMerchant() && user.getRole() != UserRole.ADMIN) {
            throw new BusinessException("FORBIDDEN", "operator permission is required");
        }
        return user;
    }

    /**
     * 写入订单导出工作表及格式。
     *
     * @param workbook 工作簿
     * @param rows 订单导出明细
     * @param activityId 可选活动主键
     * @param status 可选订单状态
     * @param pickupPointId 可选自提点主键
     * @param verified 可选核销结果
     */
    private void writeWorkbook(Workbook workbook, List<OrderExportRow> rows, Long activityId,
                               OrderStatus status, Long pickupPointId, Boolean verified) {
        Sheet sheet = workbook.createSheet("订单与核销结果");
        CellStyle titleStyle = titleStyle(workbook);
        CellStyle headerStyle = headerStyle(workbook);
        CellStyle bodyStyle = bodyStyle(workbook);
        CellStyle dateStyle = dateStyle(workbook);
        CellStyle moneyStyle = moneyStyle(workbook);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(28);
        title.createCell(0).setCellValue("订单与核销结果");
        title.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

        Row summary = sheet.createRow(1);
        summary.createCell(0).setCellValue(summaryText(rows.size(), activityId, status, pickupPointId, verified));
        summary.getCell(0).setCellStyle(bodyStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, HEADERS.length - 1));

        Row header = sheet.createRow(2);
        header.setHeightInPoints(24);
        for (int column = 0; column < HEADERS.length; column++) {
            header.createCell(column).setCellValue(HEADERS[column]);
            header.getCell(column).setCellStyle(headerStyle);
        }

        for (int index = 0; index < rows.size(); index++) {
            writeDataRow(sheet.createRow(index + 3), rows.get(index), index + 1, bodyStyle, moneyStyle, dateStyle);
        }
        int[] widths = {8, 38, 14, 18, 32, 16, 14, 26, 18, 20, 14, 10, 14, 22, 34, 14, 18, 18, 22, 22};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new CellRangeAddress(2, Math.max(2, rows.size() + 2), 0, HEADERS.length - 1));
    }

    /**
     * 写入一条订单导出明细。
     *
     * @param row 工作表数据行
     * @param item 订单导出明细
     * @param sequence 导出序号
     * @param bodyStyle 普通单元格样式
     * @param moneyStyle 金额单元格样式
     * @param dateStyle 日期单元格样式
     */
    private void writeDataRow(Row row, OrderExportRow item, int sequence, CellStyle bodyStyle,
                              CellStyle moneyStyle, CellStyle dateStyle) {
        setText(row, 0, sequence, bodyStyle);
        setText(row, 1, item.orderNo(), bodyStyle);
        setText(row, 2, item.activityId(), bodyStyle);
        setText(row, 3, item.nickname(), bodyStyle);
        setText(row, 4, item.email(), bodyStyle);
        setText(row, 5, orderStatusLabel(item.status()), bodyStyle);
        setMoney(row, 6, item.totalAmount(), moneyStyle);
        setText(row, 7, item.productName(), bodyStyle);
        setText(row, 8, item.skuCode(), bodyStyle);
        setText(row, 9, item.skuName(), bodyStyle);
        setMoney(row, 10, item.unitPrice(), moneyStyle);
        setText(row, 11, item.quantity(), bodyStyle);
        setMoney(row, 12, item.lineAmount(), moneyStyle);
        setText(row, 13, item.pickupPointName(), bodyStyle);
        setText(row, 14, item.pickupPointAddress(), bodyStyle);
        setText(row, 15, item.verifiedAt() == null ? "未核销" : "已核销", bodyStyle);
        setText(row, 16, item.pickupCode(), bodyStyle);
        setText(row, 17, item.verifierNickname(), bodyStyle);
        setDate(row, 18, item.verifiedAt(), dateStyle);
        setDate(row, 19, item.createdAt(), dateStyle);
    }

    /**
     * 生成导出筛选摘要。
     *
     * @param count 导出明细数量
     * @param activityId 可选活动主键
     * @param status 可选订单状态
     * @param pickupPointId 可选自提点主键
     * @param verified 可选核销结果
     * @return 筛选摘要文本
     */
    private String summaryText(int count, Long activityId, OrderStatus status, Long pickupPointId, Boolean verified) {
        return "导出明细：" + count
                + "    活动：" + (activityId == null ? "全部" : activityId)
                + "    订单状态：" + (status == null ? "全部" : orderStatusLabel(status))
                + "    自提点：" + (pickupPointId == null ? "全部" : pickupPointId)
                + "    核销状态：" + (verified == null ? "全部" : verified ? "已核销" : "未核销");
    }

    /**
     * 解析可选订单状态。
     *
     * @param status 状态文本
     * @return 订单状态，未指定时返回空
     */
    private OrderStatus parseOrderStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_ORDER_STATUS", "order status is not supported");
        }
    }

    /**
     * 解析可选核销状态。
     *
     * @param status 核销状态文本
     * @return true 表示已核销，false 表示未核销，未指定时返回空
     */
    private Boolean parseVerificationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "VERIFIED" -> true;
            case "PENDING" -> false;
            default -> throw new BusinessException("INVALID_VERIFICATION_STATUS",
                    "verification status is not supported");
        };
    }

    /**
     * 将订单状态转换为运营侧中文标签。
     *
     * @param status 订单状态
     * @return 中文状态标签
     */
    private String orderStatusLabel(OrderStatus status) {
        return switch (status) {
            case WAIT_PAYMENT -> "待支付";
            case PAID -> "已支付";
            case WAIT_VERIFICATION -> "待核销";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case REFUNDING -> "退款中";
            case REFUNDED -> "已退款";
        };
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
     * 创建金额数据样式。
     *
     * @param workbook 工作簿
     * @return 金额样式
     */
    private CellStyle moneyStyle(Workbook workbook) {
        CellStyle style = bodyStyle(workbook);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
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
     * 写入可为空的普通单元格。
     *
     * @param row 数据行
     * @param column 列下标
     * @param value 单元格值
     * @param style 单元格样式
     */
    private void setText(Row row, int column, Object value, CellStyle style) {
        row.createCell(column).setCellValue(value == null ? "" : String.valueOf(value));
        row.getCell(column).setCellStyle(style);
    }

    /**
     * 写入可为空的金额单元格。
     *
     * @param row 数据行
     * @param column 列下标
     * @param value 金额值
     * @param style 金额样式
     */
    private void setMoney(Row row, int column, BigDecimal value, CellStyle style) {
        if (value == null) {
            row.createCell(column).setCellValue("");
        } else {
            row.createCell(column).setCellValue(value.doubleValue());
        }
        row.getCell(column).setCellStyle(style);
    }

    /**
     * 写入可为空的日期单元格。
     *
     * @param row 数据行
     * @param column 列下标
     * @param value 日期值
     * @param style 日期样式
     */
    private void setDate(Row row, int column, LocalDateTime value, CellStyle style) {
        if (value == null) {
            row.createCell(column).setCellValue("");
        } else {
            row.createCell(column).setCellValue(value);
        }
        row.getCell(column).setCellStyle(style);
    }
}
