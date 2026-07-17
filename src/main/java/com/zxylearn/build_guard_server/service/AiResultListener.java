package com.zxylearn.build_guard_server.service;

import com.zxylearn.build_guard_server.config.MqNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiResultListener {
    private final AiTaskService aiTaskService;

    public AiResultListener(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @RabbitListener(queues = MqNames.AI_RESULT_QUEUE)
    public void onAiResult(String messageJson) {
        aiTaskService.handleResultJson(messageJson);
    }
}
