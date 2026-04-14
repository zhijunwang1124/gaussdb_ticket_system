package com.example.gaussdb.service;

import com.example.gaussdb.dto.Requests.AnalysisColumnRequest;
import com.example.gaussdb.dto.Requests.PivotRequest;
import com.example.gaussdb.dto.Requests.SaveAnalysisRequest;
import com.example.gaussdb.dto.Requests.TicketSearchRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface CoreService {
    Map<String, Object> importExcel(MultipartFile file) throws IOException;
    List<Map<String, Object>> listTickets(TicketSearchRequest request);
    List<Map<String, Object>> listPersistedTickets(TicketSearchRequest request);
    void exportTickets(TicketSearchRequest request, OutputStream outputStream) throws IOException;
    List<String> listColumns();
    List<Map<String, Object>> listAnalysisColumns();
    Long saveAnalysisColumn(Long id, AnalysisColumnRequest request);
    Long createAnalyzeTask(List<String> ywNos, List<String> selectedColumnKeys, String backgroundPrompt);
    boolean stopAnalyzeTask(Long taskId);
    SseEmitter subscribeTask(Long taskId);
    Map<String, Object> pivot(PivotRequest request);
    void saveAnalysisResults(SaveAnalysisRequest request);
    void writeSampleTicketsExcel100(OutputStream outputStream) throws IOException;
}
