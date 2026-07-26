package com.zxylearn.build_guard_server.controller;

import com.zxylearn.build_guard_server.common.BusinessException;
import com.zxylearn.build_guard_server.dto.AiChatDtos.ChatMessage;
import com.zxylearn.build_guard_server.service.AiChatService;
import com.zxylearn.build_guard_server.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 智能问答 Controller，使用 SSE (Server-Sent Events) 流式返回回复。
 *
 * 前端通过 POST /api/ai-chat/ask 发送问题，
 * 后端以 text/event-stream 格式逐字推送 AI 回复。
 *
 * SSE 事件类型：
 * - "text"   : AI 回复的文本片段，前端逐字追加显示
 * - "done"   : 回复完成
 * - "error"  : 出错时的错误信息
 */
@RestController
@RequestMapping("/api/ai-chat")
public class AiChatController {

    private final AiChatService aiChatService;
    private final AuthService authService;

    public AiChatController(AiChatService aiChatService, AuthService authService) {
        this.aiChatService = aiChatService;
        this.authService = authService;
    }

    /**
     * 发送消息并获取流式 AI 回复。
     *
     * 请求体: { "message": "用户问题", "history": [...] }
     * 响应:  SSE 流 (text/event-stream)
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody Map<String, Object> body, HttpServletRequest servletRequest) {
        // Token 认证
        String token = extractToken(servletRequest);
        if (authService.resolveAdminId(token).isEmpty()) {
            throw new BusinessException(401, "请先登录");
        }

        String message = (String) body.getOrDefault("message", "");
        if (message == null || message.isBlank()) {
            throw new BusinessException(400, "消息不能为空");
        }

        // 解析历史对话
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawHistory = (List<Map<String, Object>>) body.get("history");
        List<ChatMessage> history = null;
        if (rawHistory != null) {
            history = rawHistory.stream()
                    .map(m -> new ChatMessage(
                            (String) m.getOrDefault("role", "user"),
                            (String) m.get("content"),
                            (String) m.get("toolCallId"),
                            null))
                    .toList();
        }

        // 创建 SSE 发射器（30 秒超时）
        SseEmitter emitter = new SseEmitter(60_000L);

        aiChatService.chatStream(message, history, emitter);

        return emitter;
    }

    private String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }
}
