package com.zxylearn.build_guard_server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;

public final class AiDtos {
    private AiDtos() {
    }

    public record AiTaskRequest(
            @NotBlank String taskType,
            Long deviceId,
            Long sourceFileId,
            Map<String, Object> payload
    ) {
    }

    public record AiTaskResponse(
            Long taskId,
            String messageId,
            String taskStatus
    ) {
    }

    public record AiRequestMessage(
            String messageId,
            Long taskId,
            String taskType,
            Long deviceId,
            Long sourceFileId,
            LocalDateTime occurredAt,
            Map<String, Object> payload
    ) {
    }

    public record AiResultMessage(
            @NotBlank String messageId,
            @NotNull Long taskId,
            @NotBlank String resultStatus,
            Map<String, Object> detections,
            Map<String, Object> prediction,
            Map<String, Object> model,
            String errorMessage
    ) {
    }
}
