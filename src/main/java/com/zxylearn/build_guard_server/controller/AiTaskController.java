package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.dto.AiDtos.AiResultMessage;
import com.zxylearn.build_guard_server.dto.AiDtos.AiTaskRequest;
import com.zxylearn.build_guard_server.dto.AiDtos.AiTaskResponse;
import com.zxylearn.build_guard_server.service.AiTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/tasks")
public class AiTaskController {
    private final AiTaskService aiTaskService;

    public AiTaskController(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @PostMapping
    public ApiResponse<AiTaskResponse> createTask(@Valid @RequestBody AiTaskRequest request) {
        return ApiResponse.ok(aiTaskService.createTask(request));
    }

    @PostMapping("/results")
    public ApiResponse<Void> acceptResultForLocalDebug(@Valid @RequestBody AiResultMessage message) {
        aiTaskService.handleResult(message);
        return ApiResponse.ok();
    }
}
