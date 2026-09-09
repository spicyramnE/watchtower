package com.watchtower.watchtower.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin client for Groq's OpenAI-compatible chat completions API
 * (https://console.groq.com/docs/tool-use), used as the reasoning model for
 * Watchtower's agent loop. See README architecture notes for why Groq
 * (an open-weight model host) is used here instead of the Anthropic Claude
 * API the project doc originally specified.
 */
@Component
public class GroqClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GroqClient(@Value("${groq.api-key}") String apiKey,
                       @Value("${groq.base-url}") String baseUrl,
                       @Value("${groq.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        // Same NIO loopback-socket bug as VoyageEmbeddingClient - see README
        // Windows notes. Avoided by forcing the classic HttpURLConnection
        // based request factory.
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public ChatCompletionResponse chat(List<ChatMessage> messages, List<GroqTool> tools) {
        if (!isConfigured()) {
            throw new IllegalStateException("groq.api-key (GROQ_API_KEY) is not configured");
        }

        ChatRequest request = new ChatRequest(model, messages, tools, "auto");

        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Groq API returned an empty response");
        }

        return response;
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            List<GroqTool> tools,
            @JsonProperty("tool_choice") String toolChoice) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<ToolCall> toolCalls,
            @JsonProperty("tool_call_id") String toolCallId,
            String name) {

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null, null);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null, null);
        }

        public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", content, toolCalls, null, null);
        }

        public static ChatMessage toolResult(String toolCallId, String toolName, String content) {
            return new ChatMessage("tool", content, null, toolCallId, toolName);
        }
    }

    public record ToolCall(String id, String type, FunctionCall function) {
    }

    public record FunctionCall(String name, String arguments) {
    }

    public record GroqTool(String type, FunctionDef function) {
        public static GroqTool function(String name, String description, Map<String, Object> parameters) {
            return new GroqTool("function", new FunctionDef(name, description, parameters));
        }
    }

    public record FunctionDef(String name, String description, Map<String, Object> parameters) {
    }

    public record ChatCompletionResponse(List<Choice> choices) {
    }

    public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    public record Message(String role, String content, @JsonProperty("tool_calls") List<ToolCall> toolCalls) {
    }
}
