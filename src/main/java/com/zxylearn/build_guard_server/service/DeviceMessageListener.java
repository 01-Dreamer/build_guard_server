package com.zxylearn.build_guard_server.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zxylearn.build_guard_server.config.MqNames;
import com.zxylearn.build_guard_server.entity.AlarmRecord;
import com.zxylearn.build_guard_server.entity.DeviceAsset;
import com.zxylearn.build_guard_server.entity.DeviceOnlineRecord;
import com.zxylearn.build_guard_server.entity.MonitorPoint;
import com.zxylearn.build_guard_server.entity.MonitorRule;
import com.zxylearn.build_guard_server.entity.MqMessageLog;
import com.zxylearn.build_guard_server.mapper.AlarmRecordMapper;
import com.zxylearn.build_guard_server.mapper.DeviceAssetMapper;
import com.zxylearn.build_guard_server.mapper.DeviceOnlineRecordMapper;
import com.zxylearn.build_guard_server.mapper.MonitorPointMapper;
import com.zxylearn.build_guard_server.mapper.MonitorRuleMapper;
import com.zxylearn.build_guard_server.mapper.MqMessageLogMapper;
import org.bson.Document;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DeviceMessageListener {
    private static final long LATEST_TTL_MINUTES = 10;
    private static final long ALARM_DEDUPE_TTL_MINUTES = 5;

    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final DeviceAssetMapper deviceAssetMapper;
    private final DeviceOnlineRecordMapper deviceOnlineRecordMapper;
    private final MonitorPointMapper monitorPointMapper;
    private final MonitorRuleMapper monitorRuleMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final MqMessageLogMapper mqMessageLogMapper;

    public DeviceMessageListener(ObjectMapper objectMapper,
                                 MongoTemplate mongoTemplate,
                                 StringRedisTemplate redisTemplate,
                                 DeviceAssetMapper deviceAssetMapper,
                                 DeviceOnlineRecordMapper deviceOnlineRecordMapper,
                                 MonitorPointMapper monitorPointMapper,
                                 MonitorRuleMapper monitorRuleMapper,
                                 AlarmRecordMapper alarmRecordMapper,
                                 MqMessageLogMapper mqMessageLogMapper) {
        this.objectMapper = objectMapper;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
        this.deviceAssetMapper = deviceAssetMapper;
        this.deviceOnlineRecordMapper = deviceOnlineRecordMapper;
        this.monitorPointMapper = monitorPointMapper;
        this.monitorRuleMapper = monitorRuleMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.mqMessageLogMapper = mqMessageLogMapper;
    }

    @RabbitListener(queues = MqNames.DEVICE_TELEMETRY_QUEUE)
    public void onTelemetry(String messageJson) {
        handleMessage(messageJson, MqNames.DEVICE_TELEMETRY_QUEUE, message -> {
            persistTelemetry(message);
            updateLatest(message, messageJson);
            checkMonitorRules(message);
        });
    }

    @RabbitListener(queues = MqNames.DEVICE_STATUS_QUEUE)
    public void onStatus(String messageJson) {
        handleMessage(messageJson, MqNames.DEVICE_STATUS_QUEUE, message -> {
            persistRaw(message, "device_telemetry_raw");
            updateDeviceStatus(message);
        });
    }

    @RabbitListener(queues = MqNames.CAMERA_FRAME_QUEUE)
    public void onCameraFrame(String messageJson) {
        handleMessage(messageJson, MqNames.CAMERA_FRAME_QUEUE, message -> {
            persistRaw(message, "camera_frame_event");
            updateLatest(message, messageJson);
        });
    }

    private void handleMessage(String messageJson, String topic, Consumer<Map<String, Object>> handler) {
        Map<String, Object> message = parse(messageJson);
        if (alreadyConsumed(message)) {
            return;
        }

        try {
            handler.accept(message);
            logConsumed(message, topic, 1, null);
        } catch (RuntimeException exception) {
            logConsumed(message, topic, 2, trimError(exception.getMessage()));
            throw exception;
        }
    }

    private Map<String, Object> parse(String messageJson) {
        try {
            return objectMapper.readValue(messageJson, new TypeReference<>() {
            });
        } catch (Exception exception) {
            MqMessageLog log = new MqMessageLog();
            log.setMessageId("parse-error-" + UUID.randomUUID());
            log.setTopic("unknown");
            log.setConsumeStatus(2);
            log.setErrorMessage("设备消息JSON解析失败");
            log.setCreatedAt(LocalDateTime.now());
            mqMessageLogMapper.insert(log);
            throw new IllegalArgumentException("设备消息JSON解析失败", exception);
        }
    }

    private boolean alreadyConsumed(Map<String, Object> message) {
        String messageId = String.valueOf(message.getOrDefault("messageId", ""));
        if (messageId.isBlank()) {
            return false;
        }
        Long count = mqMessageLogMapper.selectCount(
                Wrappers.<MqMessageLog>lambdaQuery()
                        .eq(MqMessageLog::getMessageId, messageId)
                        .eq(MqMessageLog::getConsumeStatus, 1)
        );
        return count > 0;
    }

    private void persistTelemetry(Map<String, Object> message) {
        Document document = telemetryDocument(message, true);
        mongoTemplate.insert(document, "device_telemetry_raw");

        String collection = telemetryCollection(String.valueOf(message.get("deviceType")));
        if (!"device_telemetry_raw".equals(collection)) {
            mongoTemplate.insert(new Document(document), collection);
        }
    }

    private void persistRaw(Map<String, Object> message, String collectionName) {
        Document document = telemetryDocument(message, false);
        mongoTemplate.insert(document, collectionName);
    }

    @SuppressWarnings("unchecked")
    private Document telemetryDocument(Map<String, Object> message, boolean alarmChecked) {
        Document document = new Document(message);
        Map<String, Object> payload = message.get("payload") instanceof Map<?, ?> rawPayload
                ? (Map<String, Object>) rawPayload
                : Map.of();
        document.putIfAbsent("source", payload.getOrDefault("source", "sim"));
        document.putIfAbsent("reportedAt", parseMessageTime(message.get("occurredAt")));
        document.put("receivedAt", LocalDateTime.now());
        document.put("createdAt", LocalDateTime.now());
        document.put("alarmChecked", alarmChecked);
        document.putIfAbsent("normalized", payload);
        return document;
    }

    private void updateLatest(Map<String, Object> message, String messageJson) {
        Object deviceCode = message.get("deviceCode");
        if (deviceCode == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    "buildguard:device:latest:" + deviceCode,
                    messageJson,
                    LATEST_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (RuntimeException ignored) {
            // Redis cache should not block MQ consumption in local testing.
        }
    }

    @SuppressWarnings("unchecked")
    private void updateDeviceStatus(Map<String, Object> message) {
        String deviceCode = String.valueOf(message.get("deviceCode"));
        Map<String, Object> payload = message.get("payload") instanceof Map<?, ?> rawPayload
                ? (Map<String, Object>) rawPayload
                : Map.of();
        boolean online = Boolean.TRUE.equals(payload.get("online")) || "online".equals(payload.get("status"));

        DeviceAsset device = deviceAssetMapper.selectOne(Wrappers.<DeviceAsset>lambdaQuery()
                .eq(DeviceAsset::getCode, deviceCode)
                .last("limit 1"));
        if (device == null) {
            return;
        }

        device.setOnlineStatus(online ? 1 : 0);
        deviceAssetMapper.updateById(device);

        DeviceOnlineRecord record = new DeviceOnlineRecord();
        record.setDeviceId(device.getId());
        record.setOnlineStatus(online ? 1 : 0);
        record.setReportedAt(LocalDateTime.now());
        record.setSource(String.valueOf(payload.getOrDefault("source", "sim")));
        deviceOnlineRecordMapper.insert(record);
    }

    @SuppressWarnings("unchecked")
    private void checkMonitorRules(Map<String, Object> message) {
        String deviceCode = String.valueOf(message.getOrDefault("deviceCode", ""));
        if (deviceCode.isBlank()) {
            return;
        }

        DeviceAsset device = deviceAssetMapper.selectOne(Wrappers.<DeviceAsset>lambdaQuery()
                .eq(DeviceAsset::getCode, deviceCode)
                .last("limit 1"));
        if (device == null) {
            return;
        }

        Map<String, Object> payload = message.get("payload") instanceof Map<?, ?> rawPayload
                ? (Map<String, Object>) rawPayload
                : Map.of();
        if (payload.isEmpty()) {
            return;
        }

        List<MonitorPoint> points = monitorPointMapper.selectList(Wrappers.<MonitorPoint>lambdaQuery()
                .eq(MonitorPoint::getDeviceId, device.getId())
                .eq(MonitorPoint::getStatus, 1));
        if (points.isEmpty()) {
            return;
        }

        Map<Long, MonitorRule> rules = monitorRuleMapper.selectList(Wrappers.<MonitorRule>lambdaQuery()
                        .in(MonitorRule::getMonitorPointId, points.stream().map(MonitorPoint::getId).toList())
                        .eq(MonitorRule::getEnabled, 1))
                .stream()
                .collect(Collectors.toMap(MonitorRule::getMonitorPointId, Function.identity(), (left, right) -> left));

        LocalDateTime occurredAt = parseMessageTime(message.get("occurredAt"));
        for (MonitorPoint point : points) {
            MonitorRule rule = rules.get(point.getId());
            if (rule == null) {
                continue;
            }

            BigDecimal value = numericValue(payload.get(point.getMetricCode()));
            if (value == null) {
                continue;
            }

            String level = alarmLevel(value, rule);
            if (level == null || isDuplicateAlarm(rule.getId(), deviceCode)) {
                continue;
            }

            AlarmRecord alarm = new AlarmRecord();
            alarm.setAlarmType(String.valueOf(message.getOrDefault("deviceType", "device")));
            alarm.setAlarmLevel(level);
            alarm.setDeviceId(device.getId());
            alarm.setMonitorPointId(point.getId());
            alarm.setContent(point.getMetricName() + ("alarm".equals(level) ? "超过报警阈值" : "达到预警阈值"));
            alarm.setAlarmValue(value);
            alarm.setUnit(point.getUnit());
            alarm.setOccurredAt(occurredAt);
            alarm.setStatus(0);
            alarm.setCreatedAt(LocalDateTime.now());
            alarmRecordMapper.insert(alarm);
        }
    }

    private boolean isDuplicateAlarm(Long ruleId, String deviceCode) {
        String key = "buildguard:alarm:dedupe:" + ruleId + ":" + deviceCode;
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", ALARM_DEDUPE_TTL_MINUTES, TimeUnit.MINUTES);
            return !Boolean.TRUE.equals(success);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private BigDecimal numericValue(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String alarmLevel(BigDecimal value, MonitorRule rule) {
        if ((rule.getAlarmUpper() != null && value.compareTo(rule.getAlarmUpper()) >= 0)
                || (rule.getAlarmLower() != null && value.compareTo(rule.getAlarmLower()) <= 0)) {
            return "alarm";
        }
        if ((rule.getWarnUpper() != null && value.compareTo(rule.getWarnUpper()) >= 0)
                || (rule.getWarnLower() != null && value.compareTo(rule.getWarnLower()) <= 0)) {
            return "warn";
        }
        return null;
    }

    private LocalDateTime parseMessageTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(text), ZoneId.systemDefault());
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
                } catch (DateTimeParseException ignoredAgain) {
                    return LocalDateTime.now();
                }
            }
        }
        return LocalDateTime.now();
    }

    private void logConsumed(Map<String, Object> message, String topic, int status, String errorMessage) {
        String messageId = String.valueOf(message.getOrDefault("messageId", ""));
        if (messageId.isBlank()) {
            messageId = "missing-message-id-" + UUID.randomUUID();
        }

        MqMessageLog log = new MqMessageLog();
        log.setMessageId(messageId);
        log.setTopic(topic);
        log.setDeviceCode(String.valueOf(message.getOrDefault("deviceCode", "")));
        log.setConsumeStatus(status);
        log.setErrorMessage(errorMessage);
        log.setCreatedAt(LocalDateTime.now());

        MqMessageLog existing = mqMessageLogMapper.selectOne(Wrappers.<MqMessageLog>lambdaQuery()
                .eq(MqMessageLog::getMessageId, messageId)
                .last("limit 1"));
        if (existing == null) {
            mqMessageLogMapper.insert(log);
            return;
        }

        existing.setTopic(topic);
        existing.setDeviceCode(log.getDeviceCode());
        existing.setConsumeStatus(status);
        existing.setErrorMessage(errorMessage);
        mqMessageLogMapper.updateById(existing);
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private String telemetryCollection(String deviceType) {
        return switch (deviceType) {
            case "tower_crane" -> "tower_crane_telemetry";
            case "elevator" -> "elevator_telemetry";
            case "formwork" -> "formwork_telemetry";
            case "deep_pit" -> "deep_pit_telemetry";
            case "environment_sensor" -> "environment_telemetry";
            default -> "device_telemetry_raw";
        };
    }
}
