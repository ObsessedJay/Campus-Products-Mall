package com.sk.onlinemall.order.controller;

import com.sk.onlinemall.common.api.ApiResponse;
import com.sk.onlinemall.order.dto.CreateOrderRequest;
import com.sk.onlinemall.order.dto.PayOrderRequest;
import com.sk.onlinemall.order.dto.VerifyPickupRequest;
import com.sk.onlinemall.order.dto.RequestRefundRequest;
import com.sk.onlinemall.order.dto.ProcessRefundRequest;
import com.sk.onlinemall.order.model.OrderDetailResponse;
import com.sk.onlinemall.order.model.PaymentRecordEntity;
import com.sk.onlinemall.order.model.PickupVerificationEntity;
import com.sk.onlinemall.order.model.TradeOrderEntity;
import com.sk.onlinemall.order.model.RefundRecordEntity;
import com.sk.onlinemall.order.model.FlashSaleRequestEntity;
import com.sk.onlinemall.order.service.FlashSaleRequestService;
import com.sk.onlinemall.order.service.TradeOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/orders")
public class TradeOrderController {
    private final TradeOrderService orderService;
    private final FlashSaleRequestService flashSaleRequestService;

    /**
     * 创建 TradeOrderController 实例。
     *
     * @param orderService 订单业务服务
     * @param flashSaleRequestService 抢购发售请求业务服务
     */
    public TradeOrderController(TradeOrderService orderService, FlashSaleRequestService flashSaleRequestService) {
        this.orderService = orderService;
        this.flashSaleRequestService = flashSaleRequestService;
    }

    /**
     * 查询业务数据列表。
     *
     * @param status 业务状态或连接关闭状态
     * @param authentication 当前登录身份
     * @return 查询结果
     */
    @GetMapping
    public ApiResponse<List<TradeOrderEntity>> list(
            @RequestParam(required = false) String status, Authentication authentication) {
        return ApiResponse.success(orderService.findMine(authentication.getName(), status));
    }

    /**
     * 查询业务详情。
     *
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 查询结果
     */
    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> detail(
            @PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(orderService.detail(id, authentication.getName()));
    }

    /**
     * 为当前学生创建普通订单。
     *
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TradeOrderEntity>> create(
            @Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(orderService.create(request, authentication.getName())));
    }

    /**
     * 创建抢购发售。
     *
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/flash-sale")
    public ResponseEntity<ApiResponse<FlashSaleRequestEntity>> createFlashSale(
            @Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("accepted",
                        flashSaleRequestService.accept(request, authentication.getName())));
    }

    /**
     * 抢购发售结果。
     *
     * @param requestNo 业务请求号
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @GetMapping("/flash-sale/{requestNo}")
    public ApiResponse<FlashSaleRequestEntity> flashSaleResult(
            @PathVariable String requestNo, Authentication authentication) {
        return ApiResponse.success(flashSaleRequestService.findMine(requestNo, authentication.getName()));
    }

    /**
     * 支付。
     *
     * @param id 记录主键
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/pay")
    public ApiResponse<PaymentRecordEntity> pay(
            @PathVariable Long id, @Valid @RequestBody PayOrderRequest request,
            Authentication authentication) {
        return ApiResponse.success(orderService.pay(id, request, authentication.getName()));
    }

    /**
     * 取消。
     *
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<TradeOrderEntity> cancel(
            @PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(orderService.cancel(id, authentication.getName()));
    }

    /**
     * 自提核销。
     *
     * @param id 记录主键
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @GetMapping("/{id}/pickup")
    public ApiResponse<PickupVerificationEntity> pickup(
            @PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(orderService.pickup(id, authentication.getName()));
    }

    /**
     * 验证。
     *
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 方法执行结果
     */
    @PostMapping("/verification")
    public ApiResponse<TradeOrderEntity> verify(
            @Valid @RequestBody VerifyPickupRequest request, Authentication authentication) {
        return ApiResponse.success(orderService.verifyPickup(request.pickupCode(), authentication.getName()));
    }

    /**
     * 提交订单退款申请。
     *
     * @param id 记录主键
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/refund")
    public ApiResponse<RefundRecordEntity> requestRefund(
            @PathVariable Long id, @Valid @RequestBody RequestRefundRequest request,
            Authentication authentication) {
        return ApiResponse.success(orderService.requestRefund(id, request.reason(), authentication.getName()));
    }

    /**
     * 批准订单退款申请。
     *
     * @param id 记录主键
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/refund/approve")
    public ApiResponse<RefundRecordEntity> approveRefund(
            @PathVariable Long id, @Valid @RequestBody ProcessRefundRequest request,
            Authentication authentication) {
        return ApiResponse.success(orderService.processRefund(id, request.reason(), true, authentication.getName()));
    }

    /**
     * 驳回订单退款申请。
     *
     * @param id 记录主键
     * @param request 请求参数
     * @param authentication 当前登录身份
     * @return 处理后的业务数据
     */
    @PostMapping("/{id}/refund/reject")
    public ApiResponse<RefundRecordEntity> rejectRefund(
            @PathVariable Long id, @Valid @RequestBody ProcessRefundRequest request,
            Authentication authentication) {
        return ApiResponse.success(orderService.processRefund(id, request.reason(), false, authentication.getName()));
    }
}
