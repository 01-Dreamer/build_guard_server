package com.zxylearn.build_guard_server.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public final class DeviceDtos {
    private DeviceDtos() {
    }

    public record DeviceView(
            Long id,
            String name,
            String code,
            Long typeId,
            String typeName,
            Long locationId,
            String locationName,
            String model,
            String manufacturer,
            LocalDate installDate,
            Integer onlineStatus,
            Integer enabled,
            Double x,
            Double y
    ) {
    }

    public record DeviceTypeView(Long id, Long parentId, String name, String code, Integer sort, Integer status) {
    }

    public record DeviceLocationView(Long id, Long areaId, String name, String code, Integer sort, Integer status) {
    }

    public record DeviceRequest(
            @NotBlank String name,
            @NotBlank String code,
            Long typeId,
            Long locationId,
            String model,
            String manufacturer,
            LocalDate installDate,
            Integer onlineStatus,
            Integer enabled,
            Double x,
            Double y
    ) {
    }

    public record DeviceTypeRequest(
            Long parentId,
            @NotBlank String name,
            @NotBlank String code,
            Integer sort,
            Integer status
    ) {
    }

    public record DeviceLocationRequest(
            Long areaId,
            @NotBlank String name,
            @NotBlank String code,
            Integer sort,
            Integer status
    ) {
    }

    public record DeviceEnabledRequest(Integer enabled) {
    }
}
