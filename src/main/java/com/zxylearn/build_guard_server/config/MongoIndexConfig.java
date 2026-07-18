package com.zxylearn.build_guard_server.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
public class MongoIndexConfig {
    private static final String[] DEVICE_COLLECTIONS = {
            "device_telemetry_raw",
            "tower_crane_telemetry",
            "elevator_telemetry",
            "formwork_telemetry",
            "deep_pit_telemetry",
            "environment_telemetry",
            "camera_frame_event"
    };

    private final MongoTemplate mongoTemplate;

    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        for (String collection : DEVICE_COLLECTIONS) {
            mongoTemplate.indexOps(collection).createIndex(
                    new Index()
                            .on("deviceCode", Sort.Direction.ASC)
                            .on("reportedAt", Sort.Direction.DESC)
                            .named(collection + "_device_time_idx")
            );
        }

        mongoTemplate.indexOps("ai_inference_raw").createIndex(
                new Index()
                        .on("taskId", Sort.Direction.ASC)
                        .on("createdAt", Sort.Direction.DESC)
                        .named("ai_inference_task_time_idx")
        );
    }
}
