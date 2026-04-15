package com.example.gaussdb.controller;

import com.example.gaussdb.dto.Requests;
import com.example.gaussdb.dto.Requests.AnalysisColumnRequest;
import com.example.gaussdb.dto.Requests.AnalyzeRequest;
import com.example.gaussdb.dto.Requests.PivotRequest;
import com.example.gaussdb.dto.Requests.SaveAnalysisRequest;
import com.example.gaussdb.dto.Requests.TicketSearchRequest;
import com.example.gaussdb.service.CoreService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api")
public class CoreController {
    private final CoreService coreService;

    public CoreController(CoreService coreService) {
        this.coreService = coreService;
    }

    @PostMapping("/import/excel")
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        return coreService.importExcel(file);
    }

    @GetMapping("/sample/tickets-excel-100")
    public ResponseEntity<byte[]> sampleTicketsExcel100() throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        coreService.writeSampleTicketsExcel100(bos);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ticket_test_sample_full_with_extra_columns.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }

    @GetMapping("/tickets/columns")
    public Map<String, Object> columns() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("defaultVisibleCount", 10);
        result.put("columns", coreService.listColumns());
        return result;
    }

    @PostMapping("/tickets/search")
    public Object search(@RequestBody(required = false) TicketSearchRequest request) {
        return coreService.listTickets(request);
    }

    @PostMapping("/persisted-tickets/search")
    public Object searchPersisted(@RequestBody(required = false) TicketSearchRequest request) {
        return coreService.listPersistedTickets(request);
    }

    @PostMapping("/tickets/export")
    public ResponseEntity<byte[]> export(@RequestBody(required = false) TicketSearchRequest request) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        coreService.exportTickets(request, bos);
        String date = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
        String fileName = "tickets_" + date + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bos.toByteArray());
    }

    @GetMapping("/analysis-columns")
    public Object listAnalysisColumns() {
        return coreService.listAnalysisColumns();
    }

    @PostMapping("/analysis-columns")
    public Map<String, Object> createAnalysisColumn(@RequestBody @Valid AnalysisColumnRequest request) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", coreService.saveAnalysisColumn(null, request));
        return result;
    }

    @PutMapping("/analysis-columns/{id}")
    public Map<String, Object> updateAnalysisColumn(@PathVariable Long id, @RequestBody @Valid AnalysisColumnRequest request) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("id", coreService.saveAnalysisColumn(id, request));
        return result;
    }

    @PostMapping("/analyze/tasks")
    public Map<String, Object> createTask(@RequestBody AnalyzeRequest request) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("taskId", coreService.createAnalyzeTask(
                request.getYwNos(),
                request.getSelectedColumnKeys(),
                request.getBackgroundPrompt()));
        return result;
    }

    @GetMapping("/analyze/tasks/{taskId}/stream")
    public SseEmitter stream(@PathVariable Long taskId) {
        return coreService.subscribeTask(taskId);
    }

    @PostMapping("/analyze/tasks/{taskId}/stop")
    public Map<String, Object> stopTask(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("stopped", coreService.stopAnalyzeTask(taskId));
        return result;
    }

    @PostMapping("/pivot/query")
    public Object pivot(@RequestBody PivotRequest request) {
        return coreService.pivot(request);
    }

    @PostMapping("/pivot/personnel")
    public Object pivotPersonnel(@RequestBody  Requests.PersonnelPivotRequest request) {
         return coreService.pivotPersonnel(request);
    }

    @PostMapping("/pivot/personnel-transfer")
    public Object pivotPersonnelTransfer(@RequestBody Requests.PersonnelPivotRequest request) {
        return coreService.pivotPersonnelTransfer(request);
    }

    @PostMapping("/analysis-results/save")
    public Map<String, Object> saveAnalysisResults(@RequestBody SaveAnalysisRequest request) {
        coreService.saveAnalysisResults(request);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("saved", true);
        return result;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, String> body = new HashMap<String, String>();
        body.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }
}
