package com.zxylearn.build_guard_server.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PersonnelDtos {
    private PersonnelDtos() {
    }

    public record PersonnelRequest(
            @NotBlank String name,
            String phone,
            @Email String email,
            Long avatarFileId,
            String jobTitle,
            String teamName,
            Integer status
    ) {
    }

    public record PersonnelView(
            Long id,
            String name,
            String phone,
            String email,
            String avatarUrl,
            String jobTitle,
            String teamName,
            Integer status,
            LocalDateTime createdAt
    ) {
    }

    public record ViolationRequest(
            Long personnelId,
            @NotBlank String violationItem,
            BigDecimal fineAmount,
            Integer paymentStatus,
            Long sourceAlarmId,
            LocalDateTime occurredAt,
            String remark
    ) {
    }

    public record ViolationView(
            Long id,
            Long personnelId,
            String personnelName,
            String violationItem,
            BigDecimal fineAmount,
            Integer paymentStatus,
            Long sourceAlarmId,
            String snapshotUrl,
            Integer reviewStatus,
            LocalDateTime occurredAt,
            String remark,
            LocalDateTime createdAt
    ) {
    }

    public record ViolationReviewRequest(
            Long personnelId,
            BigDecimal fineAmount,
            Integer paymentStatus,
            String remark
    ) {
    }

    public record ViolationFineRequest(
            Long personnelId,
            BigDecimal fineAmount,
            String remark,
            Boolean revoked
    ) {
    }

    public record PaymentOrderView(
            Long id,
            Long violationId,
            String outTradeNo,
            BigDecimal amount,
            String subject,
            String qrCode,
            Integer payStatus,
            LocalDateTime expireAt
    ) {
    }
}
