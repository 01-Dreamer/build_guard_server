package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelView;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationFineRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationReviewRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationView;
import com.zxylearn.build_guard_server.service.PersonnelService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {
    private final PersonnelService personnelService;

    public PersonnelController(PersonnelService personnelService) {
        this.personnelService = personnelService;
    }

    @GetMapping
    public ApiResponse<PageResult<PersonnelView>> listPersonnel(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(personnelService.listPersonnel(name, page, pageSize));
    }

    @PostMapping
    public ApiResponse<Map<String, Long>> createPersonnel(@Valid @RequestBody PersonnelRequest request) {
        return ApiResponse.ok(Map.of("id", personnelService.createPersonnel(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updatePersonnel(@PathVariable long id, @Valid @RequestBody PersonnelRequest request) {
        personnelService.updatePersonnel(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePersonnel(@PathVariable long id) {
        personnelService.deletePersonnel(id);
        return ApiResponse.ok();
    }

    @PostMapping(value = "/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PersonnelView> uploadAvatar(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(personnelService.updateAvatar(id, file));
    }

    @GetMapping("/violations")
    public ApiResponse<PageResult<ViolationView>> listViolations(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.ok(personnelService.listViolations(name, page, pageSize));
    }

    @PostMapping("/violations")
    public ApiResponse<Map<String, Long>> createViolation(@Valid @RequestBody ViolationRequest request) {
        return ApiResponse.ok(Map.of("id", personnelService.createViolation(request)));
    }

    @PutMapping("/violations/{id}/review")
    public ApiResponse<Void> reviewViolation(@PathVariable long id, @RequestBody ViolationReviewRequest request) {
        personnelService.reviewViolation(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/violations/{id}/fine")
    public ApiResponse<Void> updateViolationFine(@PathVariable long id, @RequestBody ViolationFineRequest request) {
        personnelService.updateViolationFine(id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/violations/{id}/remind")
    public ApiResponse<Void> remindViolationFine(@PathVariable long id) {
        personnelService.remindViolationFine(id);
        return ApiResponse.ok();
    }
}
