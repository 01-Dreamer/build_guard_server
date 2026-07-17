package com.zxylearn.build_guard_server.config;

public final class MqNames {
    public static final String AI_EXCHANGE = "buildguard.ai";
    public static final String AI_REQUEST_ROUTING_KEY = "ai.request";
    public static final String AI_RESULT_ROUTING_KEY = "ai.result";
    public static final String AI_REQUEST_QUEUE = "buildguard.ai.request";
    public static final String AI_RESULT_QUEUE = "buildguard.ai.result";

    private MqNames() {
    }
}
