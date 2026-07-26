package com.zxylearn.build_guard_server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek / OpenAI 兼容 API 配置。
 *
 * application.yml 中已有的 openai 配置块：
 * <pre>
 * openai:
 *   api-key: sk-xxx
 *   base-url: https://api.deepseek.com
 *   model: deepseek-v4-flash
 * </pre>
 */
@ConfigurationProperties(prefix = "openai")
public record AiChatConfig(
        String apiKey,
        String baseUrl,
        String model
) {
    /** 返回去除尾部斜杠的 base URL，用于拼接 /v1/chat/completions */
    public String chatCompletionsUrl() {
        String url = baseUrl == null ? "https://api.deepseek.com" : baseUrl;
        return url.replaceAll("/+$", "") + "/v1/chat/completions";
    }
}
