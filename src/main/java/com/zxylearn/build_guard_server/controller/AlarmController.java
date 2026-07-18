package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.dto.PageDtos.AlarmHandleRequest;
import com.zxylearn.build_guard_server.service.PlatformPageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {
    private final PlatformPageService platformPageService;

    public AlarmController(PlatformPageService platformPageService) {
        this.platformPageService = platformPageService;
    }

    @PostMapping("/{id}/handle")
    public ApiResponse<Void> handleAlarm(@PathVariable long id, @Valid @RequestBody AlarmHandleRequest request) {
        platformPageService.handleAlarm(id, request);
        return ApiResponse.ok();
    }
}
