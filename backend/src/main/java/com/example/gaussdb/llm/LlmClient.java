package com.example.gaussdb.llm;

import java.util.List;
import java.util.Map;

public interface LlmClient {
    Map<String, Object> analyzeTicket(Map<String, Object> ticketData, List<Map<String, Object>> columnDefinitions, String backgroundPrompt);
}
