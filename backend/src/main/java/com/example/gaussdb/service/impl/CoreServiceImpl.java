package com.example.gaussdb.service.impl;

import com.example.gaussdb.dto.Requests.AnalysisColumnRequest;
import com.example.gaussdb.dto.Requests.PivotRequest;
import com.example.gaussdb.dto.Requests.PersonnelPivotRequest;
import com.example.gaussdb.TicketColumns;
import com.example.gaussdb.dto.Requests.SaveAnalysisItem;
import com.example.gaussdb.dto.Requests.SaveAnalysisRequest;
import com.example.gaussdb.dto.Requests.TicketSearchRequest;
import com.example.gaussdb.llm.LlmClient;
import com.example.gaussdb.service.CoreService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import javax.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class CoreServiceImpl implements CoreService {
    private static final Pattern COLUMN_KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LlmClient llmClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<Long, List<SseEmitter>>();
    private final Map<Long, AtomicBoolean> taskCancelFlags = new ConcurrentHashMap<Long, AtomicBoolean>();
    private final Map<Long, ExecutorService> taskWorkers = new ConcurrentHashMap<Long, ExecutorService>();
    private final int llmConcurrentThreads;

    public CoreServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            LlmClient llmClient,
            @Value("${app.llm.concurrent-threads:4}") int llmConcurrentThreads) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.llmClient = llmClient;
        this.llmConcurrentThreads = llmConcurrentThreads;
    }

    @PostConstruct
    public void syncPhysicalAnalysisColumns() {
        syncResultTableColumnsFromConfig();
    }

    private void syncResultTableColumnsFromConfig() {
        List<String> keys = jdbcTemplate.queryForList("SELECT column_key FROM analysis_column_config ORDER BY id", String.class);
        for (String key : keys) {
            ensurePhysicalResultColumn(key);
        }
    }

    private void ensurePhysicalResultColumn(String columnKey) {
        if (columnKey == null || !COLUMN_KEY_PATTERN.matcher(columnKey).matches()) {
            return;
        }
        jdbcTemplate.update("ALTER TABLE ticket_analysis_result ADD COLUMN IF NOT EXISTS " + columnKey + " TEXT");
    }

    @Override
    public Map<String, Object> importExcel(MultipartFile file) throws IOException {
        int count = 0;
        try (XSSFWorkbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                Map<String, Object> data = new LinkedHashMap<String, Object>();
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    Cell hc = header.getCell(c);
                    if (hc == null) {
                        continue;
                    }
                    String colName = hc.getStringCellValue().trim();
                    data.put(colName, readCellValue(row.getCell(c)));
                }
                String ywNo = firstNonBlank(
                        data.get("工单号"),
                        data.get("YW单号"),
                        data.get("YW工单号"),
                        data.get("YW_NO"),
                        data.get("yw_no"));
                if (ywNo.isEmpty() || !ywNo.startsWith("YW")) {
                    continue;
                }
                upsertTicket(ywNo.trim(), data);
                count++;
            }
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("imported", count);
        return result;
    }

    private Object readCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new Timestamp(cell.getDateCellValue().getTime());
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
            default:
                return "";
        }
    }

    private void upsertTicket(String ywNo, Map<String, Object> data) {
        String currentPhase = stringVal(data.get("当前阶段"));
        String severity = stringVal(data.get("问题严重性"));
        String site = stringVal(data.get("局点"));
        String instanceName = stringVal(data.get("实例简称"));
        String businessName = stringVal(data.get("业务名称"));
        String handler = stringVal(data.get("当前处理人"));
        String desc = stringVal(data.get("描述"));
        String retentionTime = stringVal(data.get("滞留时间"));
        String slaTime = stringVal(data.get("SLA时间"));
        String reviewer = stringVal(data.get("问题审核人"));
        String opsAnalyzer = stringVal(data.get("运维人员分析人"));
        String devAnalyzer = stringVal(data.get("开发人员分析人"));
        String devCloser = stringVal(data.get("开发人员闭环人"));
        String opsCloser = stringVal(data.get("运维人员闭环人"));
        String closer = stringVal(data.get("问题审核关闭人"));
        String progress = stringVal(data.get("进展概述"));
        Timestamp createDate = parseStartDate(data.get("创建日期"));
        String reviewDuration = stringVal(data.get("问题审核总时长"));
        String opsAnalysisDuration = stringVal(data.get("运维人员分析总时长"));
        String devAnalysisDuration = stringVal(data.get("开发人员分析总时长"));
        String devCloseDuration = stringVal(data.get("开发人员闭环总时长"));
        String opsCloseDuration = stringVal(data.get("运维人员闭环总时长"));
        String closeDuration = stringVal(data.get("问题审核关闭总时长"));
        String ctrlVer = stringVal(data.get("管控版本"));
        String reply = stringVal(data.get("对外答复"));
        String recoveryTime = stringVal(data.get("故障到恢复用时"));
        String isIncident = stringVal(data.get("是否涉及故障恢复"));
        String isPanshi = stringVal(data.get("磐石版本是否涉及"));
        String needWarning = stringVal(data.get("是否需要预警"));
        String rootCause = stringVal(data.get("问题根因"));
        String dtsNo = stringVal(data.get("DTS单号"));
        String workaround = stringVal(data.get("规避措施"));
        String isQuality = stringVal(data.get("是否质量问题"));
        String recoveryMethod = stringVal(data.get("恢复方法"));
        String isConsult = stringVal(data.get("是否咨询问题"));
        String kernelUpgrade = stringVal(data.get("是否涉及内核升级"));
        String coopPerson = stringVal(data.get("协同处理人"));
        String kernelVer = stringVal(data.get("高斯版本"));
        String hcsVer = stringVal(data.get("HCS版本号"));
        String hcsType = stringVal(data.get("HCS/轻量化"));
        String problemType = stringVal(data.get("问题类型"));
        String rootCauseCategory = stringVal(data.get("根因分类"));
        String deployType = stringVal(data.get("部署形态"));
        
        String sql = "INSERT INTO ticket(yw_no, \"当前阶段\", \"问题严重性\", \"局点\", \"实例简称\", \"业务名称\", \"当前处理人\", "
                + "\"描述\", \"滞留时间\", \"SLA时间\", \"问题审核人\", \"运维人员分析人\", \"开发人员分析人\", \"开发人员闭环人\", "
                + "\"运维人员闭环人\", \"问题审核关闭人\", \"进展概述\", \"创建日期\", \"问题审核总时长\", \"运维人员分析总时长\", "
                + "\"开发人员分析总时长\", \"开发人员闭环总时长\", \"运维人员闭环总时长\", \"问题审核关闭总时长\", \"管控版本\", "
                + "\"对外答复\", \"故障到恢复用时\", \"是否涉及故障恢复\", \"磐石版本是否涉及\", \"是否需要预警\", \"问题根因\", "
                + "\"DTS单号\", \"规避措施\", \"是否质量问题\", \"恢复方法\", \"是否咨询问题\", \"是否涉及内核升级\", "
                + "\"协同处理人\", \"高斯版本\", \"HCS版本号\", \"HCS/轻量化\", \"问题类型\", \"根因分类\", \"部署形态\", updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW()) "
                + "ON CONFLICT (yw_no) DO UPDATE SET "
                + "\"当前阶段\" = EXCLUDED.\"当前阶段\", \"问题严重性\" = EXCLUDED.\"问题严重性\", \"局点\" = EXCLUDED.\"局点\", "
                + "\"实例简称\" = EXCLUDED.\"实例简称\", \"业务名称\" = EXCLUDED.\"业务名称\", \"当前处理人\" = EXCLUDED.\"当前处理人\", "
                + "\"描述\" = EXCLUDED.\"描述\", \"滞留时间\" = EXCLUDED.\"滞留时间\", \"SLA时间\" = EXCLUDED.\"SLA时间\", "
                + "\"问题审核人\" = EXCLUDED.\"问题审核人\", \"运维人员分析人\" = EXCLUDED.\"运维人员分析人\", "
                + "\"开发人员分析人\" = EXCLUDED.\"开发人员分析人\", \"开发人员闭环人\" = EXCLUDED.\"开发人员闭环人\", "
                + "\"运维人员闭环人\" = EXCLUDED.\"运维人员闭环人\", \"问题审核关闭人\" = EXCLUDED.\"问题审核关闭人\", "
                + "\"进展概述\" = EXCLUDED.\"进展概述\", \"创建日期\" = EXCLUDED.\"创建日期\", \"问题审核总时长\" = EXCLUDED.\"问题审核总时长\", "
                + "\"运维人员分析总时长\" = EXCLUDED.\"运维人员分析总时长\", \"开发人员分析总时长\" = EXCLUDED.\"开发人员分析总时长\", "
                + "\"开发人员闭环总时长\" = EXCLUDED.\"开发人员闭环总时长\", \"运维人员闭环总时长\" = EXCLUDED.\"运维人员闭环总时长\", "
                + "\"问题审核关闭总时长\" = EXCLUDED.\"问题审核关闭总时长\", \"管控版本\" = EXCLUDED.\"管控版本\", "
                + "\"对外答复\" = EXCLUDED.\"对外答复\", \"故障到恢复用时\" = EXCLUDED.\"故障到恢复用时\", "
                + "\"是否涉及故障恢复\" = EXCLUDED.\"是否涉及故障恢复\", \"磐石版本是否涉及\" = EXCLUDED.\"磐石版本是否涉及\", "
                + "\"是否需要预警\" = EXCLUDED.\"是否需要预警\", \"问题根因\" = EXCLUDED.\"问题根因\", \"DTS单号\" = EXCLUDED.\"DTS单号\", "
                + "\"规避措施\" = EXCLUDED.\"规避措施\", \"是否质量问题\" = EXCLUDED.\"是否质量问题\", "
                + "\"恢复方法\" = EXCLUDED.\"恢复方法\", \"是否咨询问题\" = EXCLUDED.\"是否咨询问题\", "
                + "\"是否涉及内核升级\" = EXCLUDED.\"是否涉及内核升级\", \"协同处理人\" = EXCLUDED.\"协同处理人\", "
                + "\"高斯版本\" = EXCLUDED.\"高斯版本\", \"HCS版本号\" = EXCLUDED.\"HCS版本号\", \"HCS/轻量化\" = EXCLUDED.\"HCS/轻量化\", "
                + "\"问题类型\" = EXCLUDED.\"问题类型\", \"根因分类\" = EXCLUDED.\"根因分类\", \"部署形态\" = EXCLUDED.\"部署形态\", "
                + "updated_at = NOW()";
        jdbcTemplate.update(sql, ywNo, emptyToNull(currentPhase), emptyToNull(severity), emptyToNull(site), 
                emptyToNull(instanceName), emptyToNull(businessName), emptyToNull(handler),
                emptyToNull(desc), emptyToNull(retentionTime), emptyToNull(slaTime), emptyToNull(reviewer),
                emptyToNull(opsAnalyzer), emptyToNull(devAnalyzer), emptyToNull(devCloser), emptyToNull(opsCloser),
                emptyToNull(closer), emptyToNull(progress), createDate, emptyToNull(reviewDuration), emptyToNull(opsAnalysisDuration),
                emptyToNull(devAnalysisDuration), emptyToNull(devCloseDuration), emptyToNull(opsCloseDuration), emptyToNull(closeDuration),
                emptyToNull(ctrlVer), emptyToNull(reply), emptyToNull(recoveryTime), emptyToNull(isIncident),
                emptyToNull(isPanshi), emptyToNull(needWarning), emptyToNull(rootCause), emptyToNull(dtsNo),
                emptyToNull(workaround), emptyToNull(isQuality), emptyToNull(recoveryMethod), emptyToNull(isConsult),
                emptyToNull(kernelUpgrade), emptyToNull(coopPerson), emptyToNull(kernelVer), emptyToNull(hcsVer),
                emptyToNull(hcsType), emptyToNull(problemType), emptyToNull(rootCauseCategory), emptyToNull(deployType));
    }

    private String stringVal(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private Timestamp parseStartDate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp) {
            return (Timestamp) raw;
        }
        if (raw instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) raw).getTime());
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd"};
        for (String p : patterns) {
            try {
                return new Timestamp(new SimpleDateFormat(p).parse(s).getTime());
            } catch (ParseException ignored) {
            }
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> listTickets(TicketSearchRequest request) {
        String sql = buildTicketSelectSql();
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : list) {
            if (request == null) {
                result.add(row);
                continue;
            }
            if (isNotBlank(request.getYwNo()) && !String.valueOf(row.get("yw_no")).contains(request.getYwNo())) {
                continue;
            }
            if (isNotBlank(request.getCreateLogKeyword()) && !ticketMatchesKeyword(row, request.getCreateLogKeyword())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    private boolean ticketMatchesKeyword(Map<String, Object> row, String keyword) {
        String k = keyword == null ? "" : keyword;
        return String.valueOf(row.get("进展概述")).contains(k)
                || String.valueOf(row.get("描述")).contains(k)
                || String.valueOf(row.get("对外答复")).contains(k);
    }

    private String buildTicketSelectSql() {
        StringBuilder sb = new StringBuilder("SELECT t.id, t.yw_no, ");
        for (int i = 0; i < TicketColumns.DATA_COLUMNS.size(); i++) {
            String col = TicketColumns.DATA_COLUMNS.get(i);
            sb.append("t.").append(TicketColumns.quote(col)).append(" AS ").append(TicketColumns.quote(col));
            if (i < TicketColumns.DATA_COLUMNS.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(", t.updated_at FROM ticket t ORDER BY t.updated_at DESC");
        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> listPersistedTickets(TicketSearchRequest request) {
        syncResultTableColumnsFromConfig();
        String sql = buildPersistedTicketsSelectSql();
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : list) {
            if (request == null) {
                result.add(row);
                continue;
            }
            if (isNotBlank(request.getYwNo()) && !String.valueOf(row.get("yw_no")).contains(request.getYwNo())) {
                continue;
            }
            if (isNotBlank(request.getCreateLogKeyword()) && !ticketMatchesKeyword(row, request.getCreateLogKeyword())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    private String buildPersistedTicketsSelectSql() {
        List<String> keys = jdbcTemplate.queryForList("SELECT column_key FROM analysis_column_config ORDER BY id", String.class);
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT t.id, t.yw_no, ");
        for (int i = 0; i < TicketColumns.DATA_COLUMNS.size(); i++) {
            String col = TicketColumns.DATA_COLUMNS.get(i);
            sb.append("t.").append(TicketColumns.quote(col)).append(" AS ").append(TicketColumns.quote(col)).append(", ");
        }
        sb.append("t.updated_at AS ticket_updated_at, r.updated_at AS analysis_updated_at");
        for (String key : keys) {
            if (COLUMN_KEY_PATTERN.matcher(key).matches()) {
                sb.append(", r.").append(key).append(" AS ").append(key);
            }
        }
        sb.append(" FROM ticket t LEFT JOIN ticket_analysis_result r ON t.yw_no = r.yw_no ORDER BY t.updated_at DESC");
        return sb.toString();
    }

    @Override
    public void exportTickets(TicketSearchRequest request, OutputStream outputStream) throws IOException {
        List<Map<String, Object>> rows = listTickets(request);
        Map<String, Object> session = request == null ? null : request.getSessionAnalysis();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("tickets");
            Row head = sheet.createRow(0);
            int col = 0;
            head.createCell(col++).setCellValue("工单号");
            for (String cn : TicketColumns.DATA_COLUMNS) {
                head.createCell(col++).setCellValue(cn);
            }
            head.createCell(col).setCellValue("分析结果(JSON)");
            for (int i = 0; i < rows.size(); i++) {
                Row r = sheet.createRow(i + 1);
                Map<String, Object> row = rows.get(i);
                int c = 0;
                r.createCell(c++).setCellValue(String.valueOf(row.get("yw_no")));
                for (String cn : TicketColumns.DATA_COLUMNS) {
                    Object v = row.get(cn);
                    r.createCell(c++).setCellValue(v == null ? "" : String.valueOf(v));
                }
                String yw = String.valueOf(row.get("yw_no"));
                Map<String, Object> analysis = new LinkedHashMap<String, Object>();
                if (session != null && session.containsKey(yw)) {
                    Object raw = session.get(yw);
                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) raw;
                        analysis.putAll(m);
                    }
                }
                r.createCell(c).setCellValue(toJson(analysis));
            }
            wb.write(outputStream);
        }
    }

    @Override
    public List<String> listColumns() {
        List<String> list = new ArrayList<String>();
        list.add("工单号");
        list.addAll(TicketColumns.DATA_COLUMNS);
        return list;
    }

    @Override
    public List<Map<String, Object>> listAnalysisColumns() {
        return jdbcTemplate.queryForList("SELECT * FROM analysis_column_config ORDER BY id ASC");
    }

    @Override
    public Long saveAnalysisColumn(Long id, AnalysisColumnRequest request) {
        String allowed = toJson(request.getAllowedValues() == null ? Collections.emptyList() : request.getAllowedValues());
        boolean topLevel = request.getTopLevel() != null && request.getTopLevel();
        if (id == null) {
            String ck = request.getColumnKey() == null ? "" : request.getColumnKey().trim();
            if (!COLUMN_KEY_PATTERN.matcher(ck).matches()) {
                throw new IllegalArgumentException("新增分析列必须提供合法的 columnKey：小写字母开头，仅含小写字母、数字、下划线，长度 1–63");
            }
            ensurePhysicalResultColumn(ck);
            jdbcTemplate.update(
                    "INSERT INTO analysis_column_config(column_key,column_name,description,allowed_values,is_top_level) VALUES (?,?,?,CAST(? AS JSONB),?)",
                    ck, request.getColumnName(), request.getDescription(), allowed, topLevel);
            return jdbcTemplate.queryForObject("SELECT id FROM analysis_column_config WHERE column_key=?", Long.class, ck);
        }
        jdbcTemplate.update("UPDATE analysis_column_config SET column_name=?,description=?,allowed_values=CAST(? AS JSONB),is_top_level=? WHERE id=?",
                request.getColumnName(), request.getDescription(), allowed, topLevel, id);
        return id;
    }

    @Override
    public Long createAnalyzeTask(List<String> ywNos, List<String> selectedColumnKeys, String backgroundPrompt) {
        if (ywNos == null) {
            ywNos = Collections.emptyList();
        }
        if (selectedColumnKeys == null) {
            selectedColumnKeys = Collections.emptyList();
        }
        final List<String> taskSelectedColumnKeys = selectedColumnKeys;
        final String taskBackgroundPrompt = backgroundPrompt;
        Long taskId = jdbcTemplate.queryForObject(
                "INSERT INTO analyze_task(status,total_count,success_count,failed_count,created_at,updated_at) "
                        + "VALUES('RUNNING',?,?,0,NOW(),NOW()) RETURNING id",
                Long.class,
                ywNos.size(),
                0);
        taskCancelFlags.put(taskId, new AtomicBoolean(false));
        final List<String> taskYwNos = ywNos;
        executor.submit(new Runnable() {
            @Override
            public void run() {
                runTask(taskId, taskYwNos, taskSelectedColumnKeys, taskBackgroundPrompt);
            }
        });
        return taskId;
    }

    @Override
    public boolean stopAnalyzeTask(Long taskId) {
        AtomicBoolean flag = taskCancelFlags.get(taskId);
        if (flag == null) {
            return false;
        }
        flag.set(true);
        ExecutorService worker = taskWorkers.get(taskId);
        if (worker != null) {
            worker.shutdownNow();
        }
        jdbcTemplate.update("UPDATE analyze_task SET status='STOPPED',updated_at=NOW() WHERE id=?", taskId);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("status", "STOPPED");
        payload.put("finished", true);
        send(taskId, payload);
        return true;
    }

    private void runTask(Long taskId, List<String> ywNos, List<String> selectedColumnKeys, String backgroundPrompt) {
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> columns = resolveColumnsForTask(selectedColumnKeys);
        int workerCount = Math.max(1, llmConcurrentThreads);
        ExecutorService llmExecutor = Executors.newFixedThreadPool(workerCount);
        taskWorkers.put(taskId, llmExecutor);
        CompletionService<AnalyzeItemResult> completionService = new ExecutorCompletionService<AnalyzeItemResult>(llmExecutor);
        AtomicBoolean cancelFlag = taskCancelFlags.get(taskId);

        for (final String ywNo : ywNos) {
            completionService.submit(new Callable<AnalyzeItemResult>() {
                @Override
                public AnalyzeItemResult call() {
                    try {
                        Map<String, Object> ticketData = loadTicketData(ywNo);
                        Map<String, Object> result = llmClient.analyzeTicket(ticketData, columns, backgroundPrompt);
                        validateResult(result, columns);
                        return AnalyzeItemResult.success(ywNo, result);
                    } catch (Exception ex) {
                        return AnalyzeItemResult.failed(ywNo, ex.getMessage());
                    }
                }
            });
        }

        for (int i = 0; i < ywNos.size(); i++) {
            if (cancelFlag != null && cancelFlag.get()) {
                break;
            }
            try {
                Future<AnalyzeItemResult> future = completionService.take();
                AnalyzeItemResult itemResult = future.get();
                if (itemResult.success) {
                    success++;
                    send(taskId, progressPayload(itemResult.ywNo, "SUCCESS", null, success, failed, ywNos.size(), itemResult.analysisResult));
                    continue;
                }
                failed++;
                send(taskId, progressPayload(itemResult.ywNo, "FAILED", itemResult.message, success, failed, ywNos.size()));
            } catch (Exception ex) {
                failed++;
                send(taskId, progressPayload("UNKNOWN", "FAILED", ex.getMessage(), success, failed, ywNos.size()));
            }
        }
        llmExecutor.shutdown();
        boolean stopped = cancelFlag != null && cancelFlag.get();
        String status = stopped ? "STOPPED" : (failed == 0 ? "DONE" : "PARTIAL");
        jdbcTemplate.update("UPDATE analyze_task SET status=?,success_count=?,failed_count=?,updated_at=NOW() WHERE id=?", status, success, failed, taskId);
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("status", status);
        payload.put("finished", true);
        send(taskId, payload);
        taskWorkers.remove(taskId);
        taskCancelFlags.remove(taskId);
    }

    private List<Map<String, Object>> resolveColumnsForTask(List<String> selectedColumnKeys) {
        List<Map<String, Object>> allColumns = listAnalysisColumns();
        if (selectedColumnKeys == null || selectedColumnKeys.isEmpty()) {
            return allColumns;
        }
        List<Map<String, Object>> selected = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> col : allColumns) {
            String ck = String.valueOf(col.get("column_key"));
            if (selectedColumnKeys.contains(ck)) {
                selected.add(col);
            }
        }
        return selected;
    }

    private Map<String, Object> loadTicketData(String ywNo) throws IOException {
        StringBuilder q = new StringBuilder("SELECT t.yw_no");
        for (String cn : TicketColumns.DATA_COLUMNS) {
            q.append(", t.").append(TicketColumns.quote(cn));
        }
        q.append(" FROM ticket t WHERE t.yw_no = ?");
        Map<String, Object> row = jdbcTemplate.queryForMap(q.toString(), ywNo);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("工单号", row.get("yw_no"));
        for (String cn : TicketColumns.DATA_COLUMNS) {
            out.put(cn, row.get(cn));
        }
        return out;
    }

    private void validateResult(Map<String, Object> result, List<Map<String, Object>> columns) throws IOException {
        for (Map<String, Object> col : columns) {
            String key = String.valueOf(col.get("column_name"));
            Object value = result.get(key);
            List<String> allowed = objectMapper.readValue(String.valueOf(col.get("allowed_values")), new TypeReference<List<String>>() {});
            if (value == null || !allowed.contains(String.valueOf(value))) {
                result.put(key, "UNKNOWN");
            }
        }
    }

    @Override
    public SseEmitter subscribeTask(Long taskId) {
        final SseEmitter emitter = new SseEmitter(0L);
        if (!emitters.containsKey(taskId)) {
            emitters.put(taskId, new ArrayList<SseEmitter>());
        }
        emitters.get(taskId).add(emitter);
        emitter.onCompletion(new Runnable() {
            @Override
            public void run() {
                List<SseEmitter> list = emitters.get(taskId);
                if (list != null) {
                    list.remove(emitter);
                }
            }
        });
        emitter.onTimeout(new Runnable() {
            @Override
            public void run() {
                List<SseEmitter> list = emitters.get(taskId);
                if (list != null) {
                    list.remove(emitter);
                }
            }
        });
        return emitter;
    }

    private void send(Long taskId, Map<String, Object> payload) {
        List<SseEmitter> list = emitters.get(taskId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(payload));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public Map<String, Object> pivot(PivotRequest request) {
        TicketSearchRequest allRequest = new TicketSearchRequest();
        List<Map<String, Object>> allRows = listPersistedTickets(allRequest);
        List<Map<String, Object>> dateFilteredRows = filterByDateRange(allRows, request == null ? null : request.getStartDate(),
                request == null ? null : request.getEndDate());
        List<Map<String, Object>> baseRows = filterBySelectedPath(dateFilteredRows, request == null ? null : request.getSelectedPath());
        String mode = request == null ? "distribution" : stringOrDefault(request.getMode(), "distribution");
        if ("trend".equalsIgnoreCase(mode)) {
            return buildTrendPivotResult(baseRows, request);
        }
        return buildDistributionPivotResult(baseRows, request);
    }

    private Map<String, Object> buildTrendPivotResult(List<Map<String, Object>> rows, PivotRequest request) {
        String granularity = request == null ? "month" : stringOrDefault(request.getGranularity(), "month");
        Map<String, Integer> counter = new LinkedHashMap<String, Integer>();
        for (Map<String, Object> row : rows) {
            LocalDate date = extractStartDate(row);
            if (date == null) {
                continue;
            }
            String bucket = formatBucket(date, granularity);
            counter.put(bucket, counter.getOrDefault(bucket, 0) + 1);
        }
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", entry.getKey());
            item.put("count", entry.getValue());
            data.add(item);
        }
        data.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                return String.valueOf(o1.get("name")).compareTo(String.valueOf(o2.get("name")));
            }
        });
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", "trend");
        result.put("granularity", granularity);
        result.put("total", rows.size());
        result.put("data", data);
        return result;
    }

    private Map<String, Object> buildDistributionPivotResult(List<Map<String, Object>> rows, PivotRequest request) {
        String dimLabel = request == null ? null : request.getDimension();
        String dimKey = resolvePivotDimensionKey(dimLabel);
        Map<String, Integer> count = new HashMap<String, Integer>();
        for (Map<String, Object> row : rows) {
            Object cell = row.get(dimKey);
            if (cell == null || String.valueOf(cell).trim().isEmpty()) {
                continue;
            }
            String value = String.valueOf(cell).trim();
            count.put(value, count.getOrDefault(value, 0) + 1);
        }
        int groupedTotal = 0;
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            groupedTotal += entry.getValue();
        }
        int safeTotal = Math.max(groupedTotal, 1);
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            Map<String, Object> pivotRow = new LinkedHashMap<String, Object>();
            pivotRow.put("name", entry.getKey());
            pivotRow.put("count", entry.getValue());
            pivotRow.put("ratio", (double) entry.getValue() / safeTotal);
            data.add(pivotRow);
        }
        data.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                Integer c1 = Integer.valueOf(String.valueOf(o1.get("count")));
                Integer c2 = Integer.valueOf(String.valueOf(o2.get("count")));
                return c2.compareTo(c1);
            }
        });
        boolean topLevelOnly = request != null && request.getTopLevelOnly() != null && request.getTopLevelOnly();
        List<Map<String, Object>> columns = listPivotColumns(topLevelOnly);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", "distribution");
        result.put("dimension", dimLabel);
        result.put("dimensionKey", dimKey);
        result.put("chartType", data.size() > 5 ? "bar" : "pie");
        result.put("total", rows.size());
        result.put("groupedTotal", groupedTotal);
        result.put("data", data);
        result.put("columns", columns);
        result.put("path", request == null || request.getSelectedPath() == null ? Collections.emptyMap() : request.getSelectedPath());
        return result;
    }

    private List<Map<String, Object>> listPivotColumns(boolean topLevelOnly) {
        List<Map<String, Object>> all = listAnalysisColumns();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> c : all) {
            boolean topLevel = parseBoolean(c.get("is_top_level"));
            if (topLevelOnly && !topLevel) {
                continue;
            }
            if (!topLevelOnly && topLevel) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("columnKey", String.valueOf(c.get("column_key")));
            item.put("columnName", String.valueOf(c.get("column_name")));
            item.put("topLevel", topLevel);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> filterByDateRange(List<Map<String, Object>> rows, String startDate, String endDate) {
        LocalDate start = parseLocalDate(startDate);
        LocalDate end = parseLocalDate(endDate);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            LocalDate d = extractStartDate(row);
            if (d == null) {
                continue;
            }
            if (start != null && d.isBefore(start)) {
                continue;
            }
            if (end != null && d.isAfter(end)) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    private List<Map<String, Object>> filterBySelectedPath(List<Map<String, Object>> rows, Map<String, String> selectedPath) {
        if (selectedPath == null || selectedPath.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            boolean matched = true;
            for (Map.Entry<String, String> e : selectedPath.entrySet()) {
                Object actual = row.get(e.getKey());
                String actualText = actual == null ? "" : String.valueOf(actual).trim();
                if (!actualText.equals(e.getValue())) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                result.add(row);
            }
        }
        return result;
    }

    private LocalDate extractStartDate(Map<String, Object> row) {
        Object raw = row.get("创建日期");
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp) {
            return ((Timestamp) raw).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (raw instanceof java.util.Date) {
            return ((java.util.Date) raw).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return parseLocalDate(String.valueOf(raw));
    }

    private LocalDate parseLocalDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String value = text.trim();
        String[] patterns = new String[] {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                sdf.setLenient(false);
                return sdf.parse(value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String formatBucket(LocalDate date, String granularity) {
        String g = stringOrDefault(granularity, "month").toLowerCase();
        if ("year".equals(g)) {
            return date.format(DateTimeFormatter.ofPattern("yyyy"));
        }
        if ("week".equals(g)) {
            WeekFields wf = WeekFields.ISO;
            int week = date.get(wf.weekOfWeekBasedYear());
            int year = date.get(wf.weekBasedYear());
            return String.format("%04d-W%02d", year, week);
        }
        if ("day".equals(g)) {
            return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        }
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String stringOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private boolean parseBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    @Override
    public void saveAnalysisResults(SaveAnalysisRequest request) {
        if (request == null || request.getItems() == null) {
            return;
        }
        syncResultTableColumnsFromConfig();
        Map<String, String> nameToKey = columnNameToKeyMap();
        for (SaveAnalysisItem item : request.getItems()) {
            if (item == null || !isNotBlank(item.getYwNo())) {
                continue;
            }
            Map<String, Object> analysis = item.getAnalysis();
            if (analysis == null || analysis.isEmpty()) {
                continue;
            }
            List<String> dbCols = new ArrayList<String>();
            List<Object> values = new ArrayList<Object>();
            for (Map.Entry<String, Object> e : analysis.entrySet()) {
                String dbCol = nameToKey.get(e.getKey());
                if (dbCol == null || !COLUMN_KEY_PATTERN.matcher(dbCol).matches()) {
                    continue;
                }
                ensurePhysicalResultColumn(dbCol);
                dbCols.add(dbCol);
                values.add(e.getValue() == null ? null : String.valueOf(e.getValue()));
            }
            if (dbCols.isEmpty()) {
                continue;
            }
            upsertAnalysisResultRow(item.getYwNo().trim(), dbCols, values);
        }
    }

    private Map<String, String> columnNameToKeyMap() {
        List<Map<String, Object>> configs = listAnalysisColumns();
        Map<String, String> m = new LinkedHashMap<String, String>();
        for (Map<String, Object> col : configs) {
            m.put(String.valueOf(col.get("column_name")), String.valueOf(col.get("column_key")));
        }
        return m;
    }

    private void upsertAnalysisResultRow(String ywNo, List<String> dbCols, List<Object> values) {
        StringBuilder insertCols = new StringBuilder("yw_no");
        StringBuilder placeholders = new StringBuilder("?");
        List<Object> args = new ArrayList<Object>();
        args.add(ywNo);
        for (int i = 0; i < dbCols.size(); i++) {
            insertCols.append(", ").append(dbCols.get(i));
            placeholders.append(", ?");
            args.add(values.get(i));
        }
        insertCols.append(", updated_at");
        placeholders.append(", NOW()");
        StringBuilder updateSet = new StringBuilder();
        for (int i = 0; i < dbCols.size(); i++) {
            if (i > 0) {
                updateSet.append(", ");
            }
            updateSet.append(dbCols.get(i)).append(" = EXCLUDED.").append(dbCols.get(i));
        }
        String sql = "INSERT INTO ticket_analysis_result (" + insertCols + ") VALUES (" + placeholders + ") "
                + "ON CONFLICT (yw_no) DO UPDATE SET " + updateSet + ", updated_at = NOW()";
        jdbcTemplate.update(sql, args.toArray());
    }

    private String resolvePivotDimensionKey(String dim) {
        if (dim == null || dim.trim().isEmpty()) {
            return dim;
        }
        String d = dim.trim();
        for (String tc : TicketColumns.DATA_COLUMNS) {
            if (d.equals(tc)) {
                return tc;
            }
        }
        List<Map<String, Object>> cols = listAnalysisColumns();
        for (Map<String, Object> c : cols) {
            if (d.equals(String.valueOf(c.get("column_key")))) {
                return String.valueOf(c.get("column_key"));
            }
        }
        for (Map<String, Object> c : cols) {
            if (d.equals(String.valueOf(c.get("column_name")))) {
                return String.valueOf(c.get("column_key"));
            }
        }
        return d;
    }

    @Override
    public void writeSampleTicketsExcel100(OutputStream outputStream) throws IOException {
        String[] phases = new String[] {
                "问题审核", "运维人员分析", "开发人员分析", "开发人员闭环", "运维人员闭环", "问题关闭"
        };
        String[] severities = new String[] {"严重", "较严重", "一般", "轻微"};
        String[] sites = new String[] {"北京局", "上海局", "深圳局", "杭州局", "成都局", "广州局"};
        String[] instanceNames = new String[] {"inst001", "inst002", "inst003", "inst004", "inst005", "inst006"};
        String[] businessNames = new String[] {"核心业务", "交易系统", "报表系统", "网关服务", "监控系统"};
        String[] handlers = new String[] {"张三", "李四", "王五", "赵六", "钱七", "孙八"};
        String[] durations = new String[] {"2小时", "4小时", "8小时", "16小时", "24小时", "48小时", "72小时"};
        String[] ctrlVers = new String[] {"V2.1.0", "V2.2.0", "V2.3.0", "V3.0.0", "V3.1.0", "V3.2.0"};
        String[] kernelVers = new String[] {"K505.1", "K506.0", "K507.1", "K508.0", "K509.0", "K510.1"};
        String[] hcsVersions = new String[] {"HCS 8.0.1", "HCS 8.0.2", "HCS 8.0.3", "HCS 8.1.0"};
        String[] hcsTypes = new String[] {"HCS", "轻量化"};
        String[] problemTypes = new String[] {"性能问题", "功能缺陷", "配置问题", "网络问题", "资源问题"};
        String[] rootCauseCategories = new String[] {"代码缺陷", "配置错误", "资源不足", "网络故障", "第三方问题"};
        String[] deployTypes = new String[] {"集中式", "分布式"};
        String[] yesNo = new String[] {"是", "否"};
        Random rnd = new Random(42);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(2025, java.util.Calendar.JANUARY, 1, 0, 0, 0);
        long baseMs = cal.getTimeInMillis();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("tickets");
            Row head = sheet.createRow(0);
            int hc = 0;
            head.createCell(hc++).setCellValue("工单号");
            for (String cn : TicketColumns.DATA_COLUMNS) {
                head.createCell(hc++).setCellValue(cn);
            }
            for (int i = 0; i < 100; i++) {
                Row r = sheet.createRow(i + 1);
                String yw = "YW" + (8800000000L + i);
                int dayOff = rnd.nextInt(421);
                String dateStr = sdf.format(new java.util.Date(baseMs + (long) dayOff * 86400000L));
                int c = 0;
                r.createCell(c++).setCellValue(yw);
                r.createCell(c++).setCellValue(phases[rnd.nextInt(phases.length)]);
                r.createCell(c++).setCellValue(severities[rnd.nextInt(severities.length)]);
                r.createCell(c++).setCellValue(sites[rnd.nextInt(sites.length)]);
                r.createCell(c++).setCellValue(instanceNames[rnd.nextInt(instanceNames.length)]);
                r.createCell(c++).setCellValue(businessNames[rnd.nextInt(businessNames.length)]);
                r.createCell(c++).setCellValue(handlers[rnd.nextInt(handlers.length)]);
                r.createCell(c++).setCellValue("问题描述样本" + (i + 1) + "：GaussDB 连接异常或查询超时相关描述");
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(handlers[rnd.nextInt(handlers.length)]);
                r.createCell(c++).setCellValue(handlers[rnd.nextInt(handlers.length)]);
                r.createCell(c++).setCellValue(handlers[rnd.nextInt(handlers.length)]);
                r.createCell(c++).setCellValue(handlers[rnd.nextInt(handlers.length)]);
                r.createCell(c++).setCellValue(handlers[rnd.nextInt(handlers.length)]);
                r.createCell(c++).setCellValue(handlers[rnd.nextInt(handlers.length)]);
                r.createCell(c++).setCellValue("进展概述" + (i + 1) + "：已联系现场排查网络与参数");
                r.createCell(c++).setCellValue(dateStr);
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(ctrlVers[rnd.nextInt(ctrlVers.length)]);
                r.createCell(c++).setCellValue("对外答复" + (i + 1) + "：已处理并建议后续观察");
                r.createCell(c++).setCellValue(durations[rnd.nextInt(durations.length)]);
                r.createCell(c++).setCellValue(yesNo[rnd.nextInt(yesNo.length)]);
                r.createCell(c++).setCellValue(yesNo[rnd.nextInt(yesNo.length)]);
                r.createCell(c++).setCellValue(yesNo[rnd.nextInt(yesNo.length)]);
                r.createCell(c++).setCellValue("问题根因" + (i + 1) + "：与配置/资源/网络相关的示例根因");
                r.createCell(c++).setCellValue("DTS" + String.format("%06d", i + 1));
                r.createCell(c++).setCellValue("规避措施" + (i + 1) + "：临时规避方案");
                r.createCell(c++).setCellValue(yesNo[rnd.nextInt(yesNo.length)]);
                r.createCell(c++).setCellValue("自动恢复");
                r.createCell(c++).setCellValue(yesNo[rnd.nextInt(yesNo.length)]);
                r.createCell(c++).setCellValue(yesNo[rnd.nextInt(yesNo.length)]);
                r.createCell(c++).setCellValue("张三,李四");
                r.createCell(c++).setCellValue(kernelVers[rnd.nextInt(kernelVers.length)]);
                r.createCell(c++).setCellValue(hcsVersions[rnd.nextInt(hcsVersions.length)]);
                r.createCell(c++).setCellValue(hcsTypes[rnd.nextInt(hcsTypes.length)]);
                r.createCell(c++).setCellValue(problemTypes[rnd.nextInt(problemTypes.length)]);
                r.createCell(c++).setCellValue(rootCauseCategories[rnd.nextInt(rootCauseCategories.length)]);
                r.createCell(c++).setCellValue(deployTypes[rnd.nextInt(deployTypes.length)]);
            }
            wb.write(outputStream);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && value.trim().length() > 0;
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return "";
        }
        for (Object value : values) {
            String s = value == null ? "" : String.valueOf(value).trim();
            if (s.length() > 0) {
                return s;
            }
        }
        return "";
    }

    private Map<String, Object> progressPayload(String ywNo, String status, String message, int success, int failed, int total) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("ywNo", ywNo);
        payload.put("status", status);
        if (message != null) {
            payload.put("message", message);
        }
        payload.put("success", success);
        payload.put("failed", failed);
        payload.put("total", total);
        return payload;
    }

    private Map<String, Object> progressPayload(String ywNo, String status, String message, int success, int failed, int total,
            Map<String, Object> analysis) {
        Map<String, Object> payload = progressPayload(ywNo, status, message, success, failed, total);
        if (analysis != null) {
            payload.put("analysis", analysis);
        }
        return payload;
    }

    @Override
    public List<Map<String, Object>> pivotPersonnel(PersonnelPivotRequest request) {
        String field = request.getField();
        if (field == null || field.trim().isEmpty()) {
            field = "运维人员分析人";
        }
        LocalDate start = parseLocalDate(request.getStartDate());
        LocalDate end = parseLocalDate(request.getEndDate());
        String quotedField = TicketColumns.quote(field);
        String sql = "SELECT " + quotedField + " AS person, COUNT(*) AS cnt FROM ticket WHERE " + quotedField + " IS NOT NULL AND " + quotedField + " != '' ";
        if (start != null) {
            sql += " AND \"创建日期\" >= '" + start.toString() + "' ";
        }
        if (end != null) {
            sql += " AND \"创建日期\" <= '" + end.toString() + "' ";
        }
        sql += " GROUP BY " + quotedField + " ORDER BY cnt DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", String.valueOf(row.get("person")));
            item.put("count", ((Number) row.get("cnt")).intValue());
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> pivotPersonnelTransfer(PersonnelPivotRequest request) {
        LocalDate start = parseLocalDate(request.getStartDate());
        LocalDate end = parseLocalDate(request.getEndDate());
        String sql = "SELECT \"运维人员分析人\" AS person, COUNT(*) AS cnt FROM ticket " +
                " WHERE \"运维人员分析人\" IS NOT NULL AND \"运维人员分析人\" != '' " +
                " AND ((\"开发人员分析人\" IS NOT NULL AND \"开发人员分析人\" != '') OR (\"协同处理人\" IS NOT NULL AND \"协同处理人\" != '')) ";
        if (start != null) {
            sql += " AND \"创建日期\" >= '" + start.toString() + "' ";
        }
        if (end != null) {
            sql += " AND \"创建日期\" <= '" + end.toString() + "' ";
        }
        sql += " GROUP BY \"运维人员分析人\" ORDER BY cnt DESC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", String.valueOf(row.get("person")));
            item.put("count", ((Number) row.get("cnt")).intValue());
            result.add(item);
        }
        return result;
    }

    private static class AnalyzeItemResult {
        private final boolean success;
        private final String ywNo;
        private final Map<String, Object> analysisResult;
        private final String message;

        private AnalyzeItemResult(boolean success, String ywNo, Map<String, Object> analysisResult, String message) {
            this.success = success;
            this.ywNo = ywNo;
            this.analysisResult = analysisResult;
            this.message = message;
        }

        private static AnalyzeItemResult success(String ywNo, Map<String, Object> analysisResult) {
            return new AnalyzeItemResult(true, ywNo, analysisResult, null);
        }

        private static AnalyzeItemResult failed(String ywNo, String message) {
            return new AnalyzeItemResult(false, ywNo, null, message);
        }
    }
}
