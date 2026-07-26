package com.zxylearn.build_guard_server.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 智能聊天模块的请求/响应 DTO。
 * 消息格式兼容 OpenAI Chat Completions API。
 */
public final class AiChatDtos {

    private AiChatDtos() {
    }

    /** 前端发送的聊天请求 */
    public record ChatRequest(String message) {
    }

    /** 返回给前端的聊天响应 */
    public record ChatResponse(
            String reply,
            List<ChatMessage> history
    ) {
    }

    /** 对话消息，兼容 OpenAI 消息格式 */
    public record ChatMessage(
            String role,       // "system" | "user" | "assistant" | "tool"
            String content,    // 消息文本
            String toolCallId, // tool 角色时的调用 ID
            List<ToolCall> toolCalls // assistant 角色时的工具调用列表
    ) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content, null, null);
        }

        public static ChatMessage assistantWithToolCalls(List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", null, null, toolCalls);
        }

        public static ChatMessage tool(String toolCallId, String content) {
            return new ChatMessage("tool", content, toolCallId, null);
        }
    }

    /** LLM 返回的工具调用 */
    public record ToolCall(
            String id,
            String name,
            Map<String, Object> arguments
    ) {
    }

    /** 工具函数执行结果 */
    public record ToolResult(
            String toolCallId,
            String content
    ) {
    }
}
