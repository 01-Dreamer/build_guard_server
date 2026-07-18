package com.zxylearn.build_guard_server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceLocationRequest;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceLocationView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceRequest;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceTypeRequest;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceTypeView;
import com.zxylearn.build_guard_server.dto.DeviceDtos.DeviceView;
import com.zxylearn.build_guard_server.entity.DeviceAsset;
import com.zxylearn.build_guard_server.entity.DeviceLocation;
import com.zxylearn.build_guard_server.entity.DeviceType;
import com.zxylearn.build_guard_server.mapper.DeviceAssetMapper;
import com.zxylearn.build_guard_server.mapper.DeviceLocationMapper;
import com.zxylearn.build_guard_server.mapper.DeviceTypeMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DeviceService {
    private final DeviceAssetMapper deviceAssetMapper;
    private final DeviceTypeMapper deviceTypeMapper;
    private final DeviceLocationMapper deviceLocationMapper;

    public DeviceService(DeviceAssetMapper deviceAssetMapper,
                         DeviceTypeMapper deviceTypeMapper,
                         DeviceLocationMapper deviceLocationMapper) {
        this.deviceAssetMapper = deviceAssetMapper;
        this.deviceTypeMapper = deviceTypeMapper;
        this.deviceLocationMapper = deviceLocationMapper;
    }

    public PageResult<DeviceView> listDevices(String name, Long typeId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        IPage<DeviceAsset> result = deviceAssetMapper.selectPage(
                Page.of(safePage, safePageSize),
                Wrappers.<DeviceAsset>lambdaQuery()
                        .like(name != null && !name.isBlank(), DeviceAsset::getName, name == null ? null : name.trim())
                        .eq(typeId != null, DeviceAsset::getTypeId, typeId)
                        .orderByDesc(DeviceAsset::getId)
        );

        Map<Long, DeviceType> typeMap = deviceTypeMapper.selectList(null).stream()
                .collect(Collectors.toMap(DeviceType::getId, Function.identity(), (left, right) -> left));
        Map<Long, DeviceLocation> locationMap = deviceLocationMapper.selectList(null).stream()
                .collect(Collectors.toMap(DeviceLocation::getId, Function.identity(), (left, right) -> left));

        List<DeviceView> records = result.getRecords().stream()
                .map(device -> {
                    DeviceType type = device.getTypeId() == null ? null : typeMap.get(device.getTypeId());
                    DeviceLocation location = device.getLocationId() == null ? null : locationMap.get(device.getLocationId());
                    return new DeviceView(
                            device.getId(),
                            device.getName(),
                            device.getCode(),
                            device.getTypeId(),
                            type == null ? null : type.getName(),
                            device.getLocationId(),
                            location == null ? null : location.getName(),
                            device.getModel(),
                            device.getManufacturer(),
                            device.getInstallDate(),
                            device.getOnlineStatus(),
                            device.getEnabled(),
                            device.getX(),
                            device.getY()
                    );
                })
                .toList();

        return new PageResult<>(records, result.getTotal(), safePage, safePageSize);
    }

    public List<DeviceTypeView> listTypes() {
        return deviceTypeMapper.selectList(Wrappers.<DeviceType>lambdaQuery().orderByAsc(DeviceType::getSort, DeviceType::getId))
                .stream()
                .map(type -> new DeviceTypeView(
                        type.getId(),
                        type.getParentId(),
                        type.getName(),
                        type.getCode(),
                        type.getSort(),
                        type.getStatus()
                ))
                .toList();
    }

    public List<DeviceLocationView> listLocations() {
        return deviceLocationMapper.selectList(Wrappers.<DeviceLocation>lambdaQuery().orderByAsc(DeviceLocation::getSort, DeviceLocation::getId))
                .stream()
                .map(location -> new DeviceLocationView(
                        location.getId(),
                        location.getAreaId(),
                        location.getName(),
                        location.getCode(),
                        location.getSort(),
                        location.getStatus()
                ))
                .toList();
    }

    public Long createDevice(DeviceRequest request) {
        DeviceAsset device = new DeviceAsset();
        applyDeviceRequest(device, request);
        device.setOnlineStatus(request.onlineStatus() == null ? 0 : request.onlineStatus());
        device.setEnabled(request.enabled() == null ? 1 : request.enabled());
        device.setCreatedAt(LocalDateTime.now());
        deviceAssetMapper.insert(device);
        return device.getId();
    }

    public void updateDevice(long id, DeviceRequest request) {
        DeviceAsset device = new DeviceAsset();
        device.setId(id);
        applyDeviceRequest(device, request);
        device.setOnlineStatus(request.onlineStatus() == null ? 0 : request.onlineStatus());
        device.setEnabled(request.enabled() == null ? 1 : request.enabled());
        device.setUpdatedAt(LocalDateTime.now());
        deviceAssetMapper.updateById(device);
    }

    public void deleteDevice(long id) {
        deviceAssetMapper.deleteById(id);
    }

    public void updateDeviceEnabled(long id, Integer enabled) {
        DeviceAsset device = new DeviceAsset();
        device.setId(id);
        device.setEnabled(enabled == null ? 1 : enabled);
        device.setUpdatedAt(LocalDateTime.now());
        deviceAssetMapper.updateById(device);
    }

    public Long createType(DeviceTypeRequest request) {
        DeviceType type = new DeviceType();
        applyTypeRequest(type, request);
        deviceTypeMapper.insert(type);
        return type.getId();
    }

    public void updateType(long id, DeviceTypeRequest request) {
        DeviceType type = new DeviceType();
        type.setId(id);
        applyTypeRequest(type, request);
        deviceTypeMapper.updateById(type);
    }

    public void deleteType(long id) {
        Long usedCount = deviceAssetMapper.selectCount(
                Wrappers.<DeviceAsset>lambdaQuery().eq(DeviceAsset::getTypeId, id)
        );
        if (usedCount > 0) {
            throw new BusinessException(400, "该设备类型已被设备引用，不能删除");
        }
        deviceTypeMapper.deleteById(id);
    }

    public Long createLocation(DeviceLocationRequest request) {
        DeviceLocation location = new DeviceLocation();
        applyLocationRequest(location, request);
        deviceLocationMapper.insert(location);
        return location.getId();
    }

    public void updateLocation(long id, DeviceLocationRequest request) {
        DeviceLocation location = new DeviceLocation();
        location.setId(id);
        applyLocationRequest(location, request);
        deviceLocationMapper.updateById(location);
    }

    public void deleteLocation(long id) {
        Long usedCount = deviceAssetMapper.selectCount(
                Wrappers.<DeviceAsset>lambdaQuery().eq(DeviceAsset::getLocationId, id)
        );
        if (usedCount > 0) {
            throw new BusinessException(400, "该设备位置已被设备引用，不能删除");
        }
        deviceLocationMapper.deleteById(id);
    }

    private void applyDeviceRequest(DeviceAsset device, DeviceRequest request) {
        device.setName(request.name());
        device.setCode(request.code());
        device.setTypeId(request.typeId());
        device.setLocationId(request.locationId());
        device.setModel(request.model());
        device.setManufacturer(request.manufacturer());
        device.setInstallDate(request.installDate());
        device.setX(request.x());
        device.setY(request.y());
    }

    private void applyTypeRequest(DeviceType type, DeviceTypeRequest request) {
        type.setParentId(request.parentId());
        type.setName(request.name());
        type.setCode(request.code());
        type.setSort(request.sort() == null ? 99 : request.sort());
        type.setStatus(request.status() == null ? 1 : request.status());
    }

    private void applyLocationRequest(DeviceLocation location, DeviceLocationRequest request) {
        location.setAreaId(request.areaId());
        location.setName(request.name());
        location.setCode(request.code());
        location.setSort(request.sort() == null ? 99 : request.sort());
        location.setStatus(request.status() == null ? 1 : request.status());
    }
}
