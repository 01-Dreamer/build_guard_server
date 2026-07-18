package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PageDtos.MonitorPointRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.MonitorPointView;
import com.zxylearn.build_guard_server.dto.PageDtos.MonitorRuleRequest;
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
@RequestMapping("/api/monitor-config")
public class MonitorConfigController {
    private final PlatformPageService platformPageService;

    public MonitorConfigController(PlatformPageService platformPageService) {
        this.platformPageService = platformPageService;
    }

    @GetMapping("/device-points")
    public ApiResponse<PageResult<MonitorPointView>> devicePoints(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.monitorPoints(false, name, type, page, pageSize));
    }

    @GetMapping("/environment-points")
    public ApiResponse<PageResult<MonitorPointView>> environmentPoints(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.monitorPoints(true, name, null, page, pageSize));
    }

    @PostMapping("/points")
    public ApiResponse<Map<String, Long>> createPoint(@Valid @RequestBody MonitorPointRequest request) {
        return ApiResponse.ok(Map.of("id", platformPageService.createMonitorPoint(request)));
    }

    @PutMapping("/points/{id}")
    public ApiResponse<Void> updatePoint(@PathVariable long id, @Valid @RequestBody MonitorPointRequest request) {
        platformPageService.updateMonitorPoint(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/points/{id}/rule")
    public ApiResponse<Void> updateRule(@PathVariable long id, @RequestBody MonitorRuleRequest request) {
        platformPageService.updateMonitorRule(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/points/{id}")
    public ApiResponse<Void> deletePoint(@PathVariable long id) {
        platformPageService.deleteMonitorPoint(id);
        return ApiResponse.ok();
    }
}
