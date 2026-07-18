package com.zxylearn.build_guard_server.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;

public final class PageDtos {
    private PageDtos() {
    }

    public record MetricValue(String code, String name, Double value, String unit, String status) {
    }

    public record ChartPoint(LocalDateTime time, Map<String, Double> values) {
    }

    public record DashboardOverview(
            Integer totalDevices,
            Integer onlineDevices,
            Integer openAlarms,
            Integer aiRisks,
            List<MetricValue> environment,
            List<ChartPoint> alarmTrend,
            List<AlarmView> latestAlarms
    ) {
    }

    public record DeviceOverview(
            Integer total,
            Integer online,
            Integer towerCranes,
            Integer elevators,
            Integer formworks,
            Integer deepPits,
            List<DeviceCard> devices,
            List<AlarmView> latestAlarms
    ) {
    }

    public record DeviceCard(
            Long id,
            String name,
            String code,
            String typeCode,
            String typeName,
            String locationName,
            Integer onlineStatus,
            List<MetricValue> metrics
    ) {
    }

    public record DeviceMonitorView(
            DeviceCard device,
            List<MetricValue> realtime,
            List<ChartPoint> trend,
            List<WorkStatView> workStats
    ) {
    }

    public record WorkStatView(
            Long id,
            String deviceName,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Double maxWeight,
            Double maxHeight,
            Double maxMoment,
            Double maxWindSpeed
    ) {
    }

    public record OnlineRecordView(
            Long id,
            Long deviceId,
            String deviceName,
            String deviceCode,
            Integer onlineStatus,
            LocalDateTime reportedAt,
            String source
    ) {
    }

    public record AlarmView(
            Long id,
            String alarmType,
            String alarmLevel,
            Long deviceId,
            String deviceName,
            String deviceCode,
            Long monitorPointId,
            String monitorPointName,
            String content,
            Double alarmValue,
            String unit,
            Double warnUpper,
            Double warnLower,
            Double alarmUpper,
            Double alarmLower,
            LocalDateTime occurredAt,
            Integer status
    ) {
    }

    public record AlarmHandleRequest(
            String handleBy,
            @NotBlank String handleContent
    ) {
    }

    public record EnvironmentRealtimeView(
            String deviceCode,
            String deviceName,
            List<MetricValue> metrics,
            List<ChartPoint> trend
    ) {
    }

    public record SprayTaskView(
            Long id,
            String name,
            Long sprayDeviceId,
            String sprayDeviceName,
            LocalDateTime startTime,
            Integer durationMinutes,
            Integer cycleValue,
            String cycleUnit,
            Integer enabled,
            LocalDateTime nextRunAt
    ) {
    }

    public record SprayTaskRequest(
            @NotBlank String name,
            Long sprayDeviceId,
            LocalDateTime startTime,
            Integer durationMinutes,
            Integer cycleValue,
            String cycleUnit,
            Integer enabled,
            LocalDateTime nextRunAt
    ) {
    }

    public record SprayRecordView(
            Long id,
            Long sprayDeviceId,
            String sprayDeviceName,
            Long taskId,
            String taskName,
            String operationType,
            String triggerType,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            Integer executeStatus
    ) {
    }

    public record MonitorPointView(
            Long id,
            String name,
            String code,
            Long deviceId,
            String deviceName,
            String deviceCode,
            String deviceTypeCode,
            String deviceTypeName,
            String metricCode,
            String metricName,
            String unit,
            Integer sort,
            Integer status,
            Double warnUpper,
            Double warnLower,
            Double alarmUpper,
            Double alarmLower,
            Integer ruleEnabled
    ) {
    }

    public record MonitorPointRequest(
            @NotBlank String name,
            @NotBlank String code,
            Long deviceId,
            @NotBlank String metricCode,
            @NotBlank String metricName,
            String unit,
            Integer sort,
            Integer status,
            BigDecimal warnUpper,
            BigDecimal warnLower,
            BigDecimal alarmUpper,
            BigDecimal alarmLower,
            Integer ruleEnabled
    ) {
    }

    public record MonitorRuleRequest(
            BigDecimal warnUpper,
            BigDecimal warnLower,
            BigDecimal alarmUpper,
            BigDecimal alarmLower,
            Integer ruleEnabled
    ) {
    }

    public record AiRiskView(
            Long id,
            Long taskId,
            Long cameraDeviceId,
            String cameraName,
            String cameraCode,
            Long personnelId,
            String personnelName,
            String detectType,
            Double confidence,
            String resultJson,
            LocalDateTime occurredAt,
            Long sourceAlarmId,
            String alarmLevel,
            Integer alarmStatus,
            String content,
            String handleByName,
            LocalDateTime handledAt,
            String handleContent
    ) {
    }

    public record CameraView(
            Long id,
            String name,
            String code,
            String locationName,
            String cameraSource,
            String aiMonitorTypes,
            Integer onlineStatus,
            Integer enabled,
            String snapshotUrl,
            String streamUrl
    ) {
    }
}
