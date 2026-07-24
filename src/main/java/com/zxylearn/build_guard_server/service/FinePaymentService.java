package com.zxylearn.build_guard_server.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.alipay.api.internal.util.AlipaySignature;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PaymentOrderView;
import com.zxylearn.build_guard_server.entity.FinePaymentOrder;
import com.zxylearn.build_guard_server.entity.Personnel;
import com.zxylearn.build_guard_server.entity.ViolationRecord;
import com.zxylearn.build_guard_server.mapper.FinePaymentOrderMapper;
import com.zxylearn.build_guard_server.mapper.PersonnelMapper;
import com.zxylearn.build_guard_server.mapper.ViolationRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinePaymentService {
    private final ViolationRecordMapper violationRecordMapper;
    private final FinePaymentOrderMapper paymentOrderMapper;
    private final PersonnelMapper personnelMapper;
    private final String gatewayUrl;
    private final String appId;
    private final String privateKey;
    private final String alipayPublicKey;
    private final String notifyUrl;
    private final String charset;
    private final String signType;

    public FinePaymentService(ViolationRecordMapper violationRecordMapper,
                              FinePaymentOrderMapper paymentOrderMapper,
                              PersonnelMapper personnelMapper,
                              @Value("${payment.alipay.gateway-url:https://openapi-sandbox.dl.alipaydev.com/gateway.do}") String gatewayUrl,
                              @Value("${payment.alipay.app-id:}") String appId,
                              @Value("${payment.alipay.private-key:}") String privateKey,
                              @Value("${payment.alipay.public-key:}") String alipayPublicKey,
                              @Value("${payment.alipay.notify-url:http://110.41.166.11:18080/api/payments/alipay/notify}") String notifyUrl,
                              @Value("${payment.alipay.charset:UTF-8}") String charset,
                              @Value("${payment.alipay.sign-type:RSA2}") String signType) {
        this.violationRecordMapper = violationRecordMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.personnelMapper = personnelMapper;
        this.gatewayUrl = trim(gatewayUrl);
        this.appId = trim(appId);
        this.privateKey = trim(privateKey);
        this.alipayPublicKey = trim(alipayPublicKey);
        this.notifyUrl = trim(notifyUrl);
        this.charset = trim(charset).isBlank() ? "UTF-8" : trim(charset);
        this.signType = trim(signType).isBlank() ? "RSA2" : trim(signType);
    }

    public PaymentOrderView refreshPaymentQr(long violationId) {
        ViolationRecord violation = requirePayableViolation(violationId);
        FinePaymentOrder order = createPaymentOrder(violation);
        return toView(order);
    }

    public PaymentOrderView currentOrCreatePaymentQr(long violationId) {
        ViolationRecord violation = violationRecordMapper.selectById(violationId);
        if (violation == null) {
            throw new BusinessException(404, "违规记录不存在");
        }
        FinePaymentOrder latestPaid = paymentOrderMapper.selectOne(Wrappers.<FinePaymentOrder>lambdaQuery()
                .eq(FinePaymentOrder::getViolationId, violationId)
                .eq(FinePaymentOrder::getPayStatus, 1)
                .orderByDesc(FinePaymentOrder::getId)
                .last("limit 1"));
        if (latestPaid != null || Integer.valueOf(1).equals(violation.getPaymentStatus())) {
            return latestPaid == null ? paidFallbackView(violation) : toView(latestPaid);
        }

        requirePayableViolation(violationId);
        FinePaymentOrder active = paymentOrderMapper.selectOne(Wrappers.<FinePaymentOrder>lambdaQuery()
                .eq(FinePaymentOrder::getViolationId, violationId)
                .eq(FinePaymentOrder::getPayStatus, 0)
                .gt(FinePaymentOrder::getExpireAt, LocalDateTime.now())
                .orderByDesc(FinePaymentOrder::getId)
                .last("limit 1"));
        if (active != null) {
            return toView(active);
        }
        return toView(createPaymentOrder(violation));
    }

    public byte[] paymentQrPng(long violationId, Long orderId) {
        FinePaymentOrder order = paymentOrderMapper.selectOne(Wrappers.<FinePaymentOrder>lambdaQuery()
                .eq(FinePaymentOrder::getViolationId, violationId)
                .eq(orderId != null, FinePaymentOrder::getId, orderId)
                .orderByDesc(FinePaymentOrder::getId)
                .last("limit 1"));
        if (order == null || order.getQrCode() == null || order.getQrCode().isBlank()) {
            throw new BusinessException(404, "未找到支付二维码");
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(order.getQrCode(), BarcodeFormat.QR_CODE, 320, 320, hints);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException(500, "支付二维码生成失败");
        }
    }

    public String publicPaymentPageUrl(long violationId, String publicBaseUrl) {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "http://110.41.166.11:18080"
                : publicBaseUrl.trim();
        return base.replaceAll("/+$", "") + "/api/payments/fines/" + violationId + "/pay";
    }

    private FinePaymentOrder createPaymentOrder(ViolationRecord violation) {
        FinePaymentOrder order = new FinePaymentOrder();
        order.setViolationId(violation.getId());
        order.setOutTradeNo("BGF" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        order.setAmount(violation.getFineAmount() == null ? BigDecimal.ZERO : violation.getFineAmount());
        order.setSubject(paymentSubject(violation));
        order.setPayStatus(0);
        order.setExpireAt(LocalDateTime.now().plusMinutes(30));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        String qrCode = alipayConfigured() ? createAlipayQr(order) : localFallbackQr(order);
        order.setQrCode(qrCode);
        paymentOrderMapper.insert(order);
        return order;
    }

    public boolean handleAlipayNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        if (alipayConfigured()) {
            try {
                boolean verified = AlipaySignature.verifyV2(params, alipayPublicKey, charset, signType);
                if (!verified) {
                    return false;
                }
            } catch (AlipayApiException exception) {
                return false;
            }
        }

        String outTradeNo = params.get("out_trade_no");
        if (outTradeNo == null || outTradeNo.isBlank()) {
            return false;
        }
        FinePaymentOrder order = paymentOrderMapper.selectOne(Wrappers.<FinePaymentOrder>lambdaQuery()
                .eq(FinePaymentOrder::getOutTradeNo, outTradeNo)
                .last("limit 1"));
        if (order == null) {
            return false;
        }
        String tradeStatus = params.getOrDefault("trade_status", "");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            return true;
        }

        order.setTradeNo(params.get("trade_no"));
        order.setPayStatus(1);
        order.setPaidAt(LocalDateTime.now());
        order.setRawResponse(params.toString());
        order.setUpdatedAt(LocalDateTime.now());
        paymentOrderMapper.updateById(order);

        ViolationRecord violation = new ViolationRecord();
        violation.setId(order.getViolationId());
        violation.setPaymentStatus(1);
        violation.setUpdatedAt(LocalDateTime.now());
        violationRecordMapper.updateById(violation);
        return true;
    }

    public PaymentOrderView syncPaymentStatus(long violationId) {
        List<FinePaymentOrder> orders = paymentOrderMapper.selectList(Wrappers.<FinePaymentOrder>lambdaQuery()
                .eq(FinePaymentOrder::getViolationId, violationId)
                .orderByDesc(FinePaymentOrder::getId));
        if (orders.isEmpty()) {
            throw new BusinessException(404, "未找到支付订单");
        }
        for (FinePaymentOrder order : orders) {
            if (Integer.valueOf(1).equals(order.getPayStatus())) {
                return toView(order);
            }
            if (!Integer.valueOf(2).equals(order.getPayStatus()) && alipayConfigured()) {
                queryAlipayAndUpdate(order);
                if (Integer.valueOf(1).equals(order.getPayStatus())) {
                    return toView(order);
                }
            }
        }
        return toView(orders.get(0));
    }

    public void refund(long violationId) {
        ViolationRecord violation = violationRecordMapper.selectById(violationId);
        if (violation == null) {
            throw new BusinessException(404, "违规记录不存在");
        }
        if (!Integer.valueOf(1).equals(violation.getPaymentStatus())) {
            throw new BusinessException(400, "只有已支付罚款可以退款");
        }
        FinePaymentOrder order = paymentOrderMapper.selectOne(Wrappers.<FinePaymentOrder>lambdaQuery()
                .eq(FinePaymentOrder::getViolationId, violationId)
                .eq(FinePaymentOrder::getPayStatus, 1)
                .orderByDesc(FinePaymentOrder::getId)
                .last("limit 1"));
        if (order == null) {
            throw new BusinessException(404, "未找到已支付订单");
        }
        if (alipayConfigured()) {
            refundAlipay(order);
        }
        order.setPayStatus(2);
        order.setRefundedAt(LocalDateTime.now());
        order.setRefundNo("RF" + order.getOutTradeNo());
        order.setUpdatedAt(LocalDateTime.now());
        paymentOrderMapper.updateById(order);

        violation.setPaymentStatus(2);
        violation.setUpdatedAt(LocalDateTime.now());
        violationRecordMapper.updateById(violation);
    }

    private ViolationRecord requirePayableViolation(long violationId) {
        ViolationRecord violation = violationRecordMapper.selectById(violationId);
        if (violation == null) {
            throw new BusinessException(404, "违规记录不存在");
        }
        if (!Integer.valueOf(1).equals(violation.getReviewStatus())) {
            throw new BusinessException(400, "违规未审核通过，不能发起罚款支付");
        }
        if (Integer.valueOf(1).equals(violation.getPaymentStatus())) {
            throw new BusinessException(400, "罚款已支付");
        }
        if (Integer.valueOf(3).equals(violation.getPaymentStatus())) {
            throw new BusinessException(400, "罚款已撤销");
        }
        if (violation.getPersonnelId() == null) {
            throw new BusinessException(400, "请先确认违规人员后再发起支付");
        }
        if (personnelMapper.selectById(violation.getPersonnelId()) == null) {
            throw new BusinessException(400, "请先确认违规人员后再发起支付");
        }
        if (violation.getFineAmount() == null || violation.getFineAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "罚款金额必须大于0");
        }
        return violation;
    }

    private String createAlipayQr(FinePaymentOrder order) {
        try {
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setNotifyUrl(notifyUrl);
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(order.getOutTradeNo());
            model.setTotalAmount(order.getAmount().stripTrailingZeros().toPlainString());
            model.setSubject(order.getSubject());
            model.setTimeoutExpress("30m");
            request.setBizModel(model);
            AlipayTradePrecreateResponse response = alipayClient().execute(request);
            order.setRawResponse(response.getBody());
            if (!response.isSuccess() || response.getQrCode() == null || response.getQrCode().isBlank()) {
                throw new BusinessException(502, "支付宝预下单失败：" + response.getSubMsg());
            }
            return response.getQrCode();
        } catch (AlipayApiException exception) {
            throw new BusinessException(502, "支付宝预下单失败");
        }
    }

    private void refundAlipay(FinePaymentOrder order) {
        try {
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(order.getOutTradeNo());
            model.setRefundAmount(order.getAmount().stripTrailingZeros().toPlainString());
            model.setOutRequestNo("RF" + order.getOutTradeNo());
            request.setBizModel(model);
            AlipayTradeRefundResponse response = alipayClient().execute(request);
            order.setRawResponse(response.getBody());
            if (!response.isSuccess()) {
                throw new BusinessException(502, "支付宝退款失败：" + response.getSubMsg());
            }
        } catch (AlipayApiException exception) {
            throw new BusinessException(502, "支付宝退款失败");
        }
    }

    private void queryAlipayAndUpdate(FinePaymentOrder order) {
        try {
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(order.getOutTradeNo());
            request.setBizModel(model);
            AlipayTradeQueryResponse response = alipayClient().execute(request);
            order.setRawResponse(response.getBody());
            order.setUpdatedAt(LocalDateTime.now());
            if (response.isSuccess()
                    && ("TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus()))) {
                order.setTradeNo(response.getTradeNo());
                order.setPayStatus(1);
                order.setPaidAt(LocalDateTime.now());
                paymentOrderMapper.updateById(order);

                ViolationRecord violation = new ViolationRecord();
                violation.setId(order.getViolationId());
                violation.setPaymentStatus(1);
                violation.setUpdatedAt(LocalDateTime.now());
                violationRecordMapper.updateById(violation);
                return;
            }
            paymentOrderMapper.updateById(order);
        } catch (AlipayApiException exception) {
            order.setRawResponse(exception.getMessage());
            order.setUpdatedAt(LocalDateTime.now());
            paymentOrderMapper.updateById(order);
        }
    }

    private AlipayClient alipayClient() {
        return new DefaultAlipayClient(gatewayUrl, appId, privateKey, "json", charset, alipayPublicKey, signType);
    }

    private String paymentSubject(ViolationRecord violation) {
        Personnel person = violation.getPersonnelId() == null ? null : personnelMapper.selectById(violation.getPersonnelId());
        String name = person == null ? "现场人员" : person.getName();
        return "BuildGuard罚款-" + name + "-" + violation.getViolationItem();
    }

    private String localFallbackQr(FinePaymentOrder order) {
        return "buildguard://fine-payment/" + order.getOutTradeNo() + "?amount=" + order.getAmount().stripTrailingZeros().toPlainString();
    }

    private PaymentOrderView toView(FinePaymentOrder order) {
        return new PaymentOrderView(
                order.getId(),
                order.getViolationId(),
                order.getOutTradeNo(),
                order.getAmount(),
                order.getSubject(),
                order.getQrCode(),
                order.getPayStatus(),
                order.getExpireAt()
        );
    }

    private PaymentOrderView paidFallbackView(ViolationRecord violation) {
        return new PaymentOrderView(
                null,
                violation.getId(),
                null,
                violation.getFineAmount(),
                paymentSubject(violation),
                null,
                1,
                null
        );
    }

    private boolean alipayConfigured() {
        return !gatewayUrl.isBlank() && !appId.isBlank() && !privateKey.isBlank() && !alipayPublicKey.isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
