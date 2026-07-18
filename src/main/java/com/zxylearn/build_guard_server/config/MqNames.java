package com.zxylearn.build_guard_server.config;

public final class MqNames {
    public static final String AI_EXCHANGE = "buildguard.ai";
    public static final String AI_REQUEST_ROUTING_KEY = "ai.request";
    public static final String AI_RESULT_ROUTING_KEY = "ai.result";
    public static final String AI_REQUEST_QUEUE = "buildguard.ai.request";
    public static final String AI_RESULT_QUEUE = "buildguard.ai.result";

    public static final String DEVICE_EXCHANGE = "buildguard.device";
    public static final String DEVICE_TELEMETRY_ROUTING_KEY = "buildguard.device.telemetry";
    public static final String DEVICE_STATUS_ROUTING_KEY = "buildguard.device.status";
    public static final String CAMERA_FRAME_ROUTING_KEY = "buildguard.camera.frame";
    public static final String DEVICE_TELEMETRY_QUEUE = "buildguard.device.telemetry";
    public static final String DEVICE_STATUS_QUEUE = "buildguard.device.status";
    public static final String CAMERA_FRAME_QUEUE = "buildguard.camera.frame";

    private MqNames() {
    }
}
