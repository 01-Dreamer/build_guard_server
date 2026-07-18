package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PageDtos.AlarmView;
import com.zxylearn.build_guard_server.dto.PageDtos.ChartPoint;
import com.zxylearn.build_guard_server.dto.PageDtos.EnvironmentRealtimeView;
import com.zxylearn.build_guard_server.dto.PageDtos.SprayRecordView;
import com.zxylearn.build_guard_server.dto.PageDtos.SprayTaskRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.SprayTaskView;
import com.zxylearn.build_guard_server.service.PlatformPageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/environment")
public class EnvironmentController {
    private final PlatformPageService platformPageService;

    public EnvironmentController(PlatformPageService platformPageService) {
        this.platformPageService = platformPageService;
    }

    @GetMapping("/realtime")
    public ApiResponse<EnvironmentRealtimeView> realtime(@RequestParam(required = false) String deviceCode) {
        return ApiResponse.ok(platformPageService.environmentRealtime(deviceCode));
    }

    @GetMapping("/history")
    public ApiResponse<PageResult<ChartPoint>> history(
            @RequestParam(required = false) String deviceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.environmentHistory(deviceCode, page, pageSize));
    }

    @GetMapping("/spray-tasks")
    public ApiResponse<PageResult<SprayTaskView>> sprayTasks(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.sprayTasks(name, enabled, page, pageSize));
    }

    @PostMapping("/spray-tasks")
    public ApiResponse<Map<String, Long>> createSprayTask(@Valid @RequestBody SprayTaskRequest request) {
        return ApiResponse.ok(Map.of("id", platformPageService.createSprayTask(request)));
    }

    @PutMapping("/spray-tasks/{id}")
    public ApiResponse<Void> updateSprayTask(@PathVariable long id, @Valid @RequestBody SprayTaskRequest request) {
        platformPageService.updateSprayTask(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/spray-tasks/{id}")
    public ApiResponse<Void> deleteSprayTask(@PathVariable long id) {
        platformPageService.deleteSprayTask(id);
        return ApiResponse.ok();
    }

    @GetMapping("/spray-records")
    public ApiResponse<PageResult<SprayRecordView>> sprayRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.sprayRecords(page, pageSize));
    }

    @GetMapping("/alarms")
    public ApiResponse<PageResult<AlarmView>> alarms(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String alarmLevel,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.environmentAlarms(
                level == null || level.isBlank() ? alarmLevel : level,
                status,
                startTime,
                endTime,
                page,
                pageSize
        ));
    }
}
