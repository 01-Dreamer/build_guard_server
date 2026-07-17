package com.zxylearn.build_guard_server.dto;

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
}
