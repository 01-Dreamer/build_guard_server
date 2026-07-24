package com.zxylearn.build_guard_server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.common.PageResult;
import com.zxylearn.build_guard_server.dto.PageDtos.AiRiskView;
import com.zxylearn.build_guard_server.dto.PageDtos.AiRiskReviewRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.AlarmHandleRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.AlarmView;
import com.zxylearn.build_guard_server.dto.PageDtos.CameraView;
import com.zxylearn.build_guard_server.dto.PageDtos.CameraVideoView;
import com.zxylearn.build_guard_server.dto.PageDtos.ChartPoint;
import com.zxylearn.build_guard_server.dto.PageDtos.DashboardOverview;
import com.zxylearn.build_guard_server.dto.PageDtos.DeviceCard;
import com.zxylearn.build_guard_server.dto.PageDtos.DeviceMonitorView;
import com.zxylearn.build_guard_server.dto.PageDtos.DeviceOverview;
import com.zxylearn.build_guard_server.dto.PageDtos.EnvironmentRealtimeView;
import com.zxylearn.build_guard_server.dto.PageDtos.MetricValue;
import com.zxylearn.build_guard_server.dto.PageDtos.MonitorPointRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.MonitorPointView;
import com.zxylearn.build_guard_server.dto.PageDtos.MonitorRuleRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.OnlineRecordView;
import com.zxylearn.build_guard_server.dto.PageDtos.SprayRecordView;
import com.zxylearn.build_guard_server.dto.PageDtos.SprayTaskRequest;
import com.zxylearn.build_guard_server.dto.PageDtos.SprayTaskView;
import com.zxylearn.build_guard_server.dto.PageDtos.WorkStatView;
import com.zxylearn.build_guard_server.entity.AiDetectionRecord;
import com.zxylearn.build_guard_server.entity.AlarmHandleRecord;
import com.zxylearn.build_guard_server.entity.AlarmRecord;
import com.zxylearn.build_guard_server.entity.CameraDeviceProfile;
import com.zxylearn.build_guard_server.entity.DeviceAsset;
import com.zxylearn.build_guard_server.entity.DeviceLocation;
import com.zxylearn.build_guard_server.entity.DeviceOnlineRecord;
import com.zxylearn.build_guard_server.entity.DeviceType;
import com.zxylearn.build_guard_server.entity.FileResource;
import com.zxylearn.build_guard_server.entity.MonitorPoint;
import com.zxylearn.build_guard_server.entity.MonitorRule;
import com.zxylearn.build_guard_server.entity.Personnel;
import com.zxylearn.build_guard_server.entity.SprayRecord;
import com.zxylearn.build_guard_server.entity.SprayTask;
import com.zxylearn.build_guard_server.entity.TowerWorkRecord;
import com.zxylearn.build_guard_server.entity.ViolationRecord;
import com.zxylearn.build_guard_server.mapper.AiDetectionRecordMapper;
import com.zxylearn.build_guard_server.mapper.AlarmHandleRecordMapper;
import com.zxylearn.build_guard_server.mapper.AlarmRecordMapper;
import com.zxylearn.build_guard_server.mapper.CameraDeviceProfileMapper;
import com.zxylearn.build_guard_server.mapper.DeviceAssetMapper;
import com.zxylearn.build_guard_server.mapper.DeviceLocationMapper;
import com.zxylearn.build_guard_server.mapper.DeviceOnlineRecordMapper;
import com.zxylearn.build_guard_server.mapper.DeviceTypeMapper;
import com.zxylearn.build_guard_server.mapper.FileResourceMapper;
import com.zxylearn.build_guard_server.mapper.MonitorPointMapper;
import com.zxylearn.build_guard_server.mapper.MonitorRuleMapper;
import com.zxylearn.build_guard_server.mapper.PersonnelMapper;
import com.zxylearn.build_guard_server.mapper.SprayRecordMapper;
import com.zxylearn.build_guard_server.mapper.SprayTaskMapper;
import com.zxylearn.build_guard_server.mapper.TowerWorkRecordMapper;
import com.zxylearn.build_guard_server.mapper.ViolationRecordMapper;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlatformPageService {
    private static final String CAMERA_FRAME_COLLECTION = "camera_frame_event";
    private static final Map<String, String> TELEMETRY_COLLECTIONS = Map.of(
            "tower_crane", "tower_crane_telemetry",
            "elevator", "elevator_telemetry",
            "formwork", "formwork_telemetry",
            "deep_pit", "deep_pit_telemetry",
            "environment_sensor", "environment_telemetry"
    );

    private final DeviceAssetMapper deviceAssetMapper;
    private final DeviceTypeMapper deviceTypeMapper;
    private final DeviceLocationMapper deviceLocationMapper;
    private final DeviceOnlineRecordMapper deviceOnlineRecordMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final AlarmHandleRecordMapper alarmHandleRecordMapper;
    private final MonitorPointMapper monitorPointMapper;
    private final MonitorRuleMapper monitorRuleMapper;
    private final SprayTaskMapper sprayTaskMapper;
    private final SprayRecordMapper sprayRecordMapper;
    private final AiDetectionRecordMapper aiDetectionRecordMapper;
    private final PersonnelMapper personnelMapper;
    private final TowerWorkRecordMapper towerWorkRecordMapper;
    private final CameraDeviceProfileMapper cameraDeviceProfileMapper;
    private final FileResourceMapper fileResourceMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final MongoTemplate mongoTemplate;

    public PlatformPageService(DeviceAssetMapper deviceAssetMapper,
                               DeviceTypeMapper deviceTypeMapper,
                               DeviceLocationMapper deviceLocationMapper,
                               DeviceOnlineRecordMapper deviceOnlineRecordMapper,
                               AlarmRecordMapper alarmRecordMapper,
                               AlarmHandleRecordMapper alarmHandleRecordMapper,
                               MonitorPointMapper monitorPointMapper,
                               MonitorRuleMapper monitorRuleMapper,
                               SprayTaskMapper sprayTaskMapper,
                               SprayRecordMapper sprayRecordMapper,
                               AiDetectionRecordMapper aiDetectionRecordMapper,
                               PersonnelMapper personnelMapper,
                               TowerWorkRecordMapper towerWorkRecordMapper,
                               CameraDeviceProfileMapper cameraDeviceProfileMapper,
                               FileResourceMapper fileResourceMapper,
                               ViolationRecordMapper violationRecordMapper,
                               MongoTemplate mongoTemplate) {
        this.deviceAssetMapper = deviceAssetMapper;
        this.deviceTypeMapper = deviceTypeMapper;
        this.deviceLocationMapper = deviceLocationMapper;
        this.deviceOnlineRecordMapper = deviceOnlineRecordMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.alarmHandleRecordMapper = alarmHandleRecordMapper;
        this.monitorPointMapper = monitorPointMapper;
        this.monitorRuleMapper = monitorRuleMapper;
        this.sprayTaskMapper = sprayTaskMapper;
        this.sprayRecordMapper = sprayRecordMapper;
        this.aiDetectionRecordMapper = aiDetectionRecordMapper;
        this.personnelMapper = personnelMapper;
        this.towerWorkRecordMapper = towerWorkRecordMapper;
        this.cameraDeviceProfileMapper = cameraDeviceProfileMapper;
        this.fileResourceMapper = fileResourceMapper;
        this.violationRecordMapper = violationRecordMapper;
        this.mongoTemplate = mongoTemplate;
    }

    public DashboardOverview dashboardOverview() {
        List<DeviceAsset> devices = allDevices();
        long online = devices.stream().filter(device -> Integer.valueOf(1).equals(device.getOnlineStatus())).count();
        Integer openAlarms = Math.toIntExact(alarmRecordMapper.selectCount(
                Wrappers.<AlarmRecord>lambdaQuery().ne(AlarmRecord::getStatus, 2)
        ));
        Integer aiRisks = Math.toIntExact(aiDetectionRecordMapper.selectCount(null));
        DeviceAsset environmentDevice = firstDeviceByType("environment_sensor");
        List<MetricValue> environment = environmentDevice == null
                ? metricValues("environment_sensor", demoMetrics("environment_sensor", "ENV-DEMO", demoTick()))
                : realtimeMetrics(environmentDevice);

        return new DashboardOverview(
                devices.size(),
                Math.toIntExact(online),
                openAlarms,
                aiRisks,
                environment,
                alarmTrend(),
                latestAlarms(5)
        );
    }

    public DeviceOverview deviceOverview() {
        List<DeviceAsset> devices = allDevices();
        Map<Long, DeviceType> typeMap = typeMap();
        List<DeviceCard> cards = devices.stream()
                .filter(device -> isConstructionDevice(typeMap.get(device.getTypeId())))
                .map(device -> deviceCard(device, typeMap))
                .toList();
        return new DeviceOverview(
                cards.size(),
                Math.toIntExact(cards.stream().filter(card -> Integer.valueOf(1).equals(card.onlineStatus())).count()),
                countByType(devices, typeMap, "tower_crane"),
                countByType(devices, typeMap, "elevator"),
                countByType(devices, typeMap, "formwork"),
                countByType(devices, typeMap, "deep_pit"),
                cards,
                listAlarms(null, List.of("device", "tower_crane", "elevator", "formwork", "deep_pit"), null, null, null, null, null, 1, 5).records()
        );
    }

    public PageResult<DeviceMonitorView> listConstructionMonitors(String typeCode, int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceType> typeMap = typeMap();
        List<DeviceAsset> filtered = allDevices().stream()
                .filter(device -> {
                    DeviceType type = typeMap.get(device.getTypeId());
                    return type != null && typeCode.equals(type.getCode());
                })
                .sorted(Comparator.comparing(DeviceAsset::getId).reversed())
                .toList();
        int from = Math.min((safePage - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        List<DeviceMonitorView> records = filtered.subList(from, to).stream()
                .map(device -> deviceMonitor(device, typeMap))
                .toList();
        return new PageResult<>(records, filtered.size(), safePage, safePageSize);
    }

    public PageResult<OnlineRecordView> onlineRecords(String deviceCode, int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        List<Long> deviceIds = deviceCode == null || deviceCode.isBlank() ? List.of() : idsByCode(deviceCode);
        if (deviceCode != null && !deviceCode.isBlank() && deviceIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, safePage, safePageSize);
        }
        IPage<DeviceOnlineRecord> result = deviceOnlineRecordMapper.selectPage(
                Page.of(safePage, safePageSize),
                Wrappers.<DeviceOnlineRecord>lambdaQuery()
                        .in(!deviceIds.isEmpty(), DeviceOnlineRecord::getDeviceId, deviceIds)
                        .orderByDesc(DeviceOnlineRecord::getReportedAt)
        );
        List<OnlineRecordView> records = result.getRecords().stream()
                .map(record -> {
                    DeviceAsset device = deviceMap.get(record.getDeviceId());
                    return new OnlineRecordView(
                            record.getId(),
                            record.getDeviceId(),
                            device == null ? null : device.getName(),
                            device == null ? null : device.getCode(),
                            record.getOnlineStatus(),
                            record.getReportedAt(),
                            record.getSource()
                    );
                })
                .toList();
        return new PageResult<>(records, result.getTotal(), safePage, safePageSize);
    }

    public PageResult<AlarmView> deviceAlarms(String level,
                                              String alarmType,
                                              String deviceName,
                                              Integer status,
                                              String startTime,
                                              String endTime,
                                              int page,
                                              int pageSize) {
        return listAlarms(level, List.of("device", "tower_crane", "elevator", "formwork", "deep_pit"), alarmType, deviceName, status, startTime, endTime, page, pageSize);
    }

    public EnvironmentRealtimeView environmentRealtime(String deviceCode) {
        DeviceAsset device = deviceByCodeOrFirst(deviceCode, "environment_sensor");
        if (device == null) {
            List<ChartPoint> trend = demoHistory("environment_sensor", "ENV-DEMO", 24);
            return new EnvironmentRealtimeView("ENV-DEMO", "Environment Demo", metricValues("environment_sensor", trend.getLast().values()), trend);
        }
        return new EnvironmentRealtimeView(device.getCode(), device.getName(), realtimeMetrics(device), telemetryHistory(device, 24));
    }

    public PageResult<ChartPoint> environmentHistory(String deviceCode, int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        DeviceAsset device = deviceByCodeOrFirst(deviceCode, "environment_sensor");
        List<ChartPoint> history = device == null
                ? demoHistory("environment_sensor", "ENV-DEMO", 72)
                : telemetryHistory(device, 72);
        int from = Math.min((safePage - 1) * safePageSize, history.size());
        int to = Math.min(from + safePageSize, history.size());
        return new PageResult<>(history.subList(from, to), history.size(), safePage, safePageSize);
    }

    public PageResult<SprayTaskView> sprayTasks(String name, Integer enabled, int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        IPage<SprayTask> result = sprayTaskMapper.selectPage(
                Page.of(safePage, safePageSize),
                Wrappers.<SprayTask>lambdaQuery()
                        .like(name != null && !name.isBlank(), SprayTask::getName, trim(name))
                        .eq(enabled != null, SprayTask::getEnabled, enabled)
                        .orderByAsc(SprayTask::getNextRunAt, SprayTask::getId)
        );
        List<SprayTaskView> records = result.getRecords().stream()
                .map(task -> {
                    DeviceAsset device = deviceMap.get(task.getSprayDeviceId());
                    return new SprayTaskView(
                            task.getId(),
                            task.getName(),
                            task.getSprayDeviceId(),
                            device == null ? null : device.getName(),
                            task.getStartTime(),
                            task.getDurationMinutes(),
                            task.getCycleValue(),
                            task.getCycleUnit(),
                            task.getEnabled(),
                            task.getNextRunAt()
                    );
                })
                .toList();
        return new PageResult<>(records, result.getTotal(), safePage, safePageSize);
    }

    public Long createSprayTask(SprayTaskRequest request) {
        SprayTask task = new SprayTask();
        applySprayTask(task, request);
        sprayTaskMapper.insert(task);
        return task.getId();
    }

    public void updateSprayTask(long id, SprayTaskRequest request) {
        SprayTask task = sprayTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "喷淋任务不存在");
        }
        applySprayTask(task, request);
        sprayTaskMapper.updateById(task);
    }

    public void deleteSprayTask(long id) {
        sprayTaskMapper.deleteById(id);
    }

    private void applySprayTask(SprayTask task, SprayTaskRequest request) {
        if (request.sprayDeviceId() == null || deviceAssetMapper.selectById(request.sprayDeviceId()) == null) {
            throw new BusinessException(400, "请选择有效喷淋设备");
        }
        if (request.startTime() == null) {
            throw new BusinessException(400, "请填写启动时间");
        }
        if (request.durationMinutes() == null || request.durationMinutes() <= 0) {
            throw new BusinessException(400, "喷淋时长必须大于0");
        }
        task.setName(trim(request.name()));
        task.setSprayDeviceId(request.sprayDeviceId());
        task.setStartTime(request.startTime());
        task.setDurationMinutes(request.durationMinutes());
        task.setCycleValue(request.cycleValue());
        task.setCycleUnit(trim(request.cycleUnit()));
        task.setEnabled(request.enabled() == null ? 1 : request.enabled());
        task.setNextRunAt(request.nextRunAt() == null ? request.startTime() : request.nextRunAt());
    }

    public PageResult<SprayRecordView> sprayRecords(int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        Map<Long, SprayTask> taskMap = sprayTaskMapper.selectList(null).stream()
                .collect(Collectors.toMap(SprayTask::getId, Function.identity(), (left, right) -> left));
        IPage<SprayRecord> result = sprayRecordMapper.selectPage(
                Page.of(safePage, safePageSize),
                Wrappers.<SprayRecord>lambdaQuery().orderByDesc(SprayRecord::getStartedAt)
        );
        List<SprayRecordView> records = result.getRecords().stream()
                .map(record -> {
                    DeviceAsset device = deviceMap.get(record.getSprayDeviceId());
                    SprayTask task = record.getTaskId() == null ? null : taskMap.get(record.getTaskId());
                    return new SprayRecordView(
                            record.getId(),
                            record.getSprayDeviceId(),
                            device == null ? null : device.getName(),
                            record.getTaskId(),
                            task == null ? null : task.getName(),
                            record.getOperationType(),
                            record.getTriggerType(),
                            record.getStartedAt(),
                            record.getEndedAt(),
                            record.getExecuteStatus()
                    );
                })
                .toList();
        return new PageResult<>(records, result.getTotal(), safePage, safePageSize);
    }

    public PageResult<AlarmView> environmentAlarms(String level,
                                                   Integer status,
                                                   String startTime,
                                                   String endTime,
                                                   int page,
                                                   int pageSize) {
        return listAlarms(level, List.of("environment"), null, null, status, startTime, endTime, page, pageSize);
    }

    public void handleAlarm(long alarmId, AlarmHandleRequest request) {
        AlarmRecord alarm = alarmRecordMapper.selectById(alarmId);
        if (alarm == null) {
            throw new BusinessException(404, "报警记录不存在");
        }

        alarm.setStatus(2);
        alarmRecordMapper.updateById(alarm);

        AlarmHandleRecord handle = new AlarmHandleRecord();
        handle.setAlarmId(alarmId);
        handle.setHandleBy(request.handleBy() == null || request.handleBy().isBlank() ? "系统管理员" : request.handleBy().trim());
        handle.setHandleContent(request.handleContent().trim());
        handle.setHandledAt(LocalDateTime.now());
        alarmHandleRecordMapper.insert(handle);
    }

    public PageResult<MonitorPointView> monitorPoints(boolean environmentOnly, String name, String typeKeyword, int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        Map<Long, DeviceType> typeMap = typeMap();
        Map<Long, MonitorRule> ruleMap = monitorRuleMapper.selectList(null).stream()
                .collect(Collectors.toMap(MonitorRule::getMonitorPointId, Function.identity(), (left, right) -> left));

        List<MonitorPoint> filtered = monitorPointMapper.selectList(
                        Wrappers.<MonitorPoint>lambdaQuery().orderByAsc(MonitorPoint::getSort, MonitorPoint::getId)
                )
                .stream()
                .filter(point -> {
                    DeviceAsset device = deviceMap.get(point.getDeviceId());
                    DeviceType type = device == null ? null : typeMap.get(device.getTypeId());
                    boolean isEnvironment = type != null && "environment_sensor".equals(type.getCode());
                    return environmentOnly == isEnvironment;
                })
                .filter(point -> monitorPointMatchesName(point, deviceMap.get(point.getDeviceId()), name))
                .filter(point -> monitorPointMatchesType(deviceMap.get(point.getDeviceId()), typeMap, typeKeyword))
                .toList();
        int from = Math.min((safePage - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        List<MonitorPointView> records = filtered.subList(from, to).stream()
                .map(point -> monitorPointView(point, deviceMap, typeMap, ruleMap))
                .toList();
        return new PageResult<>(records, filtered.size(), safePage, safePageSize);
    }

    public Long createMonitorPoint(MonitorPointRequest request) {
        ensureUniqueMonitorPointCode(null, request.code());
        MonitorPoint point = new MonitorPoint();
        applyMonitorPoint(point, request);
        monitorPointMapper.insert(point);
        upsertMonitorRule(point.getId(), request.warnUpper(), request.warnLower(), request.alarmUpper(), request.alarmLower(), request.ruleEnabled());
        return point.getId();
    }

    public void updateMonitorPoint(long id, MonitorPointRequest request) {
        MonitorPoint point = monitorPointMapper.selectById(id);
        if (point == null) {
            throw new BusinessException(404, "监测点不存在");
        }
        ensureUniqueMonitorPointCode(id, request.code());
        applyMonitorPoint(point, request);
        monitorPointMapper.updateById(point);
        upsertMonitorRule(id, request.warnUpper(), request.warnLower(), request.alarmUpper(), request.alarmLower(), request.ruleEnabled());
    }

    public void updateMonitorRule(long monitorPointId, MonitorRuleRequest request) {
        if (monitorPointMapper.selectById(monitorPointId) == null) {
            throw new BusinessException(404, "监测点不存在");
        }
        upsertMonitorRule(monitorPointId, request.warnUpper(), request.warnLower(), request.alarmUpper(), request.alarmLower(), request.ruleEnabled());
    }

    public void deleteMonitorPoint(long id) {
        monitorRuleMapper.delete(Wrappers.<MonitorRule>lambdaQuery().eq(MonitorRule::getMonitorPointId, id));
        monitorPointMapper.deleteById(id);
    }

    private void applyMonitorPoint(MonitorPoint point, MonitorPointRequest request) {
        point.setName(trim(request.name()));
        point.setCode(trim(request.code()));
        point.setDeviceId(request.deviceId());
        point.setMetricCode(trim(request.metricCode()));
        point.setMetricName(trim(request.metricName()));
        point.setUnit(trim(request.unit()));
        point.setSort(request.sort() == null ? 99 : request.sort());
        point.setStatus(request.status() == null ? 1 : request.status());
    }

    private void upsertMonitorRule(Long monitorPointId,
                                   BigDecimal warnUpper,
                                   BigDecimal warnLower,
                                   BigDecimal alarmUpper,
                                   BigDecimal alarmLower,
                                   Integer enabled) {
        MonitorRule rule = monitorRuleMapper.selectOne(
                Wrappers.<MonitorRule>lambdaQuery()
                        .eq(MonitorRule::getMonitorPointId, monitorPointId)
                        .last("LIMIT 1")
        );
        boolean create = rule == null;
        if (create) {
            rule = new MonitorRule();
            rule.setMonitorPointId(monitorPointId);
        }
        rule.setWarnUpper(warnUpper);
        rule.setWarnLower(warnLower);
        rule.setAlarmUpper(alarmUpper);
        rule.setAlarmLower(alarmLower);
        rule.setEnabled(enabled == null ? 1 : enabled);
        if (create) {
            monitorRuleMapper.insert(rule);
        } else {
            monitorRuleMapper.updateById(rule);
        }
    }

    private void ensureUniqueMonitorPointCode(Long currentId, String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        MonitorPoint existing = monitorPointMapper.selectOne(
                Wrappers.<MonitorPoint>lambdaQuery()
                        .eq(MonitorPoint::getCode, code.trim())
                        .last("LIMIT 1")
        );
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new BusinessException(400, "监测点编号已存在");
        }
    }

    private boolean monitorPointMatchesName(MonitorPoint point, DeviceAsset device, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return containsIgnoreCase(point.getName(), normalized)
                || containsIgnoreCase(point.getCode(), normalized)
                || containsIgnoreCase(point.getMetricName(), normalized)
                || containsIgnoreCase(point.getMetricCode(), normalized)
                || containsIgnoreCase(device == null ? null : device.getName(), normalized)
                || containsIgnoreCase(device == null ? null : device.getCode(), normalized);
    }

    private boolean monitorPointMatchesType(DeviceAsset device, Map<Long, DeviceType> typeMap, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        DeviceType deviceType = device == null ? null : typeMap.get(device.getTypeId());
        String normalized = keyword.trim().toLowerCase();
        return containsIgnoreCase(deviceType == null ? null : deviceType.getName(), normalized)
                || containsIgnoreCase(deviceType == null ? null : deviceType.getCode(), normalized);
    }

    private MonitorPointView monitorPointView(MonitorPoint point,
                                              Map<Long, DeviceAsset> deviceMap,
                                              Map<Long, DeviceType> typeMap,
                                              Map<Long, MonitorRule> ruleMap) {
        DeviceAsset device = deviceMap.get(point.getDeviceId());
        DeviceType deviceType = device == null ? null : typeMap.get(device.getTypeId());
        MonitorRule rule = ruleMap.get(point.getId());
        return new MonitorPointView(
                point.getId(),
                point.getName(),
                point.getCode(),
                point.getDeviceId(),
                device == null ? null : device.getName(),
                device == null ? null : device.getCode(),
                deviceType == null ? null : deviceType.getCode(),
                deviceType == null ? null : deviceType.getName(),
                point.getMetricCode(),
                point.getMetricName(),
                point.getUnit(),
                point.getSort(),
                point.getStatus(),
                doubleValue(rule == null ? null : rule.getWarnUpper()),
                doubleValue(rule == null ? null : rule.getWarnLower()),
                doubleValue(rule == null ? null : rule.getAlarmUpper()),
                doubleValue(rule == null ? null : rule.getAlarmLower()),
                rule == null ? null : rule.getEnabled()
        );
    }

    public PageResult<AiRiskView> aiRisks(String deviceCode, String alarmType, String startTime, String endTime, int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        Map<Long, Personnel> personnelMap = personnelMapper.selectList(null).stream()
                .collect(Collectors.toMap(Personnel::getId, Function.identity(), (left, right) -> left));
        Map<Long, AlarmHandleRecord> latestHandles = latestHandles();
        List<AiDetectionRecord> detections = aiDetectionRecordMapper.selectList(
                Wrappers.<AiDetectionRecord>lambdaQuery().orderByDesc(AiDetectionRecord::getOccurredAt)
        );
        Map<Long, AlarmRecord> alarmMap = detections.stream()
                .map(AiDetectionRecord::getSourceAlarmId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids -> ids.isEmpty()
                        ? Map.<Long, AlarmRecord>of()
                        : alarmRecordMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(AlarmRecord::getId, Function.identity(), (left, right) -> left))));
        Map<Long, FileResource> snapshotMap = detections.stream()
                .map(AiDetectionRecord::getSnapshotFileId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), ids -> ids.isEmpty()
                        ? Map.<Long, FileResource>of()
                        : fileResourceMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(FileResource::getId, Function.identity(), (left, right) -> left))));
        List<AiRiskView> filtered = detections
                .stream()
                .map(record -> {
                    DeviceAsset device = record.getCameraDeviceId() == null ? null : deviceMap.get(record.getCameraDeviceId());
                    Personnel personnel = record.getPersonnelId() == null ? null : personnelMap.get(record.getPersonnelId());
                    AlarmRecord alarm = record.getSourceAlarmId() == null ? null : alarmMap.get(record.getSourceAlarmId());
                    AlarmHandleRecord handle = alarm == null ? null : latestHandles.get(alarm.getId());
                    FileResource snapshot = record.getSnapshotFileId() == null ? null : snapshotMap.get(record.getSnapshotFileId());
                    return new AiRiskView(
                            record.getId(),
                            record.getTaskId(),
                            record.getCameraDeviceId(),
                            device == null ? null : device.getName(),
                            device == null ? null : device.getCode(),
                            record.getPersonnelId(),
                            personnel == null ? null : personnel.getName(),
                            record.getDetectType(),
                            doubleValue(record.getConfidence()),
                            record.getResultJson(),
                            record.getOccurredAt(),
                            snapshot == null ? null : snapshot.getUrl(),
                            alarm == null ? null : alarm.getId(),
                            alarm == null ? null : alarm.getAlarmLevel(),
                            alarm == null ? null : alarm.getStatus(),
                            alarm == null ? null : alarm.getContent(),
                            handle == null ? null : handle.getHandleBy(),
                            handle == null ? null : handle.getHandledAt(),
                            handle == null ? null : handle.getHandleContent()
                    );
                })
                .filter(record -> matchesDeviceCode(record.cameraCode(), deviceCode))
                .filter(record -> matchesAiType(record, alarmType))
                .filter(record -> inDateRange(record.occurredAt(), startTime, endTime))
                .toList();
        int from = Math.min((safePage - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        return new PageResult<>(filtered.subList(from, to), filtered.size(), safePage, safePageSize);
    }

    public Long reviewAiRisk(long detectionId, AiRiskReviewRequest request) {
        AiDetectionRecord detection = aiDetectionRecordMapper.selectById(detectionId);
        if (detection == null) {
            throw new BusinessException(404, "AI风险记录不存在");
        }
        AlarmRecord alarm = detection.getSourceAlarmId() == null ? null : alarmRecordMapper.selectById(detection.getSourceAlarmId());
        if (alarm == null) {
            alarm = createMissingAiAlarm(detection);
        }
        if (Integer.valueOf(2).equals(alarm.getStatus())) {
            ViolationRecord existing = violationRecordMapper.selectOne(Wrappers.<ViolationRecord>lambdaQuery()
                    .eq(ViolationRecord::getSourceAlarmId, alarm.getId())
                    .last("limit 1"));
            return existing == null ? null : existing.getId();
        }

        String decision = request.decision() == null || request.decision().isBlank() ? "approve" : request.decision().trim();
        boolean approved = !"reject".equalsIgnoreCase(decision);
        ViolationRecord violation = violationRecordMapper.selectOne(Wrappers.<ViolationRecord>lambdaQuery()
                .eq(ViolationRecord::getSourceAlarmId, alarm.getId())
                .last("limit 1"));
        Long violationId = null;
        if (approved) {
            Long personnelId = request.personnelId() == null ? detection.getPersonnelId() : request.personnelId();
            if (personnelId == null) {
                throw new BusinessException(400, "审核通过前请选择违规人员");
            }
            if (personnelMapper.selectById(personnelId) == null) {
                throw new BusinessException(400, "人员不存在");
            }
            boolean insert = violation == null;
            if (violation == null) {
                violation = new ViolationRecord();
                violation.setSourceAlarmId(alarm.getId());
                violation.setCreatedAt(LocalDateTime.now());
            }
            violation.setPersonnelId(personnelId);
            violation.setViolationItem(aiViolationName(detection.getDetectType()));
            violation.setFineAmount(request.fineAmount() == null ? defaultFine(detection.getDetectType()) : request.fineAmount());
            violation.setPaymentStatus(0);
            violation.setOccurredAt(detection.getOccurredAt() == null ? LocalDateTime.now() : detection.getOccurredAt());
            violation.setRemark(request.remark() == null || request.remark().isBlank() ? "AI风险审核通过，生成罚款记录" : request.remark().trim());
            violation.setReviewStatus(1);
            violation.setUpdatedAt(LocalDateTime.now());
            if (insert) {
                violationRecordMapper.insert(violation);
            } else {
                violationRecordMapper.updateById(violation);
            }
            violationId = violation.getId();
        } else if (violation != null) {
            violation.setReviewStatus(2);
            violation.setPaymentStatus(3);
            violation.setRemark(request.remark() == null || request.remark().isBlank() ? "AI风险审核驳回" : request.remark().trim());
            violation.setUpdatedAt(LocalDateTime.now());
            violationRecordMapper.updateById(violation);
        }

        alarm.setStatus(2);
        alarmRecordMapper.updateById(alarm);

        AlarmHandleRecord handle = new AlarmHandleRecord();
        handle.setAlarmId(alarm.getId());
        handle.setHandleBy("系统管理员");
        handle.setHandleContent(approved
                ? "AI风险审核通过，已生成罚款记录"
                : "AI风险审核驳回：" + (request.remark() == null ? "" : request.remark().trim()));
        handle.setHandledAt(LocalDateTime.now());
        alarmHandleRecordMapper.insert(handle);

        return violationId;
    }

    private AlarmRecord createMissingAiAlarm(AiDetectionRecord detection) {
        AlarmRecord alarm = new AlarmRecord();
        alarm.setAlarmType("ai");
        alarm.setAlarmLevel("warn");
        alarm.setDeviceId(detection.getCameraDeviceId());
        alarm.setPersonnelId(detection.getPersonnelId());
        alarm.setContent("AI识别到" + aiViolationName(detection.getDetectType()));
        alarm.setAlarmValue(detection.getConfidence());
        alarm.setOccurredAt(detection.getOccurredAt() == null ? LocalDateTime.now() : detection.getOccurredAt());
        alarm.setStatus(0);
        alarm.setSnapshotFileId(detection.getSnapshotFileId());
        alarm.setCreatedAt(LocalDateTime.now());
        alarmRecordMapper.insert(alarm);

        detection.setSourceAlarmId(alarm.getId());
        aiDetectionRecordMapper.updateById(detection);
        return alarm;
    }

    private Map<Long, AlarmHandleRecord> latestHandles() {
        return alarmHandleRecordMapper.selectList(
                        Wrappers.<AlarmHandleRecord>lambdaQuery().orderByDesc(AlarmHandleRecord::getHandledAt)
                )
                .stream()
                .collect(Collectors.toMap(AlarmHandleRecord::getAlarmId, Function.identity(), (left, right) -> left));
    }

    public PageResult<CameraView> cameras(int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceType> typeMap = typeMap();
        Map<Long, DeviceLocation> locationMap = locationMap();
        Map<Long, CameraDeviceProfile> profileMap = cameraProfileMap();
        List<DeviceAsset> cameras = allDevices().stream()
                .filter(device -> {
                    DeviceType type = typeMap.get(device.getTypeId());
                    return type != null && "camera".equals(type.getCode());
                })
                .sorted(Comparator.comparing(DeviceAsset::getId).reversed())
                .toList();
        int from = Math.min((safePage - 1) * safePageSize, cameras.size());
        int to = Math.min(from + safePageSize, cameras.size());
        List<CameraView> records = cameras.subList(from, to).stream()
                .map(device -> {
                    DeviceLocation location = device.getLocationId() == null ? null : locationMap.get(device.getLocationId());
                    CameraDeviceProfile profile = profileMap.get(device.getId());
                    Document latestFrame = freshCameraFrame(device.getCode());
                    String latestCameraSource = firstString(latestFrame, "payload.cameraSource", "normalized.cameraSource").orElse(null);
                    String profileCameraSource = profile == null ? null : profile.getCameraSource();
                    Integer effectiveOnlineStatus = latestFrame == null ? 0 : 1;
                    return new CameraView(
                            device.getId(),
                            device.getName(),
                            device.getCode(),
                            location == null ? null : location.getName(),
                            latestCameraSource != null ? latestCameraSource : profileCameraSource,
                            profile != null && profile.getAiMonitorTypes() != null ? profile.getAiMonitorTypes() : "helmet,vest,smoke,fire",
                            effectiveOnlineStatus,
                            profile != null && profile.getEnabled() != null ? profile.getEnabled() : device.getEnabled(),
                            snapshotUrl(device.getCode(), latestFrame),
                            streamUrl(device.getCode(), latestFrame)
                    );
                })
                .toList();
        return new PageResult<>(records, cameras.size(), safePage, safePageSize);
    }

    public Optional<CameraSnapshot> cameraSnapshot(String cameraCode) {
        Document latestFrame = freshCameraFrame(cameraCode);
        if (latestFrame == null) {
            return Optional.empty();
        }

        Optional<Path> path = firstString(
                latestFrame,
                "payload.localPath",
                "normalized.localPath",
                "payload.sourceFile.localPath",
                "normalized.sourceFile.localPath",
                "payload.frameRef",
                "normalized.frameRef"
        ).flatMap(this::framePath);
        if (path.isEmpty() || !Files.isRegularFile(path.get())) {
            return Optional.empty();
        }

        String contentType = firstString(latestFrame, "payload.contentType", "normalized.contentType")
                .orElseGet(() -> probeContentType(path.get()));
        String version = firstString(latestFrame, "messageId")
                .orElseGet(() -> firstString(latestFrame, "reportedAt", "createdAt").orElse(path.get().toString()));
        return Optional.of(new CameraSnapshot(path.get(), contentType, version));
    }

    public record CameraSnapshot(Path path, String contentType, String version) {
    }

    public PageResult<CameraVideoView> cameraVideos(String cameraCode, String startTime, String endTime, int page, int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        List<Long> cameraIds = cameraCode == null || cameraCode.isBlank()
                ? List.of()
                : idsByCode(cameraCode);
        if (cameraCode != null && !cameraCode.isBlank() && cameraIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, safePage, safePageSize);
        }

        List<FileResource> filtered = fileResourceMapper.selectList(Wrappers.<FileResource>lambdaQuery()
                        .eq(FileResource::getBizType, "camera_video")
                        .in(!cameraIds.isEmpty(), FileResource::getBizId, cameraIds)
                        .orderByDesc(FileResource::getCreatedAt))
                .stream()
                .filter(file -> inDateRange(file.getCreatedAt(), startTime, endTime))
                .toList();
        int from = Math.min((safePage - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        List<CameraVideoView> records = filtered.subList(from, to).stream()
                .map(file -> {
                    DeviceAsset camera = file.getBizId() == null ? null : deviceMap.get(file.getBizId());
                    return new CameraVideoView(
                            file.getId(),
                            file.getBizId(),
                            camera == null ? null : camera.getCode(),
                            camera == null ? null : camera.getName(),
                            file.getUrl(),
                            file.getObjectKey(),
                            file.getFileName(),
                            file.getContentType(),
                            file.getSizeBytes(),
                            file.getCreatedAt()
                    );
                })
                .toList();
        return new PageResult<>(records, filtered.size(), safePage, safePageSize);
    }

    private DeviceMonitorView deviceMonitor(DeviceAsset device, Map<Long, DeviceType> typeMap) {
        return new DeviceMonitorView(
                deviceCard(device, typeMap),
                realtimeMetrics(device),
                telemetryHistory(device, 24),
                towerWorkStats(device)
        );
    }

    private List<WorkStatView> towerWorkStats(DeviceAsset device) {
        return towerWorkRecordMapper.selectList(
                        Wrappers.<TowerWorkRecord>lambdaQuery()
                                .eq(TowerWorkRecord::getDeviceId, device.getId())
                                .orderByDesc(TowerWorkRecord::getStartTime)
                                .last("LIMIT 8")
                )
                .stream()
                .map(record -> new WorkStatView(
                        record.getId(),
                        device.getName(),
                        record.getStartTime(),
                        record.getEndTime(),
                        doubleValue(record.getMaxWeight()),
                        doubleValue(record.getMaxHeight()),
                        doubleValue(record.getMaxMoment()),
                        doubleValue(record.getMaxWindSpeed())
                ))
                .toList();
    }

    private DeviceCard deviceCard(DeviceAsset device, Map<Long, DeviceType> typeMap) {
        DeviceType type = typeMap.get(device.getTypeId());
        DeviceLocation location = device.getLocationId() == null ? null : locationMap().get(device.getLocationId());
        return new DeviceCard(
                device.getId(),
                device.getName(),
                device.getCode(),
                type == null ? null : type.getCode(),
                type == null ? null : type.getName(),
                location == null ? null : location.getName(),
                device.getOnlineStatus(),
                realtimeMetrics(device)
        );
    }

    private List<MetricValue> realtimeMetrics(DeviceAsset device) {
        Map<Long, DeviceType> types = typeMap();
        DeviceType type = types.get(device.getTypeId());
        String typeCode = type == null ? "unknown" : type.getCode();
        String collection = TELEMETRY_COLLECTIONS.get(typeCode);
        if (collection != null) {
            Document latest = mongoTemplate.findOne(
                    new Query(Criteria.where("deviceCode").is(device.getCode()))
                            .with(Sort.by(Sort.Direction.DESC, "reportedAt"))
                            .limit(1),
                    Document.class,
                    collection
            );
            if (latest != null && isFreshTelemetry(latest)) {
                return metricValues(typeCode, valuesFromDocument(latest, typeCode, device.getCode(), 0));
            }
        }
        return metricValues(typeCode, demoMetrics(typeCode, device.getCode(), demoTick()));
    }

    private List<ChartPoint> telemetryHistory(DeviceAsset device, int limit) {
        DeviceType type = typeMap().get(device.getTypeId());
        String typeCode = type == null ? "unknown" : type.getCode();
        String collection = TELEMETRY_COLLECTIONS.get(typeCode);
        if (collection != null) {
            List<Document> docs = mongoTemplate.find(
                    new Query(Criteria.where("deviceCode").is(device.getCode()))
                            .with(Sort.by(Sort.Direction.DESC, "reportedAt"))
                            .limit(limit),
                    Document.class,
                    collection
            );
            if (!docs.isEmpty()) {
                List<ChartPoint> points = new ArrayList<>(docs.stream()
                        .map(doc -> new ChartPoint(documentTime(doc), valuesFromDocument(doc, typeCode, device.getCode(), 0)))
                        .toList());
                points.sort(Comparator.comparing(ChartPoint::time));
                return points;
            }
        }
        return demoHistory(typeCode, device.getCode(), limit);
    }

    private List<ChartPoint> demoHistory(String typeCode, String deviceCode, int limit) {
        LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.MIN).minusHours(Math.max(0, limit - 24));
        List<ChartPoint> points = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            points.add(new ChartPoint(start.plusHours(index), demoMetrics(typeCode, deviceCode, index)));
        }
        return points;
    }

    private Map<String, Double> valuesFromDocument(Document doc, String typeCode, String deviceCode, int index) {
        Map<String, Double> fallback = demoMetrics(typeCode, deviceCode, index);
        Object normalized = doc.get("normalized");
        Object payload = doc.get("payload");
        Map<String, Double> values = new LinkedHashMap<>();
        for (String metric : metricNames(typeCode).keySet()) {
            Double value = numberFrom(normalized, metric);
            if (value == null) {
                value = numberFrom(payload, metric);
            }
            if (value == null) {
                value = numberFrom(doc, metric);
            }
            values.put(metric, value == null ? fallback.get(metric) : value);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private Double numberFrom(Object source, String key) {
        if (source instanceof Document document) {
            Object value = document.get(key);
            return value instanceof Number number ? number.doubleValue() : null;
        }
        if (source instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get(key);
            return value instanceof Number number ? number.doubleValue() : null;
        }
        return null;
    }

    private LocalDateTime documentTime(Document document) {
        Object reportedAt = document.get("reportedAt");
        if (reportedAt instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (reportedAt instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }
        return LocalDateTime.now();
    }

    private boolean isFreshTelemetry(Document document) {
        return !documentTime(document).isBefore(LocalDateTime.now().minusSeconds(15));
    }

    private Map<String, Double> demoMetrics(String typeCode, String deviceCode, int index) {
        int seed = Math.abs((deviceCode == null ? typeCode : deviceCode).hashCode() % 17);
        double wave = Math.sin((seed + index) / 3.0);
        Map<String, Double> values = new LinkedHashMap<>();
        switch (typeCode) {
            case "tower_crane" -> {
                values.put("weight", round(4.6 + wave * 1.1 + seed * 0.08));
                values.put("amplitude", round(33 + wave * 3.5));
                values.put("obliquity", round(0.7 + Math.abs(wave) * 0.6));
                values.put("windSpeed", round(5.4 + Math.abs(wave) * 1.7));
                values.put("moment", round(510 + wave * 45 + seed * 4));
                values.put("height", round(42 + Math.abs(wave) * 8));
                values.put("rotation", round((seed * 18 + index * 7) % 360));
            }
            case "elevator" -> {
                values.put("speed", round(0.7 + Math.abs(wave) * 0.5));
                values.put("loadWeight", round(420 + wave * 90 + seed * 5));
                values.put("height", round(18 + Math.abs(wave) * 26));
                values.put("tilt", round(0.4 + Math.abs(wave) * 0.4));
                values.put("peopleCount", round(5 + Math.abs(wave) * 4));
            }
            case "formwork" -> {
                values.put("settlement", round(1.6 + Math.abs(wave) * 1.2));
                values.put("batteryLevel", round(82 - index * 0.1 + seed * 0.3));
                values.put("xAngle", round(0.8 + wave * 0.5));
                values.put("yAngle", round(0.6 + Math.abs(wave) * 0.5));
                values.put("pressure", round(18 + Math.abs(wave) * 4));
            }
            case "deep_pit" -> {
                values.put("waterLevel", round(2.2 + Math.abs(wave) * 0.6));
                values.put("xAngle", round(0.5 + wave * 0.3));
                values.put("yAngle", round(0.4 + Math.abs(wave) * 0.3));
                values.put("settlement", round(2.8 + Math.abs(wave) * 1.1));
                values.put("strain", round(120 + wave * 18));
                values.put("soilPressure", round(38 + Math.abs(wave) * 5));
            }
            case "environment_sensor" -> {
                values.put("pm25", round(48 + Math.abs(wave) * 18 + seed * 0.6));
                values.put("pm10", round(86 + Math.abs(wave) * 28 + seed));
                values.put("noise", round(58 + Math.abs(wave) * 7));
                values.put("temperature", round(25 + wave * 4));
                values.put("humidity", round(55 + Math.abs(wave) * 12));
                values.put("windSpeed", round(2.5 + Math.abs(wave) * 1.8));
                values.put("tsp", round(150 + Math.abs(wave) * 45));
            }
            default -> values.put("status", 1.0);
        }
        return values;
    }

    private int demoTick() {
        return (int) ((System.currentTimeMillis() / 3000L) % 100000);
    }

    private List<MetricValue> metricValues(String typeCode, Map<String, Double> values) {
        Map<String, String> names = metricNames(typeCode);
        return values.entrySet().stream()
                .map(entry -> new MetricValue(
                        entry.getKey(),
                        names.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue(),
                        metricUnits().get(entry.getKey()),
                        metricStatus(entry.getKey(), entry.getValue())
                ))
                .toList();
    }

    private Map<String, String> metricNames(String typeCode) {
        Map<String, String> names = new LinkedHashMap<>();
        switch (typeCode) {
            case "tower_crane" -> {
                names.put("weight", "吊重");
                names.put("amplitude", "幅度");
                names.put("obliquity", "倾度");
                names.put("windSpeed", "风速");
                names.put("moment", "力矩");
                names.put("height", "高度");
                names.put("rotation", "回转角度");
            }
            case "elevator" -> {
                names.put("speed", "速度");
                names.put("loadWeight", "载重");
                names.put("height", "高度");
                names.put("tilt", "倾斜度");
                names.put("peopleCount", "人数");
            }
            case "formwork" -> {
                names.put("settlement", "沉降量");
                names.put("batteryLevel", "电池电量");
                names.put("xAngle", "X轴倾斜角度");
                names.put("yAngle", "Y轴倾斜角度");
                names.put("pressure", "压力值");
            }
            case "deep_pit" -> {
                names.put("waterLevel", "地下水位");
                names.put("xAngle", "X轴倾角");
                names.put("yAngle", "Y轴倾角");
                names.put("settlement", "沉降值");
                names.put("strain", "应变拉力");
                names.put("soilPressure", "土压力");
            }
            case "environment_sensor" -> {
                names.put("pm25", "PM2.5");
                names.put("pm10", "PM10");
                names.put("noise", "噪音");
                names.put("temperature", "温度");
                names.put("humidity", "湿度");
                names.put("windSpeed", "风速");
                names.put("tsp", "TSP");
            }
            default -> names.put("status", "Status");
        }
        return names;
    }

    private Map<String, String> metricUnits() {
        return Map.ofEntries(
                Map.entry("weight", "t"),
                Map.entry("loadWeight", "kg"),
                Map.entry("amplitude", "m"),
                Map.entry("obliquity", "deg"),
                Map.entry("windSpeed", "m/s"),
                Map.entry("moment", "kN.m"),
                Map.entry("height", "m"),
                Map.entry("rotation", "deg"),
                Map.entry("speed", "m/s"),
                Map.entry("tilt", "deg"),
                Map.entry("peopleCount", "person"),
                Map.entry("settlement", "mm"),
                Map.entry("batteryLevel", "%"),
                Map.entry("xAngle", "deg"),
                Map.entry("yAngle", "deg"),
                Map.entry("pressure", "kPa"),
                Map.entry("waterLevel", "m"),
                Map.entry("strain", "ue"),
                Map.entry("soilPressure", "kPa"),
                Map.entry("pm25", "ug/m3"),
                Map.entry("pm10", "ug/m3"),
                Map.entry("noise", "dB"),
                Map.entry("temperature", "C"),
                Map.entry("humidity", "%"),
                Map.entry("tsp", "ug/m3")
        );
    }

    private String metricStatus(String code, Double value) {
        if (value == null) {
            return "unknown";
        }
        return switch (code) {
            case "pm25" -> value >= 75 ? "alarm" : value >= 60 ? "warn" : "normal";
            case "pm10" -> value >= 150 ? "alarm" : value >= 120 ? "warn" : "normal";
            case "noise" -> value >= 75 ? "alarm" : value >= 68 ? "warn" : "normal";
            case "windSpeed" -> value >= 12 ? "alarm" : value >= 8 ? "warn" : "normal";
            case "moment" -> value >= 760 ? "alarm" : value >= 680 ? "warn" : "normal";
            case "settlement" -> value >= 8 ? "alarm" : value >= 5 ? "warn" : "normal";
            default -> "normal";
        };
    }

    private PageResult<AlarmView> listAlarms(String level,
                                             List<String> types,
                                             String alarmType,
                                             String deviceName,
                                             Integer status,
                                             String startTime,
                                             String endTime,
                                             int page,
                                             int pageSize) {
        int safePage = safePage(page);
        int safePageSize = safePageSize(pageSize);
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        Map<Long, MonitorPoint> pointMap = monitorPointMapper.selectList(null).stream()
                .collect(Collectors.toMap(MonitorPoint::getId, Function.identity(), (left, right) -> left));
        List<AlarmView> filtered = alarmRecordMapper.selectList(
                        Wrappers.<AlarmRecord>lambdaQuery()
                                .in(types != null && !types.isEmpty(), AlarmRecord::getAlarmType, types)
                                .orderByDesc(AlarmRecord::getOccurredAt)
                )
                .stream()
                .map(alarm -> alarmView(alarm, deviceMap, pointMap))
                .filter(alarm -> matchesLevel(alarm.alarmLevel(), level))
                .filter(alarm -> status == null || status.equals(alarm.status()))
                .filter(alarm -> matchesAlarmType(alarm, alarmType))
                .filter(alarm -> matchesText(alarm.deviceName(), deviceName) || matchesText(alarm.deviceCode(), deviceName))
                .filter(alarm -> inDateRange(alarm.occurredAt(), startTime, endTime))
                .toList();
        int from = Math.min((safePage - 1) * safePageSize, filtered.size());
        int to = Math.min(from + safePageSize, filtered.size());
        return new PageResult<>(filtered.subList(from, to), filtered.size(), safePage, safePageSize);
    }

    private List<AlarmView> latestAlarms(int limit) {
        Map<Long, DeviceAsset> deviceMap = deviceMap();
        Map<Long, MonitorPoint> pointMap = monitorPointMapper.selectList(null).stream()
                .collect(Collectors.toMap(MonitorPoint::getId, Function.identity(), (left, right) -> left));
        return alarmRecordMapper.selectList(
                        Wrappers.<AlarmRecord>lambdaQuery()
                                .orderByDesc(AlarmRecord::getOccurredAt)
                                .last("LIMIT " + Math.max(1, limit))
                )
                .stream()
                .map(alarm -> alarmView(alarm, deviceMap, pointMap))
                .toList();
    }

    private AlarmView alarmView(AlarmRecord alarm, Map<Long, DeviceAsset> deviceMap, Map<Long, MonitorPoint> pointMap) {
        DeviceAsset device = alarm.getDeviceId() == null ? null : deviceMap.get(alarm.getDeviceId());
        MonitorPoint point = alarm.getMonitorPointId() == null ? null : pointMap.get(alarm.getMonitorPointId());
        MonitorRule rule = point == null ? null : monitorRuleMapper.selectOne(
                Wrappers.<MonitorRule>lambdaQuery()
                        .eq(MonitorRule::getMonitorPointId, point.getId())
                        .last("LIMIT 1")
        );
        return new AlarmView(
                alarm.getId(),
                alarm.getAlarmType(),
                alarm.getAlarmLevel(),
                alarm.getDeviceId(),
                device == null ? null : device.getName(),
                device == null ? null : device.getCode(),
                alarm.getMonitorPointId(),
                point == null ? null : point.getName(),
                alarm.getContent(),
                doubleValue(alarm.getAlarmValue()),
                alarm.getUnit(),
                doubleValue(rule == null ? null : rule.getWarnUpper()),
                doubleValue(rule == null ? null : rule.getWarnLower()),
                doubleValue(rule == null ? null : rule.getAlarmUpper()),
                doubleValue(rule == null ? null : rule.getAlarmLower()),
                alarm.getOccurredAt(),
                alarm.getStatus()
        );
    }

    private List<ChartPoint> alarmTrend() {
        List<AlarmRecord> alarms = alarmRecordMapper.selectList(
                Wrappers.<AlarmRecord>lambdaQuery().ge(AlarmRecord::getOccurredAt, LocalDateTime.now().minusDays(6))
        );
        Map<LocalDate, Long> grouped = alarms.stream()
                .collect(Collectors.groupingBy(alarm -> alarm.getOccurredAt().toLocalDate(), Collectors.counting()));
        List<ChartPoint> trend = new ArrayList<>();
        for (int index = 6; index >= 0; index--) {
            LocalDate date = LocalDate.now().minusDays(index);
            trend.add(new ChartPoint(date.atStartOfDay(), Map.of("count", grouped.getOrDefault(date, 0L).doubleValue())));
        }
        return trend;
    }

    private List<DeviceAsset> allDevices() {
        return deviceAssetMapper.selectList(Wrappers.<DeviceAsset>lambdaQuery().orderByDesc(DeviceAsset::getId));
    }

    private Map<Long, DeviceAsset> deviceMap() {
        return allDevices().stream().collect(Collectors.toMap(DeviceAsset::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, DeviceType> typeMap() {
        return deviceTypeMapper.selectList(null).stream()
                .collect(Collectors.toMap(DeviceType::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, DeviceLocation> locationMap() {
        return deviceLocationMapper.selectList(null).stream()
                .collect(Collectors.toMap(DeviceLocation::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, CameraDeviceProfile> cameraProfileMap() {
        return cameraDeviceProfileMapper.selectList(null).stream()
                .collect(Collectors.toMap(CameraDeviceProfile::getDeviceId, Function.identity(), (left, right) -> left));
    }

    private Document latestCameraFrame(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return null;
        }
        Query query = new Query(Criteria.where("deviceCode").is(deviceCode.trim()))
                .with(Sort.by(Sort.Order.desc("reportedAt"), Sort.Order.desc("createdAt")))
                .limit(1);
        return mongoTemplate.findOne(query, Document.class, CAMERA_FRAME_COLLECTION);
    }

    private Document freshCameraFrame(String deviceCode) {
        Document latestFrame = latestCameraFrame(deviceCode);
        return latestFrame != null && isFreshTelemetry(latestFrame) ? latestFrame : null;
    }

    private String snapshotUrl(String deviceCode, Document latestFrame) {
        if (latestFrame == null) {
            return null;
        }
        String version = firstString(latestFrame, "messageId")
                .orElseGet(() -> firstString(latestFrame, "reportedAt", "createdAt").orElse("latest"));
        return "/api/site/cameras/" + encodePath(deviceCode) + "/snapshot?v=" + encodePath(version);
    }

    private String streamUrl(String deviceCode, Document latestFrame) {
        if (latestFrame == null) {
            return null;
        }
        Optional<String> sourceStreamUrl = firstString(latestFrame, "payload.streamUrl", "normalized.streamUrl");
        if (sourceStreamUrl.isPresent()) {
            return sourceStreamUrl.get();
        }
        return "/api/site/cameras/" + encodePath(deviceCode) + "/stream";
    }

    private Optional<String> firstString(Document document, String... paths) {
        if (document == null) {
            return Optional.empty();
        }
        for (String path : paths) {
            Object value = nestedValue(document, path);
            if (value != null && !String.valueOf(value).isBlank()) {
                return Optional.of(String.valueOf(value));
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Object nestedValue(Object source, String path) {
        Object current = source;
        for (String part : path.split("\\.")) {
            if (current instanceof Document document) {
                current = document.get(part);
            } else if (current instanceof Map<?, ?> map) {
                current = ((Map<String, Object>) map).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private Optional<Path> framePath(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("file://")) {
                return Optional.of(Path.of(URI.create(trimmed)).normalize());
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("oss://")) {
                return Optional.empty();
            }
            return Optional.of(Path.of(trimmed).toAbsolutePath().normalize());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String probeContentType(Path path) {
        try {
            String contentType = Files.probeContentType(path);
            return contentType == null || contentType.isBlank() ? "image/jpeg" : contentType;
        } catch (Exception exception) {
            return "image/jpeg";
        }
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private DeviceAsset firstDeviceByType(String typeCode) {
        Map<Long, DeviceType> types = typeMap();
        return allDevices().stream()
                .filter(device -> {
                    DeviceType type = types.get(device.getTypeId());
                    return type != null && typeCode.equals(type.getCode());
                })
                .findFirst()
                .orElse(null);
    }

    private DeviceAsset deviceByCodeOrFirst(String deviceCode, String typeCode) {
        if (deviceCode != null && !deviceCode.isBlank()) {
            DeviceAsset device = deviceAssetMapper.selectOne(
                    Wrappers.<DeviceAsset>lambdaQuery().eq(DeviceAsset::getCode, deviceCode.trim()).last("LIMIT 1")
            );
            if (device != null) {
                return device;
            }
        }
        return firstDeviceByType(typeCode);
    }

    private List<Long> idsByCode(String deviceCode) {
        return deviceAssetMapper.selectList(Wrappers.<DeviceAsset>lambdaQuery().eq(DeviceAsset::getCode, deviceCode.trim()))
                .stream()
                .map(DeviceAsset::getId)
                .toList();
    }

    private boolean isConstructionDevice(DeviceType type) {
        return type != null && List.of("tower_crane", "elevator", "formwork", "deep_pit").contains(type.getCode());
    }

    private Integer countByType(List<DeviceAsset> devices, Map<Long, DeviceType> typeMap, String typeCode) {
        return Math.toIntExact(devices.stream()
                .filter(device -> {
                    DeviceType type = typeMap.get(device.getTypeId());
                    return type != null && typeCode.equals(type.getCode());
                })
                .count());
    }

    private int safePage(int page) {
        return Math.max(page, 1);
    }

    private int safePageSize(int pageSize) {
        return Math.max(1, Math.min(pageSize, 100));
    }

    private Double doubleValue(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean containsIgnoreCase(String value, String lowerKeyword) {
        return value != null && value.toLowerCase().contains(lowerKeyword);
    }

    private boolean matchesLevel(String value, String level) {
        if (level == null || level.isBlank()) {
            return true;
        }
        String normalized = level.trim();
        if ("warning".equalsIgnoreCase(normalized)) {
            normalized = "warn";
        }
        return value != null && value.equalsIgnoreCase(normalized);
    }

    private boolean matchesAlarmType(AlarmView alarm, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        if (normalized.contains("塔吊")) {
            return "tower_crane".equals(alarm.alarmType());
        }
        if (normalized.contains("升降机")) {
            return "elevator".equals(alarm.alarmType());
        }
        if (normalized.contains("高支模")) {
            return "formwork".equals(alarm.alarmType());
        }
        if (normalized.contains("基坑")) {
            return "deep_pit".equals(alarm.alarmType());
        }
        if (normalized.contains("环境")) {
            return "environment".equals(alarm.alarmType());
        }
        return matchesText(alarm.alarmType(), normalized)
                || matchesText(alarm.monitorPointName(), normalized)
                || matchesText(alarm.content(), normalized);
    }

    private boolean matchesDeviceCode(String value, String keyword) {
        return matchesText(value, keyword);
    }

    private boolean matchesAiType(AiRiskView record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim();
        if ("未戴安全帽".equals(normalized)) {
            normalized = "未佩戴安全帽";
        } else if ("未穿安全衣".equals(normalized)) {
            normalized = "未穿反光衣";
        }
        return matchesText(record.detectType(), normalized)
                || matchesText(record.alarmLevel(), normalized)
                || matchesText(record.content(), normalized);
    }

    private String aiViolationName(String detectType) {
        return switch (detectType == null ? "" : detectType) {
            case "no_helmet", "helmet_missing" -> "未佩戴安全帽";
            case "no_vest", "vest_missing" -> "未穿反光衣";
            case "smoke", "smoking" -> "工作现场抽烟";
            case "fire", "open_fire" -> "工作现场明火";
            default -> detectType == null || detectType.isBlank() ? "AI识别违规" : detectType;
        };
    }

    private BigDecimal defaultFine(String detectType) {
        return switch (aiViolationName(detectType)) {
            case "工作现场抽烟", "工作现场明火" -> BigDecimal.valueOf(100);
            case "未佩戴安全帽", "未穿反光衣" -> BigDecimal.valueOf(50);
            default -> BigDecimal.ZERO;
        };
    }

    private boolean matchesText(String value, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private boolean inDateRange(LocalDateTime value, String startTime, String endTime) {
        if (value == null) {
            return false;
        }
        LocalDateTime start = parseDateStart(startTime);
        LocalDateTime end = parseDateEnd(endTime);
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    private LocalDateTime parseDateStart(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDateTime parseDateEnd(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atTime(LocalTime.MAX);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
