package com.example.gaussdb.dto;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public class Requests {
    public static class AnalyzeRequest {
        private List<String> ywNos;
        private List<String> selectedColumnKeys;
        private String backgroundPrompt;

        public List<String> getYwNos() {
            return ywNos;
        }

        public void setYwNos(List<String> ywNos) {
            this.ywNos = ywNos;
        }

        public List<String> getSelectedColumnKeys() {
            return selectedColumnKeys;
        }

        public void setSelectedColumnKeys(List<String> selectedColumnKeys) {
            this.selectedColumnKeys = selectedColumnKeys;
        }

        public String getBackgroundPrompt() {
            return backgroundPrompt;
        }

        public void setBackgroundPrompt(String backgroundPrompt) {
            this.backgroundPrompt = backgroundPrompt;
        }
    }

    public static class TicketSearchRequest {
        private String ywNo;
        private String createLogKeyword;
        private String analyzedAfter;
        private Map<String, String> analysisFilters;

        public String getYwNo() {
            return ywNo;
        }

        public void setYwNo(String ywNo) {
            this.ywNo = ywNo;
        }

        public String getCreateLogKeyword() {
            return createLogKeyword;
        }

        public void setCreateLogKeyword(String createLogKeyword) {
            this.createLogKeyword = createLogKeyword;
        }

        public String getAnalyzedAfter() {
            return analyzedAfter;
        }

        public void setAnalyzedAfter(String analyzedAfter) {
            this.analyzedAfter = analyzedAfter;
        }

        public Map<String, String> getAnalysisFilters() {
            return analysisFilters;
        }

        public void setAnalysisFilters(Map<String, String> analysisFilters) {
            this.analysisFilters = analysisFilters;
        }

        private Map<String, Object> sessionAnalysis;

        public Map<String, Object> getSessionAnalysis() {
            return sessionAnalysis;
        }

        public void setSessionAnalysis(Map<String, Object> sessionAnalysis) {
            this.sessionAnalysis = sessionAnalysis;
        }
    }

    public static class PivotRequest {
        private String mode;
        private String startDate;
        private String endDate;
        private String granularity;
        private String dimension;
        private Boolean topLevelOnly;
        private Map<String, String> selectedPath;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getEndDate() {
            return endDate;
        }

        public void setEndDate(String endDate) {
            this.endDate = endDate;
        }

        public String getGranularity() {
            return granularity;
        }

        public void setGranularity(String granularity) {
            this.granularity = granularity;
        }

        public String getDimension() {
            return dimension;
        }

        public void setDimension(String dimension) {
            this.dimension = dimension;
        }

        public Boolean getTopLevelOnly() {
            return topLevelOnly;
        }

        public void setTopLevelOnly(Boolean topLevelOnly) {
            this.topLevelOnly = topLevelOnly;
        }

        public Map<String, String> getSelectedPath() {
            return selectedPath;
        }

        public void setSelectedPath(Map<String, String> selectedPath) {
            this.selectedPath = selectedPath;
        }
    }

    public static class SaveAnalysisItem {
        @NotBlank
        private String ywNo;
        private Map<String, Object> analysis;

        public String getYwNo() {
            return ywNo;
        }

        public void setYwNo(String ywNo) {
            this.ywNo = ywNo;
        }

        public Map<String, Object> getAnalysis() {
            return analysis;
        }

        public void setAnalysis(Map<String, Object> analysis) {
            this.analysis = analysis;
        }
    }

    public static class SaveAnalysisRequest {
        private List<SaveAnalysisItem> items;

        public List<SaveAnalysisItem> getItems() {
            return items;
        }

        public void setItems(List<SaveAnalysisItem> items) {
            this.items = items;
        }
    }

    public static class AnalysisColumnRequest {
        private String columnKey;
        @NotBlank
        private String columnName;
        @NotBlank
        private String description;
        private List<String> allowedValues;
        private Boolean topLevel;

        public String getColumnKey() {
            return columnKey;
        }

        public void setColumnKey(String columnKey) {
            this.columnKey = columnKey;
        }

        public String getColumnName() {
            return columnName;
        }

        public void setColumnName(String columnName) {
            this.columnName = columnName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getAllowedValues() {
            return allowedValues;
        }

        public void setAllowedValues(List<String> allowedValues) {
            this.allowedValues = allowedValues;
        }

        public Boolean getTopLevel() {
            return topLevel;
        }

        public void setTopLevel(Boolean topLevel) {
            this.topLevel = topLevel;
        }
    }
}
