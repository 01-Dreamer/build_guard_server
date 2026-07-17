package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceLocationView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceTypeView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceView;
import com.zxylearn.build_guard_server.service.DeviceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public ApiResponse<PageResult<DeviceView>> listDevices(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long typeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(deviceService.listDevices(name, typeId, page, pageSize));
    }

    @GetMapping("/types")
    public ApiResponse<List<DeviceTypeView>> listTypes() {
        return ApiResponse.ok(deviceService.listTypes());
    }

    @GetMapping("/locations")
    public ApiResponse<List<DeviceLocationView>> listLocations() {
        return ApiResponse.ok(deviceService.listLocations());
    }
}
