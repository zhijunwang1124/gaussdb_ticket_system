package com.example.gaussdb.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiLlmClient.class);
    
    private final ObjectMapper objectMapper;
    private final OpenAIClient client;
    private final String model;

    public OpenAiLlmClient(
            ObjectMapper objectMapper,
            @Value("${app.llm.endpoint}") String endpoint,
            @Value("${app.llm.api-key}") String apiKey,
            @Value("${app.llm.model:gpt-4o-mini}") String model) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .build();
        logger.info("OpenAI客户端初始化完成: endpoint={}, model={}", endpoint, model);
    }

    @Override
    public Map<String, Object> analyzeTicket(Map<String, Object> ticketData, List<Map<String, Object>> columnDefinitions, String backgroundPrompt) {
        logger.debug("开始调用OpenAI分析工单: model={}", model);
        
        try {
            String systemPrompt = "你是工单分析助手。请严格输出JSON对象，不要输出额外文本。";
            String userPrompt = buildUserPrompt(ticketData, columnDefinitions, backgroundPrompt);
            
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .addSystemMessage(systemPrompt)
                    .addUserMessage(userPrompt)
                    .model(model)
                    .temperature(0)
                    .build();
            
            logger.debug("OpenAI请求: system={}, user长度={}", systemPrompt, userPrompt.length());
            logger.info("发送OpenAI请求...");
            
            ChatCompletion chatCompletion = client.chat().completions().create(params);
            
            logger.debug("OpenAI响应ID: {}", chatCompletion.id());
            
            String content = chatCompletion.choices().get(0).message().content().orElse("{}");
            logger.debug("OpenAI响应内容: {}", content);
            
            Map<String, Object> result = parseContent(content);
            logger.info("OpenAI分析完成，结果: {}", result);
            
            return result;
        } catch (Exception ex) {
            logger.error("调用OpenAI失败: {}", ex.getMessage());
            logger.error("异常详情:", ex);
            throw new RuntimeException("OpenAI call failed: " + ex.getMessage(), ex);
        }
    }

    private String buildUserPrompt(Map<String, Object> ticketData, List<Map<String, Object>> columnDefinitions, String backgroundPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("请使用Chat模式分析以下工单，并输出JSON对象。\n");
        sb.append("提示词: ");
        sb.append(backgroundPrompt == null ? "" : backgroundPrompt.trim());
        sb.append("\n");
        sb.append("本次需要分析出的结果列定义如下(每列只能从allowed_values中选择):\n");
        sb.append(toJson(columnDefinitions)).append("\n");
        sb.append("工单内容: ").append(toJson(ticketData)).append("\n");
        sb.append("请仅输出JSON对象，key必须使用column_name。\n");
        sb.append("输出示例: {\"产品分类\":\"内核问题\",\"是否质量问题\":\"是\"}\n");
        return sb.toString();
    }

    private Map<String, Object> parseContent(String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            return new HashMap<String, Object>();
        }
        String normalized = normalizeJsonContent(content);
        return objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() {});
    }

    private String normalizeJsonContent(String content) {
        String normalized = content.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7).trim();
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring(3).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }
}