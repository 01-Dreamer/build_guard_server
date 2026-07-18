package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.dto.PageDtos.DashboardOverview;
import com.zxylearn.build_guard_server.service.PlatformPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final PlatformPageService platformPageService;

    public DashboardController(PlatformPageService platformPageService) {
        this.platformPageService = platformPageService;
    }

    @GetMapping
    public ApiResponse<DashboardOverview> overview() {
        return ApiResponse.ok(platformPageService.dashboardOverview());
    }

    @GetMapping("/overview")
    public ApiResponse<DashboardOverview> overviewAlias() {
        return overview();
    }
}
