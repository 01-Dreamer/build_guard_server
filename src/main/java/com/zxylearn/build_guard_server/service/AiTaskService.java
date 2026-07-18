package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.config.MqNames;
import com.zxylearn.build_guard_server.dto.AiDtos.AiRequestMessage;
import com.zxylearn.build_guard_server.dto.AiDtos.AiResultMessage;
import com.zxylearn.build_guard_server.dto.AiDtos.AiTaskRequest;
import com.zxylearn.build_guard_server.dto.AiDtos.AiTaskResponse;
import com.zxylearn.build_guard_server.entity.AiDetectionRecord;
import com.zxylearn.build_guard_server.entity.AiTask;
import com.zxylearn.build_guard_server.entity.AlarmRecord;
import com.zxylearn.build_guard_server.entity.MqMessageLog;
import com.zxylearn.build_guard_server.entity.ViolationRecord;
import com.zxylearn.build_guard_server.mapper.AiDetectionRecordMapper;
import com.zxylearn.build_guard_server.mapper.AiTaskMapper;
import com.zxylearn.build_guard_server.mapper.AlarmRecordMapper;
import com.zxylearn.build_guard_server.mapper.MqMessageLogMapper;
import com.zxylearn.build_guard_server.mapper.ViolationRecordMapper;
import org.bson.Document;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiTaskService {
    private final AiTaskMapper aiTaskMapper;
    private final AiDetectionRecordMapper aiDetectionRecordMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final MqMessageLogMapper mqMessageLogMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    public AiTaskService(AiTaskMapper aiTaskMapper,
                         AiDetectionRecordMapper aiDetectionRecordMapper,
                         AlarmRecordMapper alarmRecordMapper,
                         ViolationRecordMapper violationRecordMapper,
                         MqMessageLogMapper mqMessageLogMapper,
                         RabbitTemplate rabbitTemplate,
                         MongoTemplate mongoTemplate,
                         ObjectMapper objectMapper) {
        this.aiTaskMapper = aiTaskMapper;
        this.aiDetectionRecordMapper = aiDetectionRecordMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.violationRecordMapper = violationRecordMapper;
        this.mqMessageLogMapper = mqMessageLogMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    public AiTaskResponse createTask(AiTaskRequest request) {
        String messageId = "ai_req_" + UUID.randomUUID().toString().replace("-", "");
        String payloadJson = toJson(request.payload());

        AiTask task = new AiTask();
        task.setTaskType(request.taskType());
        task.setDeviceId(request.deviceId());
        task.setSourceFileId(request.sourceFileId());
        task.setRequestMessageId(messageId);
        task.setTaskStatus(0);
        task.setPayloadJson(payloadJson);
        task.setCreatedAt(LocalDateTime.now());
        aiTaskMapper.insert(task);

        AiRequestMessage message = new AiRequestMessage(
                messageId,
                task.getId(),
                request.taskType(),
                request.deviceId(),
                request.sourceFileId(),
                LocalDateTime.now(),
                request.payload() == null ? Map.of() : request.payload()
        );
        rabbitTemplate.convertAndSend(MqNames.AI_EXCHANGE, MqNames.AI_REQUEST_ROUTING_KEY, toJson(message));

        task.setTaskStatus(1);
        aiTaskMapper.updateById(task);

        MqMessageLog log = new MqMessageLog();
        log.setMessageId(messageId);
        log.setTopic(MqNames.AI_REQUEST_QUEUE);
        log.setConsumeStatus(1);
        log.setCreatedAt(LocalDateTime.now());
        mqMessageLogMapper.insert(log);

        return new AiTaskResponse(task.getId(), messageId, "sent");
    }

    public void handleResult(AiResultMessage message) {
        if (resultAlreadyConsumed(message.messageId())) {
            return;
        }

        String resultJson = toJson(message);
        int status = "success".equalsIgnoreCase(message.resultStatus()) ? 2 : 3;
        AiTask existingTask = aiTaskMapper.selectById(message.taskId());
        if (existingTask == null) {
            throw new BusinessException(404, "AI任务不存在");
        }

        AiTask taskUpdate = new AiTask();
        taskUpdate.setId(message.taskId());
        taskUpdate.setResultMessageId(message.messageId());
        taskUpdate.setTaskStatus(status);
        taskUpdate.setResultJson(resultJson);
        taskUpdate.setErrorMessage(message.errorMessage());
        taskUpdate.setFinishedAt(LocalDateTime.now());
        aiTaskMapper.updateById(taskUpdate);

        persistRawResult(message, existingTask, resultJson);
        if (status == 2) {
            persistBusinessResult(message, existingTask, resultJson);
            persistPredictionAlarmIfNeeded(message, existingTask);
        }

        MqMessageLog log = new MqMessageLog();
        log.setMessageId(message.messageId());
        log.setTopic(MqNames.AI_RESULT_QUEUE);
        log.setConsumeStatus(status == 2 ? 1 : 2);
        log.setErrorMessage(message.errorMessage());
        log.setCreatedAt(LocalDateTime.now());
        mqMessageLogMapper.insert(log);
    }

    public void handleResultJson(String messageJson) {
        try {
            handleResult(objectMapper.readValue(messageJson, AiResultMessage.class));
        } catch (JacksonException exception) {
            throw new BusinessException(400, "AI结果消息JSON解析失败");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JacksonException exception) {
            throw new BusinessException(400, "JSON序列化失败");
        }
    }

    private boolean resultAlreadyConsumed(String messageId) {
        Long count = mqMessageLogMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<MqMessageLog>lambdaQuery()
                        .eq(MqMessageLog::getMessageId, messageId)
        );
        return count > 0;
    }

    private void persistRawResult(AiResultMessage message, AiTask task, String resultJson) {
        Document raw = new Document();
        raw.put("messageId", message.messageId());
        raw.put("taskId", message.taskId());
        raw.put("taskType", task.getTaskType());
        raw.put("deviceId", task.getDeviceId());
        raw.put("resultStatus", message.resultStatus());
        raw.put("payload", Document.parse(resultJson));
        raw.put("createdAt", LocalDateTime.now());
        mongoTemplate.insert(raw, "ai_inference_raw");
    }

    private void persistBusinessResult(AiResultMessage message, AiTask task, String resultJson) {
        if (!isDetectionTask(task.getTaskType())) {
            return;
        }
        if (aiDetectionRecordMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<AiDetectionRecord>lambdaQuery()
                        .eq(AiDetectionRecord::getTaskId, task.getId())
        ) > 0) {
            return;
        }

        for (Map<String, Object> detection : detections(message.detections())) {
            AiDetectionRecord record = new AiDetectionRecord();
            record.setTaskId(task.getId());
            record.setCameraDeviceId(task.getDeviceId());
            record.setPersonnelId(longValue(detection.get("personnelId")));
            record.setDetectType(textValue(firstPresent(detection, "detectType", "type", "className"), "unknown"));
            record.setConfidence(decimalValue(detection.get("confidence")));
            record.setResultJson(toJson(detection));
            record.setOccurredAt(LocalDateTime.now());
            record.setCreatedAt(LocalDateTime.now());
            aiDetectionRecordMapper.insert(record);

            AlarmRecord alarm = createAiAlarm(task, detection, record);
            createViolationIfNeeded(detection, alarm);
        }
    }

    @SuppressWarnings("unchecked")
    private void persistPredictionAlarmIfNeeded(AiResultMessage message, AiTask task) {
        if (!"tower_prediction".equals(task.getTaskType()) || !(message.prediction() instanceof Map<?, ?> rawPrediction)) {
            return;
        }

        Map<String, Object> prediction = (Map<String, Object>) rawPrediction;
        boolean safe = Boolean.TRUE.equals(prediction.get("safe"));
        String riskLevel = textValue(prediction.get("riskLevel"), "normal");
        if (safe && !"alarm".equals(riskLevel) && !"warn".equals(riskLevel)) {
            return;
        }

        BigDecimal riskScore = decimalValue(prediction.get("riskScore"));
        AlarmRecord alarm = new AlarmRecord();
        alarm.setAlarmType("tower_crane");
        alarm.setAlarmLevel("alarm".equals(riskLevel) ? "alarm" : "warn");
        alarm.setDeviceId(task.getDeviceId());
        alarm.setContent("塔吊时序预测风险：" + textValue(prediction.get("reason"), riskLevel));
        alarm.setAlarmValue(riskScore);
        alarm.setOccurredAt(LocalDateTime.now());
        alarm.setStatus(0);
        alarm.setCreatedAt(LocalDateTime.now());
        alarmRecordMapper.insert(alarm);
    }

    private AlarmRecord createAiAlarm(AiTask task, Map<String, Object> detection, AiDetectionRecord record) {
        String detectType = record.getDetectType();
        AlarmRecord alarm = new AlarmRecord();
        alarm.setAlarmType("ai");
        alarm.setAlarmLevel(textValue(detection.get("alarmLevel"), "warn"));
        alarm.setDeviceId(task.getDeviceId());
        alarm.setPersonnelId(record.getPersonnelId());
        alarm.setContent(aiAlarmContent(detection, detectType));
        alarm.setAlarmValue(record.getConfidence());
        alarm.setOccurredAt(record.getOccurredAt());
        alarm.setStatus(0);
        alarm.setSnapshotFileId(task.getSourceFileId());
        alarm.setCreatedAt(LocalDateTime.now());
        alarmRecordMapper.insert(alarm);
        return alarm;
    }

    private void createViolationIfNeeded(Map<String, Object> detection, AlarmRecord alarm) {
        Long personnelId = longValue(detection.get("personnelId"));
        if (personnelId == null) {
            return;
        }

        ViolationRecord violation = new ViolationRecord();
        violation.setPersonnelId(personnelId);
        violation.setViolationItem(aiViolationName(textValue(firstPresent(detection, "detectType", "type", "className"), "AI识别违规")));
        violation.setFineAmount(defaultFine(violation.getViolationItem()));
        violation.setPaymentStatus(0);
        violation.setSourceAlarmId(alarm.getId());
        violation.setOccurredAt(alarm.getOccurredAt());
        violation.setRemark("AI识别结果自动生成");
        violation.setCreatedAt(LocalDateTime.now());
        violationRecordMapper.insert(violation);
    }

    private boolean isDetectionTask(String taskType) {
        return taskType != null && List.of("yolo_detection", "camera_yolo", "face_recognition", "ppe_detection").contains(taskType);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> detections(Object rawDetections) {
        if (rawDetections instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        if (rawDetections instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }

    private Object firstPresent(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String textValue(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private BigDecimal decimalValue(Object value) {
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

    private String aiAlarmContent(Map<String, Object> detection, String detectType) {
        String personnelName = textValue(detection.get("personnelName"), "现场人员");
        return personnelName + "触发" + aiViolationName(detectType);
    }

    private String aiViolationName(String detectType) {
        return switch (detectType) {
            case "no_helmet", "helmet_missing" -> "未佩戴安全帽";
            case "no_vest", "vest_missing" -> "未穿反光衣";
            case "smoke", "smoking" -> "工作现场抽烟";
            case "fire", "open_fire" -> "工作现场明火";
            default -> detectType;
        };
    }

    private BigDecimal defaultFine(String violationItem) {
        return switch (violationItem) {
            case "工作现场抽烟", "工作现场明火" -> BigDecimal.valueOf(100);
            case "未佩戴安全帽", "未穿反光衣" -> BigDecimal.valueOf(50);
            default -> BigDecimal.ZERO;
        };
    }
}
