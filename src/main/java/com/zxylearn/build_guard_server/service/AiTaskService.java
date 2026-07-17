package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.config.MqNames;
import com.zxylearn.build_guard_server.dto.AiDtos.AiRequestMessage;
import com.zxylearn.build_guard_server.dto.AiDtos.AiResultMessage;
import com.zxylearn.build_guard_server.dto.AiDtos.AiTaskRequest;
import com.zxylearn.build_guard_server.dto.AiDtos.AiTaskResponse;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class AiTaskService {
    private final JdbcClient jdbcClient;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AiTaskService(JdbcClient jdbcClient, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public AiTaskResponse createTask(AiTaskRequest request) {
        String messageId = "ai_req_" + UUID.randomUUID().toString().replace("-", "");
        String payloadJson = toJson(request.payload());

        Long taskId = jdbcClient.sql("""
                        insert into ai_task(task_type, device_id, source_file_id, request_message_id, task_status, payload_json)
                        values (:taskType, :deviceId, :sourceFileId, :messageId, 0, :payload)
                        """)
                .param("taskType", request.taskType())
                .param("deviceId", request.deviceId())
                .param("sourceFileId", request.sourceFileId())
                .param("messageId", messageId)
                .param("payload", payloadJson)
                .update()
                .keyHolder()
                .getKeyAs(Long.class);

        AiRequestMessage message = new AiRequestMessage(
                messageId,
                taskId,
                request.taskType(),
                request.deviceId(),
                request.sourceFileId(),
                LocalDateTime.now(),
                request.payload() == null ? Map.of() : request.payload()
        );
        rabbitTemplate.convertAndSend(MqNames.AI_EXCHANGE, MqNames.AI_REQUEST_ROUTING_KEY, toJson(message));

        jdbcClient.sql("update ai_task set task_status = 1 where id = :id")
                .param("id", taskId)
                .update();
        jdbcClient.sql("""
                        insert into mq_message_log(message_id, topic, device_code, consume_status)
                        values (:messageId, :topic, null, 1)
                        """)
                .param("messageId", messageId)
                .param("topic", MqNames.AI_REQUEST_QUEUE)
                .update();

        return new AiTaskResponse(taskId, messageId, "sent");
    }

    public void handleResult(AiResultMessage message) {
        String resultJson = toJson(message);
        int status = "success".equalsIgnoreCase(message.resultStatus()) ? 2 : 3;
        jdbcClient.sql("""
                        update ai_task
                        set result_message_id = :resultMessageId,
                            task_status = :status,
                            result_json = :resultJson,
                            error_message = :errorMessage,
                            finished_at = :finishedAt
                        where id = :taskId
                        """)
                .param("resultMessageId", message.messageId())
                .param("status", status)
                .param("resultJson", resultJson)
                .param("errorMessage", message.errorMessage())
                .param("finishedAt", LocalDateTime.now())
                .param("taskId", message.taskId())
                .update();

        jdbcClient.sql("""
                        insert into mq_message_log(message_id, topic, consume_status, error_message)
                        values (:messageId, :topic, :consumeStatus, :errorMessage)
                        """)
                .param("messageId", message.messageId())
                .param("topic", MqNames.AI_RESULT_QUEUE)
                .param("consumeStatus", status == 2 ? 1 : 2)
                .param("errorMessage", message.errorMessage())
                .update();
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
}
