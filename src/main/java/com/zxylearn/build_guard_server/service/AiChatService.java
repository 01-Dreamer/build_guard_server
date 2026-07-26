package com.zxylearn.build_guard_server.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import tools.jackson.databind.ObjectMapper;
import com.zxylearn.build_guard_server.config.AiChatConfig;
import com.zxylearn.build_guard_server.dto.AiChatDtos.ChatMessage;
import com.zxylearn.build_guard_server.dto.PageDtos.AlarmView;
import com.zxylearn.build_guard_server.dto.PageDtos.DashboardOverview;
import com.zxylearn.build_guard_server.dto.PageDtos.DeviceCard;
import com.zxylearn.build_guard_server.dto.PageDtos.DeviceOverview;
import com.zxylearn.build_guard_server.dto.PageDtos.EnvironmentRealtimeView;
import com.zxylearn.build_guard_server.dto.PageDtos.MetricValue;
import com.zxylearn.build_guard_server.entity.AlarmRecord;
import com.zxylearn.build_guard_server.entity.DeviceAsset;
import com.zxylearn.build_guard_server.entity.DeviceType;
import com.zxylearn.build_guard_server.mapper.AlarmRecordMapper;
import com.zxylearn.build_guard_server.mapper.DeviceAssetMapper;
import com.zxylearn.build_guard_server.mapper.DeviceTypeMapper;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI 智能问答服务。
 *
 * 核心流程：
 * 1. 先以非流式模式调用 LLM（带 Function Definitions），检测是否需要工具调用
 * 2. 如有 tool_calls → 执行本地工具查询 → 将结果追加到消息 → 再次流式调用 LLM
 * 3. 流式返回：将 LLM 的 SSE 事件逐块转发给前端，实现逐字显示效果
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SYSTEM_PROMPT = """
            你是"BuildGuard 助手"，是"建筑安全智能监控平台"的 AI 助手。
            你的职责是帮助管理员查询工地设备状态、环境数据、报警情况和 AI 风险识别结果。

            ## 回答规则
            1. 用中文回答，语气专业、简洁
            2. 涉及实时数据时，必须先调用函数获取数据再回答
            3. 列出具体数值，不要只说"正常"或"异常"
            4. 数据为空时如实告知"当前没有相关数据"
            5. 当前时间是 %s，不要编造数据

            ## Markdown 格式要求（重要！）
            回复必须使用标准 Markdown，遵守以下规范：

            **表格格式**（必须包含分隔行！）：
            | 设备名称 | 编号 | 状态 |
            |---------|------|------|
            | 1号塔吊 | TC-001 | 在线 |
            | 1号升降机 | EL-001 | 在线 |

            **分级标题**：## 后必须加空格，如 "## 设备运行状态"
            **重点强调**：数值用 **粗体** 包裹，如 **705 kN·m**
            **列表**：用 - 开头
            **分隔**：段落之间空一行

            不要使用 emoji 表情符号。
            表格必须有表头分隔行（|----|----|）。
            """;

    private static final int MAX_HISTORY_TURNS = 10;

    private final AiChatConfig config;
    private final PlatformPageService platformPageService;
    private final DeviceAssetMapper deviceAssetMapper;
    private final DeviceTypeMapper deviceTypeMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<String, String> TELEMETRY_COLLECTIONS = Map.of(
            "tower_crane", "tower_crane_telemetry",
            "elevator", "elevator_telemetry",
            "formwork", "formwork_telemetry",
            "deep_pit", "deep_pit_telemetry",
            "environment_sensor", "environment_telemetry"
    );

    public AiChatService(AiChatConfig config,
                         PlatformPageService platformPageService,
                         DeviceAssetMapper deviceAssetMapper,
                         DeviceTypeMapper deviceTypeMapper,
                         AlarmRecordMapper alarmRecordMapper,
                         MongoTemplate mongoTemplate,
                         ObjectMapper objectMapper) {
        this.config = config;
        this.platformPageService = platformPageService;
        this.deviceAssetMapper = deviceAssetMapper;
        this.deviceTypeMapper = deviceTypeMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.mongoTemplate = mongoTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理用户消息并以 SSE 流式返回 AI 回复。
     *
     * @param userMessage 用户输入
     * @param history     历史对话（可为空）
     * @param emitter     SSE 发射器，逐块推送回复内容
     * @return 更新后的对话历史
     */
    public List<ChatMessage> chatStream(String userMessage, List<ChatMessage> history, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 构建消息列表
                List<Map<String, Object>> messages = buildMessages(userMessage, history);

                // 2. 首次非流式调用：检测是否需要工具调用
                Map<String, Object> firstResponse = callLlm(messages, true, false);
                if (firstResponse == null) {
                    sendSseEvent(emitter, "error", "AI 服务返回为空");
                    emitter.complete();
                    return;
                }

                // 3. 处理 tool_calls（如有）
                boolean hasToolCalls = handleToolCalls(firstResponse, messages);
                if (hasToolCalls) {
                    // 工具调用后再次非流式检测（防止 LLM 还要调工具）
                    Map<String, Object> secondResponse = callLlm(messages, false, false);
                    if (secondResponse != null) {
                        hasToolCalls = handleToolCalls(secondResponse, messages);
                    }
                }

                // 4. 流式调用 LLM 生成最终回复
                streamLlmResponse(messages, emitter);

            } catch (Exception e) {
                log.error("AI chat stream failed", e);
                sendSseEvent(emitter, "error", "抱歉，AI 服务暂时不可用：" + e.getMessage());
                emitter.complete();
            }
        });
        return history; // 前端自行维护历史
    }

    /**
     * 处理非流式响应中的工具调用，返回是否执行了工具。
     */
    private boolean handleToolCalls(Map<String, Object> response, List<Map<String, Object>> messages) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) return false;

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> msg = (Map<String, Object>) choice.get("message");
        if (msg == null) return false;

        Object toolCallsObj = msg.get("tool_calls");
        if (!(toolCallsObj instanceof List<?> toolCallsList) || toolCallsList.isEmpty()) {
            return false;
        }

        log.info("LLM requested {} tool call(s)", toolCallsList.size());
        messages.add(msg);

        for (Object tcObj : toolCallsList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tc = (Map<String, Object>) tcObj;
            String toolCallId = (String) tc.get("id");
            @SuppressWarnings("unchecked")
            Map<String, Object> func = (Map<String, Object>) tc.get("function");
            String funcName = (String) func.get("name");
            String funcArgs = (String) func.get("arguments");

            String result = executeToolCall(funcName, funcArgs);
            messages.add(Map.of(
                    "role", "tool",
                    "tool_call_id", toolCallId,
                    "content", result
            ));
            log.info("Tool {} executed, result length={}", funcName, result.length());
        }
        return true;
    }

    // ---- LLM API 调用 ----

    /**
     * 调用 LLM Chat Completions API。
     *
     * @param messages   消息列表
     * @param withTools  是否附带 tools 定义
     * @param stream     是否启用流式返回
     * @return 非流式时返回完整响应 Map，流式时返回 null（结果通过 emitter 推送）
     */
    private Map<String, Object> callLlm(List<Map<String, Object>> messages, boolean withTools, boolean stream) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("messages", messages);
            body.put("temperature", 0.3);
            body.put("max_tokens", 1024);

            if (withTools) {
                body.put("tools", buildToolDefinitions());
                body.put("tool_choice", "auto");
            }

            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = (HttpURLConnection) new URL(config.chatCompletionsUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + config.apiKey());
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(60_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() >= 400) {
                log.error("LLM API error: {} {}", conn.getResponseCode(), conn.getResponseMessage());
                return null;
            }

            // 非流式：直接解析 JSON
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(sb.toString(), Map.class);
                return result;
            }
        } catch (Exception e) {
            log.error("LLM API call failed", e);
            return null;
        }
    }

    /**
     * 流式调用 LLM，将每个 content delta 通过 emitter 推送给前端。
     */
    private void streamLlmResponse(List<Map<String, Object>> messages, SseEmitter emitter) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", config.model());
            body.put("messages", messages);
            body.put("temperature", 0.3);
            body.put("max_tokens", 1024);
            body.put("stream", true);  // 关键：启用流式返回

            String json = objectMapper.writeValueAsString(body);
            HttpURLConnection conn = (HttpURLConnection) new URL(config.chatCompletionsUrl()).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + config.apiKey());
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30_000);
            conn.setReadTimeout(90_000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() >= 400) {
                sendSseEvent(emitter, "error", "AI API error: " + conn.getResponseCode());
                emitter.complete();
                return;
            }

            // 解析 SSE 流：每行以 "data: " 开头，内容是 JSON
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6);
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> chunk = objectMapper.readValue(data, Map.class);
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                                if (delta != null) {
                                    Object content = delta.get("content");
                                    if (content instanceof String text && !text.isEmpty()) {
                                        sendSseEvent(emitter, "text", text);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                            // 跳过无法解析的 chunk
                        }
                    }
                }
            }

            sendSseEvent(emitter, "done", "");
            emitter.complete();

        } catch (Exception e) {
            log.error("Streaming LLM call failed", e);
            sendSseEvent(emitter, "error", "流式调用失败: " + e.getMessage());
            emitter.complete();
        }
    }

    // ---- SSE 事件发送 ----

    private void sendSseEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data));
        } catch (Exception e) {
            log.debug("SSE send failed (client may have disconnected): {}", e.getMessage());
        }
    }

    // ---- 消息构建 ----

    private List<Map<String, Object>> buildMessages(String userMessage, List<ChatMessage> history) {
        List<Map<String, Object>> messages = new ArrayList<>();

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT.formatted(now)));

        if (history != null) {
            for (ChatMessage msg : history) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", msg.role());
                if (msg.content() != null) m.put("content", msg.content());
                if (msg.toolCallId() != null) m.put("tool_call_id", msg.toolCallId());
                messages.add(m);
            }
        }

        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    // ---- Tool Definitions ----

    private List<Map<String, Object>> buildToolDefinitions() {
        return List.of(
                toolDef("get_dashboard_summary", "查询仪表盘摘要：设备总数、在线设备数、未处理报警数、AI风险数、当前环境指标", Map.of()),
                toolDef("get_device_overview", "查询施工设备统计概览：各类型设备（塔吊、升降机、高支模、深基坑）数量、在线数量、报警/预警数量", Map.of()),
                toolDef("get_environment_data", "查询工地环境实时监测数据：PM2.5、PM10、噪音、温度、湿度、风速、风向、TSP等所有指标", Map.of()),
                toolDef("get_recent_alarms", "查询最近的报警记录列表", Map.of("type", prop("string", "报警类型。可选值: tower_crane, elevator, formwork, deep_pit, environment, ai。不传则返回所有类型"))),
                toolDef("get_device_detail", "查询指定设备编号的详细信息和最新遥测数据", Map.of("deviceCode", prop("string", "设备编号，如 TC-001(塔吊), EL-001(升降机), ENV-001(环境), GZM-361(高支模), JK-362(深基坑), CAM-001(摄像头), SP-001(喷淋)"))),
                toolDef("get_ai_risk_summary", "查询AI风险识别记录，包括未戴安全帽、未穿反光衣等违规检测结果", Map.of())
        );
    }

    private Map<String, Object> toolDef(String name, String desc, Map<String, Object> props) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", new ArrayList<>(props.keySet()));

        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", name);
        func.put("description", desc);
        func.put("parameters", params);

        return Map.of("type", "function", "function", func);
    }

    private Map<String, Object> prop(String type, String desc) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", desc);
        return p;
    }

    // ---- Tool Execution ----

    private String executeToolCall(String name, String args) {
        try {
            return switch (name) {
                case "get_dashboard_summary" -> executeGetDashboardSummary();
                case "get_device_overview" -> executeGetDeviceOverview();
                case "get_environment_data" -> executeGetEnvironmentData();
                case "get_recent_alarms" -> executeGetRecentAlarms(args);
                case "get_device_detail" -> executeGetDeviceDetail(args);
                case "get_ai_risk_summary" -> executeGetAiRiskSummary();
                default -> "{\"error\":\"未知函数: " + name + "\"}";
            };
        } catch (Exception e) {
            log.error("Tool {} failed", name, e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String executeGetDashboardSummary() {
        DashboardOverview o = platformPageService.dashboardOverview();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"设备总数\":").append(o.totalDevices());
        sb.append(",\"在线设备\":").append(o.onlineDevices());
        sb.append(",\"未处理报警\":").append(o.openAlarms());
        sb.append(",\"AI风险数\":").append(o.aiRisks());
        sb.append(",\"环境指标\":{");
        if (o.environment() != null) {
            for (MetricValue m : o.environment()) {
                sb.append("\"").append(m.name()).append("\":{\"值\":").append(m.value());
                if (m.unit() != null) sb.append(",\"单位\":\"").append(m.unit()).append("\"");
                sb.append("},");
            }
        }
        sb.append("},\"最近报警\":[");
        if (o.latestAlarms() != null) {
            for (AlarmView a : o.latestAlarms()) {
                sb.append("{\"内容\":\"").append(a.content()).append("\",\"级别\":\"").append(a.alarmLevel()).append("\",\"设备\":\"")
                        .append(a.deviceName() != null ? a.deviceName() : "-").append("\",\"状态\":")
                        .append(a.status() != null && a.status() == 2 ? "\"已处理\"" : "\"未处理\"").append("},");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String executeGetDeviceOverview() {
        DeviceOverview o = platformPageService.deviceOverview();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"设备总数\":").append(o.total()).append(",\"在线\":").append(o.online())
                .append(",\"报警\":").append(o.totalAlarms()).append(",\"预警\":").append(o.totalWarnings())
                .append(",\"塔吊\":").append(o.towerCranes()).append(",\"升降机\":").append(o.elevators())
                .append(",\"高支模\":").append(o.formworks()).append(",\"深基坑\":").append(o.deepPits())
                .append(",\"设备列表\":[");
        if (o.devices() != null) {
            for (DeviceCard d : o.devices()) {
                sb.append("{\"名称\":\"").append(d.name()).append("\",\"编号\":\"").append(d.code())
                        .append("\",\"类型\":\"").append(d.typeName()).append("\",\"位置\":\"").append(d.locationName())
                        .append("\",\"在线\":").append(Integer.valueOf(1).equals(d.onlineStatus())).append("},");
            }
        }
        sb.append("]}");
        return sb.toString();
    }

    private String executeGetEnvironmentData() {
        EnvironmentRealtimeView env = platformPageService.environmentRealtime(null);
        if (env == null || env.metrics() == null) return "{\"message\":\"暂无环境数据\"}";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"设备\":\"").append(env.deviceName()).append("\",\"监测值\":{");
        for (MetricValue m : env.metrics()) {
            sb.append("\"").append(m.name()).append("\":{").append("\"值\":").append(m.value());
            if (m.unit() != null) sb.append(",\"单位\":\"").append(m.unit()).append("\"");
            if (m.status() != null) sb.append(",\"状态\":\"").append(m.status()).append("\"");
            sb.append("},");
        }
        sb.append("}}");
        return sb.toString();
    }

    private String executeGetRecentAlarms(String args) {
        String alarmType = extractArg(args, "type");
        List<AlarmRecord> alarms;
        if (alarmType != null && !alarmType.isBlank()) {
            alarms = alarmRecordMapper.selectList(
                    Wrappers.<AlarmRecord>lambdaQuery().eq(AlarmRecord::getAlarmType, alarmType.trim())
                            .orderByDesc(AlarmRecord::getOccurredAt).last("LIMIT 10"));
        } else {
            alarms = alarmRecordMapper.selectList(
                    Wrappers.<AlarmRecord>lambdaQuery().orderByDesc(AlarmRecord::getOccurredAt).last("LIMIT 10"));
        }
        if (alarms.isEmpty()) return "{\"message\":\"当前没有报警记录\",\"count\":0}";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"count\":").append(alarms.size()).append(",\"alarms\":[");
        for (AlarmRecord a : alarms) {
            sb.append("{\"类型\":\"").append(a.getAlarmType()).append("\",\"级别\":\"").append(a.getAlarmLevel())
                    .append("\",\"内容\":\"").append(a.getContent()).append("\",\"数值\":").append(a.getAlarmValue())
                    .append(",\"时间\":\"").append(a.getOccurredAt()).append("\",\"状态\":")
                    .append(Integer.valueOf(2).equals(a.getStatus()) ? "\"已处理\"" : "\"未处理\"").append("},");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String executeGetDeviceDetail(String args) {
        String deviceCode = extractArg(args, "deviceCode");
        if (deviceCode == null || deviceCode.isBlank()) return "{\"error\":\"请提供设备编号 deviceCode\"}";
        DeviceAsset device = deviceAssetMapper.selectOne(
                Wrappers.<DeviceAsset>lambdaQuery().eq(DeviceAsset::getCode, deviceCode.trim()).last("LIMIT 1"));
        if (device == null) return "{\"error\":\"未找到设备: " + deviceCode + "\"}";

        // 查设备类型编码
        String typeCode = null;
        if (device.getTypeId() != null) {
            DeviceType dt = deviceTypeMapper.selectById(device.getTypeId());
            if (dt != null) typeCode = dt.getCode();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"名称\":\"").append(device.getName()).append("\",\"编号\":\"").append(device.getCode())
                .append("\",\"类型\":\"").append(typeCode != null ? typeCode : "未知")
                .append("\",\"在线\":").append(Integer.valueOf(1).equals(device.getOnlineStatus()))
                .append(",\"启用\":").append(Integer.valueOf(1).equals(device.getEnabled()));

        // 从 MongoDB 查询最新遥测
        String coll = TELEMETRY_COLLECTIONS.get(typeCode);
        if (coll != null) {
            Document doc = mongoTemplate.findOne(
                    new Query(Criteria.where("deviceCode").is(device.getCode()))
                            .with(Sort.by(Sort.Direction.DESC, "reportedAt")).limit(1),
                    Document.class, coll);
            if (doc != null) {
                sb.append(",\"最新遥测\":{");
                Object payload = doc.get("payload");
                if (payload instanceof Map<?, ?> pm) {
                    for (Map.Entry<?, ?> e : pm.entrySet()) {
                        if (e.getValue() instanceof Number) {
                            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue()).append(",");
                        }
                    }
                }
                Object t = doc.get("reportedAt");
                if (t != null) sb.append("\"采集时间\":\"").append(t).append("\"");
                sb.append("}");
            } else {
                sb.append(",\"最新遥测\":\"暂无\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String executeGetAiRiskSummary() {
        var result = platformPageService.aiRisks(null, null, null, null, 1, 10);
        if (result == null || result.records() == null || result.records().isEmpty())
            return "{\"message\":\"当前没有AI风险识别记录\",\"total\":0}";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"total\":").append(result.total()).append(",\"records\":[");
        for (var r : result.records()) {
            sb.append("{\"类型\":\"").append(r.detectType()).append("\",\"摄像头\":\"")
                    .append(r.cameraName() != null ? r.cameraName() : r.cameraCode()).append("\",\"人员\":\"")
                    .append(r.personnelName() != null ? r.personnelName() : "未识别").append("\",\"时间\":\"")
                    .append(r.occurredAt()).append("\"},");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ---- Helpers ----

    private String extractArg(String json, String key) {
        if (json == null || json.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            Object val = map.get(key);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            // fallback: simple string extraction
            String search = "\"" + key + "\"";
            int ki = json.indexOf(search);
            if (ki < 0) return null;
            int ci = json.indexOf(":", ki);
            if (ci < 0) return null;
            int vs = json.indexOf("\"", ci);
            if (vs < 0) return null;
            int ve = json.indexOf("\"", vs + 1);
            if (ve < 0) return null;
            return json.substring(vs + 1, ve);
        }
    }
}
