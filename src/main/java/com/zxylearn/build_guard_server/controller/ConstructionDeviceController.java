package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PageDtos.AlarmView;
import com.zxylearn.build_guard_server.dto.PageDtos.DeviceMonitorView;
import com.zxylearn.build_guard_server.dto.PageDtos.DeviceOverview;
import com.zxylearn.build_guard_server.dto.PageDtos.OnlineRecordView;
import com.zxylearn.build_guard_server.service.PlatformPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/construction-devices")
public class ConstructionDeviceController {
    private final PlatformPageService platformPageService;

    public ConstructionDeviceController(PlatformPageService platformPageService) {
        this.platformPageService = platformPageService;
    }

    @GetMapping("/overview")
    public ApiResponse<DeviceOverview> overview() {
        return ApiResponse.ok(platformPageService.deviceOverview());
    }

    @GetMapping("/tower-cranes")
    public ApiResponse<PageResult<DeviceMonitorView>> towerCranes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.listConstructionMonitors("tower_crane", page, pageSize));
    }

    @GetMapping("/elevators")
    public ApiResponse<PageResult<DeviceMonitorView>> elevators(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.listConstructionMonitors("elevator", page, pageSize));
    }

    @GetMapping("/formworks")
    public ApiResponse<PageResult<DeviceMonitorView>> formworks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.listConstructionMonitors("formwork", page, pageSize));
    }

    @GetMapping("/deep-pits")
    public ApiResponse<PageResult<DeviceMonitorView>> deepPits(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.listConstructionMonitors("deep_pit", page, pageSize));
    }

    @GetMapping("/online-records")
    public ApiResponse<PageResult<OnlineRecordView>> onlineRecords(
            @RequestParam(required = false) String deviceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.onlineRecords(deviceCode, page, pageSize));
    }

    @GetMapping("/alarms")
    public ApiResponse<PageResult<AlarmView>> alarms(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String alarmLevel,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.deviceAlarms(
                level == null || level.isBlank() ? alarmLevel : level,
                alarmType,
                deviceName,
                status,
                startTime,
                endTime,
                page,
                pageSize
        ));
    }
}
