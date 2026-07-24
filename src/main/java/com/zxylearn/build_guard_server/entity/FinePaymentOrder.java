package com.zxylearn.build_guard_server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fine_payment_order")
public class FinePaymentOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long violationId;
    private String outTradeNo;
    private String tradeNo;
    private BigDecimal amount;
    private String subject;
    private String qrCode;
    private Integer payStatus;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private String refundNo;
    private String rawResponse;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
