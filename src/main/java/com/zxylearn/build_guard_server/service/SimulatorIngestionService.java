package com.zxylearn.build_guard_server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SimulatorIngestionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorIngestionService.class);

    private final DeviceMessageListener deviceMessageListener;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private int tick;

    public SimulatorIngestionService(DeviceMessageListener deviceMessageListener,
                                     ObjectMapper objectMapper,
                                     @Value("${buildguard.sim.enabled:true}") boolean enabled,
                                     @Value("${buildguard.sim.base-url:http://127.0.0.1:19099}") String baseUrl) {
        this.deviceMessageListener = deviceMessageListener;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Scheduled(fixedDelayString = "${buildguard.sim.poll-interval-ms:3000}")
    public void pollSimulator() {
        if (!enabled || baseUrl.isBlank()) {
            return;
        }
        boolean includeStatus = tick++ % 10 == 0;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/events/next?status=" + includeStatus))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("simulator poll failed status={} body={}", response.statusCode(), trim(response.body()));
                return;
            }
            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            for (Map<String, Object> message : messages(body.get("messages"))) {
                deviceMessageListener.handleDeviceMessage(message, "sim.device.source");
            }
        } catch (Exception exception) {
            LOGGER.warn("simulator poll skipped: {}", exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200);
    }
}
