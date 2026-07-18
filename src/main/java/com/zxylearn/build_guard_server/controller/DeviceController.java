package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceEnabledRequest;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceLocationRequest;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceLocationView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceRequest;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceTypeRequest;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceTypeView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceView;
import com.zxylearn.build_guard_server.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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

    @PostMapping
    public ApiResponse<Map<String, Long>> createDevice(@Valid @RequestBody DeviceRequest request) {
        return ApiResponse.ok(Map.of("id", deviceService.createDevice(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateDevice(@PathVariable long id, @Valid @RequestBody DeviceRequest request) {
        deviceService.updateDevice(id, request);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> updateEnabled(@PathVariable long id, @RequestBody DeviceEnabledRequest request) {
        deviceService.updateDeviceEnabled(id, request.enabled());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDevice(@PathVariable long id) {
        deviceService.deleteDevice(id);
        return ApiResponse.ok();
    }

    @PostMapping("/types")
    public ApiResponse<Map<String, Long>> createType(@Valid @RequestBody DeviceTypeRequest request) {
        return ApiResponse.ok(Map.of("id", deviceService.createType(request)));
    }

    @PutMapping("/types/{id}")
    public ApiResponse<Void> updateType(@PathVariable long id, @Valid @RequestBody DeviceTypeRequest request) {
        deviceService.updateType(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/types/{id}")
    public ApiResponse<Void> deleteType(@PathVariable long id) {
        deviceService.deleteType(id);
        return ApiResponse.ok();
    }

    @PostMapping("/locations")
    public ApiResponse<Map<String, Long>> createLocation(@Valid @RequestBody DeviceLocationRequest request) {
        return ApiResponse.ok(Map.of("id", deviceService.createLocation(request)));
    }

    @PutMapping("/locations/{id}")
    public ApiResponse<Void> updateLocation(@PathVariable long id, @Valid @RequestBody DeviceLocationRequest request) {
        deviceService.updateLocation(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/locations/{id}")
    public ApiResponse<Void> deleteLocation(@PathVariable long id) {
        deviceService.deleteLocation(id);
        return ApiResponse.ok();
    }
}
