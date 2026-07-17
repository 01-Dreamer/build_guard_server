package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.ApiResponse;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.PersonnelView;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationRequest;
import com.zxylearn.build_guard_server.dto.PersonnelDtos.ViolationView;
import com.zxylearn.build_guard_server.service.PersonnelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
}
