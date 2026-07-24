package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PageDtos.AiRiskView;
import com.zxylearn.build_guard_server.dto.PageDtos.AiRiskReviewRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.CameraView;
import com.zxylearn.build_guard_server.dto.PageDtos.CameraVideoView;
import com.zxylearn.build_guard_server.entity.DeviceAsset;
import com.zxylearn.build_guard_server.entity.FileResource;
import com.zxylearn.build_guard_server.mapper.DeviceAssetMapper;
import com.zxylearn.build_guard_server.service.FileStorageService;
import com.zxylearn.build_guard_server.service.PlatformPageService;
import com.zxylearn.build_guard_server.service.PlatformPageService.CameraSnapshot;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

@RestController
@RequestMapping("/api/site")
public class SiteController {
    private final PlatformPageService platformPageService;
    private final DeviceAssetMapper deviceAssetMapper;
    private final FileStorageService fileStorageService;

    public SiteController(PlatformPageService platformPageService,
                          DeviceAssetMapper deviceAssetMapper,
                          FileStorageService fileStorageService) {
        this.platformPageService = platformPageService;
        this.deviceAssetMapper = deviceAssetMapper;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/cameras")
    public ApiResponse<PageResult<CameraView>> cameras(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.cameras(page, pageSize));
    }

    @GetMapping("/cameras/{code}/snapshot")
    public ResponseEntity<Resource> cameraSnapshot(@PathVariable String code) {
        Optional<CameraSnapshot> snapshot = platformPageService.cameraSnapshot(code);
        if (snapshot.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(snapshot.get().contentType()))
                .body(new FileSystemResource(snapshot.get().path()));
    }

    @GetMapping("/camera-videos")
    public ApiResponse<PageResult<CameraVideoView>> cameraVideos(
            @RequestParam(required = false) String cameraCode,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.cameraVideos(cameraCode, startTime, endTime, page, pageSize));
    }

    @PostMapping(value = "/cameras/{code}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileResource> uploadCameraVideo(@PathVariable String code, @RequestParam("file") MultipartFile file) {
        DeviceAsset camera = deviceAssetMapper.selectOne(Wrappers.<DeviceAsset>lambdaQuery()
                .eq(DeviceAsset::getCode, code)
                .last("limit 1"));
        if (camera == null) {
            throw new BusinessException(404, "摄像头不存在");
        }
        return ApiResponse.ok(fileStorageService.saveMultipart(file, "camera_video", camera.getId(), "camera/video/" + camera.getCode()));
    }

    @GetMapping("/cameras/{code}/stream")
    public void cameraStream(@PathVariable String code, HttpServletResponse response) {
        String boundary = "buildguard-frame";
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType("multipart/x-mixed-replace; boundary=" + boundary);
        try {
            writeCameraStream(code, boundary, response.getOutputStream());
        } catch (IOException exception) {
            // Client disconnected before the stream was established.
        }
    }

    private void writeCameraStream(String code, String boundary, OutputStream outputStream) {
        String lastVersion = null;
        while (!Thread.currentThread().isInterrupted()) {
            Optional<CameraSnapshot> snapshot = platformPageService.cameraSnapshot(code);
            if (snapshot.isPresent() && !snapshot.get().version().equals(lastVersion)) {
                CameraSnapshot current = snapshot.get();
                try {
                    byte[] frame = Files.readAllBytes(current.path());
                    byte[] header = (
                            "--" + boundary + "\r\n" +
                                    "Content-Type: " + current.contentType() + "\r\n" +
                                    "Content-Length: " + frame.length + "\r\n" +
                                    "Cache-Control: no-store\r\n\r\n"
                    ).getBytes(StandardCharsets.UTF_8);
                    outputStream.write(header);
                    outputStream.write(frame);
                    outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    lastVersion = current.version();
                } catch (IOException exception) {
                    return;
                }
            }

            try {
                Thread.sleep(80);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @GetMapping("/ai-monitor-rules")
    public ApiResponse<PageResult<CameraView>> aiMonitorRules(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return cameras(page, pageSize);
    }

    @GetMapping("/ai-risks")
    public ApiResponse<PageResult<AiRiskView>> aiRisks(
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(platformPageService.aiRisks(deviceCode, alarmType, startTime, endTime, page, pageSize));
    }

    @PostMapping("/ai-risks/{id}/review")
    public ApiResponse<java.util.Map<String, Long>> reviewAiRisk(
            @PathVariable long id,
            @RequestBody AiRiskReviewRequest request
    ) {
        Long violationId = platformPageService.reviewAiRisk(id, request);
        return ApiResponse.ok(violationId == null ? java.util.Map.of() : java.util.Map.of("violationId", violationId));
    }
}
