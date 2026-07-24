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
import com.zxylearn.build_guard_server.entity.DeviceAsset;
import com.zxylearn.build_guard_server.entity.FileResource;
import com.zxylearn.build_guard_server.entity.MqMessageLog;
import com.zxylearn.build_guard_server.entity.PersonnelFace;
import com.zxylearn.build_guard_server.entity.ViolationRecord;
import com.zxylearn.build_guard_server.mapper.AiDetectionRecordMapper;
import com.zxylearn.build_guard_server.mapper.AiTaskMapper;
import com.zxylearn.build_guard_server.mapper.AlarmRecordMapper;
import com.zxylearn.build_guard_server.mapper.DeviceAssetMapper;
import com.zxylearn.build_guard_server.mapper.MqMessageLogMapper;
import com.zxylearn.build_guard_server.mapper.PersonnelFaceMapper;
import com.zxylearn.build_guard_server.mapper.ViolationRecordMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.bson.Document;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiTaskService {
    private final AiTaskMapper aiTaskMapper;
    private final AiDetectionRecordMapper aiDetectionRecordMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final ViolationRecordMapper violationRecordMapper;
    private final DeviceAssetMapper deviceAssetMapper;
    private final MqMessageLogMapper mqMessageLogMapper;
    private final PersonnelFaceMapper personnelFaceMapper;
    private final RabbitTemplate rabbitTemplate;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;

    public AiTaskService(AiTaskMapper aiTaskMapper,
                         AiDetectionRecordMapper aiDetectionRecordMapper,
                         AlarmRecordMapper alarmRecordMapper,
                         ViolationRecordMapper violationRecordMapper,
                         DeviceAssetMapper deviceAssetMapper,
                         MqMessageLogMapper mqMessageLogMapper,
                         PersonnelFaceMapper personnelFaceMapper,
                         RabbitTemplate rabbitTemplate,
                         MongoTemplate mongoTemplate,
                         ObjectMapper objectMapper,
                         FileStorageService fileStorageService) {
        this.aiTaskMapper = aiTaskMapper;
        this.aiDetectionRecordMapper = aiDetectionRecordMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.violationRecordMapper = violationRecordMapper;
        this.deviceAssetMapper = deviceAssetMapper;
        this.mqMessageLogMapper = mqMessageLogMapper;
        this.personnelFaceMapper = personnelFaceMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
        this.fileStorageService = fileStorageService;
    }

    public AiTaskResponse createTask(AiTaskRequest request) {
        Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
        String deviceCode = textValue(payload.get("deviceCode"), null);
        String deviceType = textValue(payload.get("deviceType"), null);
        Map<String, Object> sourceFile = mapValue(payload.get("sourceFile"));
        return createAndPublishTask(
                request.taskType(),
                request.deviceId(),
                deviceCode,
                deviceType,
                request.sourceFileId(),
                sourceFile,
                payload
        );
    }

    public AiTaskResponse createCameraFrameTask(Map<String, Object> deviceMessage) {
        String deviceCode = textValue(deviceMessage.get("deviceCode"), null);
        DeviceAsset device = findDevice(deviceCode);
        if (device == null) {
            return null;
        }

        Map<String, Object> payload = payload(deviceMessage);
        Map<String, Object> sourceFile = mapValue(payload.get("sourceFile"));
        Long sourceFileId = null;
        FileResource snapshot = storeCameraSnapshot(device.getId(), sourceFile, payload);
        if (snapshot != null) {
            sourceFileId = snapshot.getId();
            sourceFile = new java.util.LinkedHashMap<>(sourceFile);
            sourceFile.put("fileId", snapshot.getId());
            sourceFile.put("url", snapshot.getUrl());
            sourceFile.put("objectKey", snapshot.getObjectKey());
            sourceFile.put("contentType", snapshot.getContentType());
        }
        Map<String, Object> aiPayload = new java.util.LinkedHashMap<>(payload);
        aiPayload.put("deviceCode", deviceCode);
        aiPayload.put("deviceType", "camera");
        aiPayload.put("cameraFrameMessageId", deviceMessage.get("messageId"));
        aiPayload.put("occurredAt", deviceMessage.get("occurredAt"));
        aiPayload.put("sourceFile", sourceFile);

        return createAndPublishTask(
                "camera_yolo",
                device.getId(),
                deviceCode,
                "camera",
                sourceFileId,
                sourceFile,
                aiPayload
        );
    }

    public AiTaskResponse createTowerPredictionTask(Map<String, Object> deviceMessage) {
        return createPredictionTask(deviceMessage);
    }

    public AiTaskResponse createPredictionTask(Map<String, Object> deviceMessage) {
        String deviceCode = textValue(deviceMessage.get("deviceCode"), null);
        String deviceType = textValue(deviceMessage.get("deviceType"), "device");
        DeviceAsset device = findDevice(deviceCode);
        if (device == null) {
            return null;
        }

        Map<String, Object> telemetry = payload(deviceMessage);
        Map<String, Object> aiPayload = new java.util.LinkedHashMap<>();
        aiPayload.put("deviceCode", deviceCode);
        aiPayload.put("deviceType", deviceType);
        aiPayload.put("telemetryMessageId", deviceMessage.get("messageId"));
        aiPayload.put("occurredAt", deviceMessage.get("occurredAt"));
        aiPayload.put("telemetry", List.of(telemetry));

        return createAndPublishTask(
                deviceType + "_prediction",
                device.getId(),
                deviceCode,
                deviceType,
                null,
                Map.of(),
                aiPayload
        );
    }

    public AiTaskResponse createFaceRegisterTask(Long personnelId, FileResource sourceFile) {
        if (personnelId == null || sourceFile == null) {
            return null;
        }
        Map<String, Object> sourceFilePayload = new java.util.LinkedHashMap<>();
        sourceFilePayload.put("fileId", sourceFile.getId());
        sourceFilePayload.put("url", sourceFile.getUrl());
        sourceFilePayload.put("objectKey", sourceFile.getObjectKey());
        sourceFilePayload.put("contentType", sourceFile.getContentType());

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("personnelId", personnelId);
        payload.put("sourceFile", sourceFilePayload);

        return createAndPublishTask(
                "face_register",
                null,
                null,
                "personnel",
                sourceFile.getId(),
                sourceFilePayload,
                payload
        );
    }

    private AiTaskResponse createAndPublishTask(String taskType,
                                                Long deviceId,
                                                String deviceCode,
                                                String deviceType,
                                                Long sourceFileId,
                                                Map<String, Object> sourceFile,
                                                Map<String, Object> payload) {
        String messageId = "ai_req_" + UUID.randomUUID().toString().replace("-", "");
        String payloadJson = toJson(payload);

        AiTask task = new AiTask();
        task.setTaskType(taskType);
        task.setDeviceId(deviceId);
        task.setSourceFileId(sourceFileId);
        task.setRequestMessageId(messageId);
        task.setTaskStatus(0);
        task.setPayloadJson(payloadJson);
        task.setCreatedAt(LocalDateTime.now());
        aiTaskMapper.insert(task);

        AiRequestMessage message = new AiRequestMessage(
                messageId,
                task.getId(),
                taskType,
                deviceId,
                deviceCode,
                deviceType,
                sourceFileId,
                sourceFile,
                LocalDateTime.now(),
                payload == null ? Map.of() : payload
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

    private DeviceAsset findDevice(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return null;
        }
        return deviceAssetMapper.selectOne(Wrappers.<DeviceAsset>lambdaQuery()
                .eq(DeviceAsset::getCode, deviceCode.trim())
                .last("limit 1"));
    }

    private FileResource storeCameraSnapshot(Long deviceId, Map<String, Object> sourceFile, Map<String, Object> payload) {
        String localPath = textValue(firstPresent(sourceFile, "localPath", "path"), null);
        if (localPath == null) {
            localPath = textValue(payload.get("localPath"), null);
        }
        if (localPath == null || localPath.startsWith("http://") || localPath.startsWith("https://") || localPath.startsWith("file:")) {
            return null;
        }
        String contentType = textValue(firstPresent(sourceFile, "contentType", "mimeType"), "image/jpeg");
        try {
            return fileStorageService.savePath(
                    Path.of(localPath),
                    contentType,
                    Path.of(localPath).getFileName().toString(),
                    "camera_snapshot",
                    deviceId,
                    "camera/snapshot"
            );
        } catch (RuntimeException ignored) {
            return null;
        }
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
            updateFaceRegistrationIfNeeded(message, existingTask);
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

        FileResource annotatedSnapshot = storeAiAnnotatedImage(message, task);
        Long snapshotFileId = annotatedSnapshot == null ? task.getSourceFileId() : annotatedSnapshot.getId();

        for (Map<String, Object> detection : detections(message.detections())) {
            AiDetectionRecord record = new AiDetectionRecord();
            record.setTaskId(task.getId());
            record.setCameraDeviceId(task.getDeviceId());
            record.setPersonnelId(longValue(detection.get("personnelId")));
            record.setDetectType(textValue(firstPresent(detection, "detectType", "type", "className"), "unknown"));
            record.setConfidence(decimalValue(detection.get("confidence")));
            record.setSnapshotFileId(snapshotFileId);
            record.setResultJson(toJson(detection));
            record.setOccurredAt(LocalDateTime.now());
            record.setCreatedAt(LocalDateTime.now());
            aiDetectionRecordMapper.insert(record);

            AlarmRecord alarm = createAiAlarm(task, detection, record);
            record.setSourceAlarmId(alarm.getId());
            aiDetectionRecordMapper.updateById(record);
            createPendingViolation(detection, record, alarm);
        }
    }

    private void createPendingViolation(Map<String, Object> detection, AiDetectionRecord record, AlarmRecord alarm) {
        if (alarm == null || !isViolationDetection(record.getDetectType())) {
            return;
        }
        Long existing = violationRecordMapper.selectCount(Wrappers.<ViolationRecord>lambdaQuery()
                .eq(ViolationRecord::getSourceAlarmId, alarm.getId()));
        if (existing != null && existing > 0) {
            return;
        }
        ViolationRecord violation = new ViolationRecord();
        violation.setPersonnelId(record.getPersonnelId());
        violation.setViolationItem(aiViolationName(record.getDetectType()));
        violation.setFineAmount(defaultFine(record.getDetectType()));
        violation.setPaymentStatus(0);
        violation.setSourceAlarmId(alarm.getId());
        violation.setOccurredAt(record.getOccurredAt() == null ? LocalDateTime.now() : record.getOccurredAt());
        violation.setRemark("AI识别结果待管理员审核");
        violation.setReviewStatus(0);
        violation.setCreatedAt(LocalDateTime.now());
        violation.setUpdatedAt(LocalDateTime.now());
        violationRecordMapper.insert(violation);
    }

    @SuppressWarnings("unchecked")
    private void updateFaceRegistrationIfNeeded(AiResultMessage message, AiTask task) {
        if (!"face_register".equals(task.getTaskType()) || !(message.rawResult() instanceof Map<?, ?> raw)) {
            return;
        }
        Long personnelId = longValue(raw.get("id"));
        if (personnelId == null) {
            try {
                Map<String, Object> payload = objectMapper.readValue(task.getPayloadJson(), new tools.jackson.core.type.TypeReference<>() {
                });
                personnelId = longValue(payload.get("personnelId"));
            } catch (Exception ignored) {
                return;
            }
        }
        if (personnelId == null) {
            return;
        }
        PersonnelFace face = personnelFaceMapper.selectOne(Wrappers.<PersonnelFace>lambdaQuery()
                .eq(PersonnelFace::getPersonnelId, personnelId)
                .last("limit 1"));
        if (face == null) {
            face = new PersonnelFace();
            face.setPersonnelId(personnelId);
            face.setFaceRef("personnel:" + personnelId);
            face.setRegisteredAt(LocalDateTime.now());
            face.setStatus(1);
            personnelFaceMapper.insert(face);
            return;
        }
        face.setRegisteredAt(LocalDateTime.now());
        face.setStatus(1);
        personnelFaceMapper.updateById(face);
    }

    @SuppressWarnings("unchecked")
    private void persistPredictionAlarmIfNeeded(AiResultMessage message, AiTask task) {
        if (task.getTaskType() == null || !task.getTaskType().endsWith("_prediction") || !(message.prediction() instanceof Map<?, ?> rawPrediction)) {
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
        alarm.setAlarmType(task.getTaskType().replace("_prediction", ""));
        alarm.setAlarmLevel("alarm".equals(riskLevel) ? "alarm" : "warn");
        alarm.setDeviceId(task.getDeviceId());
        alarm.setContent("AI时序预测风险：" + textValue(prediction.get("reason"), riskLevel));
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
        alarm.setSnapshotFileId(record.getSnapshotFileId() == null ? task.getSourceFileId() : record.getSnapshotFileId());
        alarm.setCreatedAt(LocalDateTime.now());
        alarmRecordMapper.insert(alarm);
        return alarm;
    }

    private FileResource storeAiAnnotatedImage(AiResultMessage message, AiTask task) {
        Map<String, Object> image = mapValue(message.annotatedImage());
        if (image.isEmpty()) {
            return null;
        }
        Long fileId = longValue(image.get("fileId"));
        if (fileId != null) {
            return fileStorageService.findById(fileId);
        }
        String localPath = textValue(firstPresent(image, "localPath", "path"), null);
        if (localPath != null) {
            return fileStorageService.savePath(
                    Path.of(localPath),
                    textValue(image.get("contentType"), "image/jpeg"),
                    textValue(image.get("fileName"), "ai-detection-" + task.getId() + ".jpg"),
                    "ai_detection_result",
                    task.getDeviceId(),
                    "ai/detection"
            );
        }
        String encoded = textValue(firstPresent(image, "base64", "data"), null);
        if (encoded == null) {
            return null;
        }
        int comma = encoded.indexOf(',');
        if (comma >= 0) {
            encoded = encoded.substring(comma + 1);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return fileStorageService.saveBytes(
                    bytes,
                    textValue(image.get("contentType"), "image/jpeg"),
                    textValue(image.get("fileName"), "ai-detection-" + task.getId() + ".jpg"),
                    "ai_detection_result",
                    task.getDeviceId(),
                    "ai/detection"
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isDetectionTask(String taskType) {
        return taskType != null && List.of("yolo_detection", "camera_yolo", "ppe_detection").contains(taskType);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> message) {
        if (message.get("payload") instanceof Map<?, ?> rawPayload) {
            return (Map<String, Object>) rawPayload;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
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

    private boolean isViolationDetection(String detectType) {
        String value = detectType == null ? "" : detectType.toLowerCase();
        return value.contains("helmet")
                || value.contains("vest")
                || value.contains("reflective")
                || value.contains("smok")
                || value.contains("fire");
    }

    private BigDecimal defaultFine(String detectType) {
        return switch (aiViolationName(detectType)) {
            case "工作现场抽烟", "工作现场明火" -> BigDecimal.valueOf(100);
            case "未佩戴安全帽", "未穿反光衣" -> BigDecimal.valueOf(50);
            default -> BigDecimal.ZERO;
        };
    }
}
