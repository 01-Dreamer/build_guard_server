package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PageDtos.AiRiskView;
import com.zxylearn.build_guard_server.service.PlatformPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site-ai")
public class SiteAiController {
    private final PlatformPageService platformPageService;

    public SiteAiController(PlatformPageService platformPageService) {
        this.platformPageService = platformPageService;
    }

    @GetMapping("/risks")
    public ApiResponse<PageResult<AiRiskView>> risks(
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.aiRisks(deviceCode, alarmType, startTime, endTime, page, pageSize));
    }
}
