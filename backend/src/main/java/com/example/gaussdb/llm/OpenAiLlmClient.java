package com.example.gaussdb.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "app.llm", name = "provider", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiLlmClient.class);
    
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String endpoint;
    private final String apiKey;
    private final String model;

    public OpenAiLlmClient(
            ObjectMapper objectMapper,
            @Value("${app.llm.endpoint}") String endpoint,
            @Value("${app.llm.api-key}") String apiKey,
            @Value("${app.llm.model:gpt-4o-mini}") String model) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public Map<String, Object> analyzeTicket(Map<String, Object> ticketData, List<Map<String, Object>> columnDefinitions, String backgroundPrompt) {
        logger.debug("开始调用OpenAI分析工单: endpoint={}, model={}", endpoint, model);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<String, Object>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0);
            requestBody.put("messages", buildMessages(ticketData, columnDefinitions, backgroundPrompt));

            HttpEntity<Map<String, Object>> request = new HttpEntity<Map<String, Object>>(requestBody, headers);
            
            logger.debug("OpenAI请求内容: {}", toJson(requestBody));
            logger.info("发送OpenAI请求...");
            
            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, request, String.class);
            
            logger.debug("OpenAI响应状态: {}", response.getStatusCode());
            logger.debug("OpenAI响应内容: {}", response.getBody());
            
            Map<String, Object> result = parseResponse(response.getBody());
            logger.info("OpenAI分析完成，结果: {}", result);
            
            return result;
        } catch (Exception ex) {
            logger.error("调用OpenAI失败: {}", ex.getMessage());
            logger.error("异常详情:", ex);
            logger.error("请求配置: endpoint={}, model={}", endpoint, model);
            throw new RuntimeException("OpenAI call failed: " + ex.getMessage(), ex);
        }
    }

    private List<Map<String, Object>> buildMessages(Map<String, Object> ticketData, List<Map<String, Object>> columnDefinitions, String backgroundPrompt) {
        List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();

        Map<String, Object> systemMessage = new HashMap<String, Object>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是工单分析助手。请严格输出JSON对象，不要输出额外文本。");
        messages.add(systemMessage);

        Map<String, Object> userMessage = new HashMap<String, Object>();
        userMessage.put("role", "user");
        userMessage.put("content", buildUserPrompt(ticketData, columnDefinitions, backgroundPrompt));
        messages.add(userMessage);

        return messages;
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

    private Map<String, Object> parseResponse(String body) throws Exception {
        if (body == null) {
            return new HashMap<String, Object>();
        }
        Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
            return new HashMap<String, Object>();
        }
        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map)) {
            return new HashMap<String, Object>();
        }
        Object messageObj = ((Map<?, ?>) first).get("message");
        if (!(messageObj instanceof Map)) {
            return new HashMap<String, Object>();
        }
        Object contentObj = ((Map<?, ?>) messageObj).get("content");
        String content = contentObj == null ? "{}" : String.valueOf(contentObj);
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
