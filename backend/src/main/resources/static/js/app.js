const { createApp, ref } = Vue;

createApp({
  setup() {
    const activeMenu = ref("ticket-analysis");
    const onMenuSelect = (index) => {
      activeMenu.value = index;
    };
    const workflowStep = ref(0);
    const step1Completed = ref(false);
    const importMessage = ref("");
    const currentTaskId = ref(null);
    const isAnalyzing = ref(false);
    let analyzeEventSource = null;
    const analyzePercent = ref(0);
    const taskStatusByYw = ref({});
    const taskErrorByYw = ref({});
    const analysisStarted = ref(false);
    const analysisColumns = ref([]);
    const activeAnalysisColumnKeys = ref([]);
    const importedColumns = ref([]);
    const pivotDateRange = ref([]);
    const pivotError = ref("");
    const trendGranularity = ref("month");
    const pivotTopDimension = ref("");
    const cascadeDimension = ref("");
    const topLevelColumns = ref([]);
    const cascadeColumns = ref([]);
    const showPivotResult = ref(false);
    const selectedPivotValue = ref("");
    const selectedPivotLabel = ref("");
    const pivotPath = ref({});
    let trendChart = null;
    let distChart = null;

    const clearPivotError = () => {
      pivotError.value = "";
    };

    const resizePivotCharts = () => {
      if (trendChart) {
        trendChart.resize();
      }
      if (distChart) {
        distChart.resize();
      }
    };

    const onWindowResize = () => {
      resizePivotCharts();
    };
    window.addEventListener("resize", onWindowResize);

    const disposePivotCharts = () => {
      window.removeEventListener("resize", onWindowResize);
      if (trendChart) {
        trendChart.dispose();
        trendChart = null;
      }
      if (distChart) {
        distChart.dispose();
        distChart = null;
      }
    };
    if (typeof Vue.onUnmounted === "function") {
      Vue.onUnmounted(disposePivotCharts);
    }
    const ticketFieldColumns = [
      "起始日期",
      "当前阶段",
      "局点",
      "当前处理人",
      "问题描述",
      "进展概述",
      "管控版本",
      "内核版本",
      "问题根因",
      "对外答复"
    ];
    const configForm = ref({
      columnKey: "",
      columnName: "",
      description: "",
      allowedText: "",
      topLevel: false
    });
    const showAddConfigDialog = ref(false);
    const configError = ref("");
    const backgroundPrompt = ref("");
    const resultYwKeyword = ref("");
    const resultRows = ref([]);
    const resultPage = ref(1);
    const lastTicketList = ref([]);
    const sessionAnalysisByYw = Vue.reactive({});
    const saveMessage = ref("");
    const persistedYwKeyword = ref("");
    const persistedRows = ref([]);
    const persistedPage = ref(1);

    const api = window.apiClient.request;
    const step2Completed = Vue.computed(() =>
      step1Completed.value
      && !!backgroundPrompt.value
      && backgroundPrompt.value.trim().length > 0
      && activeAnalysisColumnKeys.value.length > 0
    );
    const pagedResultRows = Vue.computed(() => {
      const start = (resultPage.value - 1) * 10;
      return resultRows.value.slice(start, start + 10);
    });
    const pagedPersistedRows = Vue.computed(() => {
      const start = (persistedPage.value - 1) * 10;
      return persistedRows.value.slice(start, start + 10);
    });
    const canSaveAnalysis = Vue.computed(() => {
      for (let i = 0; i < resultRows.value.length; i++) {
        const r = resultRows.value[i];
        if (r.task_status !== "SUCCESS") {
          continue;
        }
        const a = sessionAnalysisByYw[r.yw_no];
        if (a && typeof a === "object" && Object.keys(a).length > 0) {
          return true;
        }
      }
      return false;
    });
    const importedColumnsToShow = Vue.computed(() => {
      const cols = [];
      for (let i = 0; i < importedColumns.value.length; i++) {
        const name = importedColumns.value[i];
        if (name === "工单号" || name === "YW单号" || name === "YW工单号" || name === "YW_NO" || name === "yw_no") {
          continue;
        }
        cols.push(name);
      }
      return cols;
    });
    const refreshWorkflowStep = () => {
      if (step2Completed.value) {
        workflowStep.value = 2;
      } else if (step1Completed.value) {
        workflowStep.value = 1;
      } else {
        workflowStep.value = 0;
      }
    };

    const mergeRowsFromCache = () => {
      resultRows.value = normalizeRows(lastTicketList.value);
    };

    const clearSaveMessage = () => {
      saveMessage.value = "";
    };

    const importExcel = async (file) => {
      const res = await window.apiClient.importExcel(file);
      const data = await res.json();
      importMessage.value = "已导入 " + data.imported + " 条";
      step1Completed.value = true;
      refreshWorkflowStep();
      await loadTicketColumns();
      await searchResultRows();
    };

    const startAnalyze = async () => {
      if (!step2Completed.value) {
        configError.value = "请先完成步骤2：填写背景提示词并至少选择1个分析列";
        return;
      }
      configError.value = "";
      Object.keys(sessionAnalysisByYw).forEach((k) => {
        delete sessionAnalysisByYw[k];
      });
      taskStatusByYw.value = {};
      taskErrorByYw.value = {};
      analyzePercent.value = 0;
      analysisStarted.value = true;
      const ywNos = resultRows.value.map((t) => t.yw_no).filter(Boolean);
      if (ywNos.length === 0) {
        configError.value = "没有可分析的工单，请先导入并查询到工单数据";
        return;
      }
      for (let i = 0; i < ywNos.length; i++) {
        taskStatusByYw.value[ywNos[i]] = "RUNNING";
      }
      const res = await api("/analyze/tasks", {
        method: "POST",
        body: JSON.stringify({
          ywNos,
          selectedColumnKeys: activeAnalysisColumnKeys.value,
          backgroundPrompt: backgroundPrompt.value
        })
      });
      const data = await res.json();
      currentTaskId.value = data.taskId;
      isAnalyzing.value = true;
      if (analyzeEventSource) {
        analyzeEventSource.close();
      }
      analyzeEventSource = new EventSource("/api/analyze/tasks/" + data.taskId + "/stream");
      analyzeEventSource.addEventListener("progress", (e) => {
        const p = JSON.parse(e.data);
        if (p.ywNo && p.status === "SUCCESS") {
          taskStatusByYw.value[p.ywNo] = "SUCCESS";
          taskErrorByYw.value[p.ywNo] = "";
          if (p.analysis && typeof p.analysis === "object") {
            sessionAnalysisByYw[p.ywNo] = p.analysis;
          }
        } else if (p.ywNo && p.status === "FAILED") {
          taskStatusByYw.value[p.ywNo] = "FAILED";
          taskErrorByYw.value[p.ywNo] = p.message || "分析失败";
        } else if (p.status === "STOPPED") {
          const keys = Object.keys(taskStatusByYw.value);
          for (let i = 0; i < keys.length; i++) {
            if (taskStatusByYw.value[keys[i]] === "RUNNING") {
              taskStatusByYw.value[keys[i]] = "STOPPED";
            }
          }
        }
        if (p.total) {
          const done = (p.success || 0) + (p.failed || 0);
          analyzePercent.value = Math.round((done * 100) / p.total);
        }
        mergeRowsFromCache();
        if (p.finished) {
          analyzeEventSource.close();
          analyzeEventSource = null;
          isAnalyzing.value = false;
          mergeRowsFromCache();
        }
      });
    };

    const stopAnalyze = async () => {
      if (!currentTaskId.value) {
        return;
      }
      await api("/analyze/tasks/" + currentTaskId.value + "/stop", { method: "POST" });
      if (analyzeEventSource) {
        analyzeEventSource.close();
        analyzeEventSource = null;
      }
      isAnalyzing.value = false;
      mergeRowsFromCache();
    };

    const saveAnalysisToDb = async () => {
      saveMessage.value = "";
      const items = [];
      for (let i = 0; i < resultRows.value.length; i++) {
        const r = resultRows.value[i];
        if (r.task_status !== "SUCCESS") {
          continue;
        }
        const a = sessionAnalysisByYw[r.yw_no];
        if (!a || typeof a !== "object") {
          continue;
        }
        items.push({ ywNo: r.yw_no, analysis: Object.assign({}, a) });
      }
      if (items.length === 0) {
        saveMessage.value = "没有可保存的成功分析结果";
        return;
      }
      try {
        const res = await api("/analysis-results/save", {
          method: "POST",
          body: JSON.stringify({ items })
        });
        if (!res.ok) {
          const t = await res.text();
          saveMessage.value = "保存失败: " + t;
          return;
        }
        saveMessage.value = "已保存 " + items.length + " 条工单的本次分析结果到分析结果表（原工单已在导入时入库）。";
      } catch (err) {
        saveMessage.value = "保存失败: " + (err && err.message ? err.message : "未知错误");
      }
    };

    const loadConfigs = async () => {
      const res = await api("/analysis-columns");
      analysisColumns.value = await res.json();
      if (activeAnalysisColumnKeys.value.length === 0) {
        activeAnalysisColumnKeys.value = analysisColumns.value.map((c) => c.column_key);
      }
      topLevelColumns.value = analysisColumns.value
        .filter((c) => parseBoolean(c.is_top_level))
        .map((c) => ({ columnKey: c.column_key, columnName: c.column_name }));
      if (!pivotTopDimension.value && topLevelColumns.value.length > 0) {
        pivotTopDimension.value = topLevelColumns.value[0].columnKey;
      }
      refreshWorkflowStep();
    };
    const loadTicketColumns = async () => {
      const res = await api("/tickets/columns");
      if (!res.ok) {
        return;
      }
      const data = await res.json();
      importedColumns.value = data.columns || [];
    };

    const addConfig = async () => {
      configError.value = "";
      if (!configForm.value.columnKey || !configForm.value.columnKey.trim()) {
        configError.value = "列 Key 不能为空";
        return;
      }
      if (!configForm.value.columnName || !configForm.value.columnName.trim()) {
        configError.value = "分析列名不能为空";
        return;
      }
      if (!configForm.value.description || !configForm.value.description.trim()) {
        configError.value = "分析列解读不能为空";
        return;
      }
      try {
        const beforeKeys = activeAnalysisColumnKeys.value.slice(0);
        const response = await api("/analysis-columns", {
          method: "POST",
          body: JSON.stringify({
            columnKey: configForm.value.columnKey.trim(),
            columnName: configForm.value.columnName,
            description: configForm.value.description,
            allowedValues: configForm.value.allowedText.split(",").map((s) => s.trim()).filter(Boolean),
            topLevel: !!configForm.value.topLevel
          })
        });
        if (!response.ok) {
          const text = await response.text();
          configError.value = "新增失败（HTTP " + response.status + "）: " + text;
          return;
        }
        configForm.value = {
          columnKey: "",
          columnName: "",
          description: "",
          allowedText: "",
          topLevel: false
        };
        await loadConfigs();
        const keySet = {};
        for (let i = 0; i < beforeKeys.length; i++) {
          keySet[beforeKeys[i]] = true;
        }
        for (let i = 0; i < analysisColumns.value.length; i++) {
          const k = analysisColumns.value[i].column_key;
          if (!keySet[k]) {
            beforeKeys.push(k);
            break;
          }
        }
        activeAnalysisColumnKeys.value = beforeKeys;
        showAddConfigDialog.value = false;
        refreshWorkflowStep();
      } catch (error) {
        configError.value = "新增失败: " + (error && error.message ? error.message : "未知错误");
      }
    };

    const searchResultRows = async () => {
      const body = { ywNo: resultYwKeyword.value, createLogKeyword: "" };
      const res = await api("/tickets/search", { method: "POST", body: JSON.stringify(body) });
      const rows = await res.json();
      lastTicketList.value = rows;
      resultRows.value = normalizeRows(rows);
      resultPage.value = 1;
    };

    const searchPersistedTickets = async () => {
      const body = { ywNo: persistedYwKeyword.value, createLogKeyword: "" };
      const res = await api("/persisted-tickets/search", { method: "POST", body: JSON.stringify(body) });
      persistedRows.value = await res.json();
      persistedPage.value = 1;
    };

    const exportTickets = async () => {
      const sessionAnalysis = JSON.parse(JSON.stringify(sessionAnalysisByYw));
      const body = {
        ywNo: resultYwKeyword.value,
        createLogKeyword: "",
        sessionAnalysis
      };
      const res = await api("/tickets/export", { method: "POST", body: JSON.stringify(body) });
      const blob = await res.blob();
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      const cd = res.headers.get("content-disposition") || "";
      const match = cd.match(/filename="?([^"]+)"?/i);
      a.download = match && match[1] ? match[1] : "tickets.xlsx";
      a.click();
    };

    const queryPivot = async (mode, dimension, topLevelOnly) => {
      const startDate = pivotDateRange.value && pivotDateRange.value[0] ? pivotDateRange.value[0] : "";
      const endDate = pivotDateRange.value && pivotDateRange.value[1] ? pivotDateRange.value[1] : "";
      const res = await api("/pivot/query", {
        method: "POST",
        body: JSON.stringify({
          mode,
          startDate,
          endDate,
          granularity: trendGranularity.value,
          dimension,
          topLevelOnly,
          selectedPath: pivotPath.value
        })
      });
      return await res.json();
    };

    const renderTrendChart = (rows) => {
      const dom = document.getElementById("trend-chart");
      if (!dom) {
        return;
      }
      if (!trendChart) {
        trendChart = echarts.init(dom);
      }
      const names = rows.map((r) => r.name);
      const counts = rows.map((r) => r.count);
      trendChart.setOption({
        title: { text: "工单数", left: "center", top: 6, textStyle: { fontSize: 13, fontWeight: 600 } },
        grid: { left: 48, right: 24, top: 44, bottom: names.length > 12 ? 56 : 36 },
        tooltip: { trigger: "axis" },
        toolbox: {
          right: 12,
          top: 6,
          feature: { saveAsImage: { title: "保存为图片" }, dataZoom: { title: { zoom: "区域缩放", back: "还原" } } }
        },
        dataZoom: names.length > 14 ? [{ type: "inside" }, { type: "slider", bottom: 8, height: 18 }] : [{ type: "inside" }],
        xAxis: { type: "category", data: names, axisLabel: { interval: 0, rotate: names.length > 10 ? 28 : 0 } },
        yAxis: { type: "value", minInterval: 1 },
        series: [{
          type: "bar",
          data: counts,
          itemStyle: { color: "#5b8ff9" },
          label: { show: true, position: "top", fontSize: 11 }
        }]
      }, true);
      resizePivotCharts();
    };

    const renderDistributionChart = (data, dimensionName, chartType) => {
      const dom = document.getElementById("dist-chart");
      if (!dom) {
        return;
      }
      if (!distChart) {
        distChart = echarts.init(dom);
      }
      distChart.off("click");
      const baseSeriesData = data.map((item) => ({
        name: item.name,
        value: item.count,
        ratio: item.ratio
      }));
      const toolbox = {
        right: 12,
        top: 6,
        feature: {
          saveAsImage: { title: "保存为图片" },
          ...(chartType === "bar"
            ? { magicType: { type: ["line", "bar"], title: { line: "折线", bar: "柱状" } }, dataZoom: { title: { zoom: "区域缩放", back: "还原" } } }
            : {})
        }
      };
      const series = chartType === "bar"
        ? [{
          name: dimensionName || "数量",
          type: "bar",
          data: baseSeriesData.map((d) => d.value),
          itemStyle: { color: "#61a0ff" },
          label: {
            show: true,
            position: "top",
            fontSize: 11,
            formatter: (p) => {
              const row = data[p.dataIndex];
              return row.count + " (" + ((row.ratio || 0) * 100).toFixed(1) + "%)";
            }
          }
        }]
        : [{
          name: dimensionName || "占比",
          type: "pie",
          radius: ["36%", "62%"],
          data: baseSeriesData,
          emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: "rgba(0,0,0,0.15)" } },
          label: {
            formatter: (p) => {
              const ratio = p.data.ratio || 0;
              return p.name + "\n" + p.value + " (" + (ratio * 100).toFixed(1) + "%)";
            }
          }
        }];
      const option = {
        title: {
          text: dimensionName || "分类",
          left: "center",
          top: 6,
          textStyle: { fontSize: 13, fontWeight: 600 }
        },
        tooltip: {
          trigger: chartType === "bar" ? "axis" : "item",
          formatter: chartType === "pie"
            ? (p) => {
              const ratio = p.data.ratio || 0;
              return p.marker + p.name + "<br/>数量：" + p.value + "<br/>占比：" + (ratio * 100).toFixed(2) + "%";
            }
            : undefined
        },
        toolbox,
        grid: chartType === "bar"
          ? { left: 44, right: 20, top: 44, bottom: data.length > 10 ? 56 : 36 }
          : undefined,
        dataZoom: chartType === "bar" && data.length > 12
          ? [{ type: "inside", xAxisIndex: 0 }, { type: "slider", bottom: 8, height: 18 }]
          : undefined,
        xAxis: chartType === "bar" ? { type: "category", data: data.map((r) => r.name), axisLabel: { interval: 0, rotate: 22 } } : undefined,
        yAxis: chartType === "bar" ? { type: "value", minInterval: 1 } : undefined,
        series
      };
      distChart.setOption(option, true);
      distChart.on("click", (params) => {
        const name = chartType === "bar" ? data[params.dataIndex].name : params.name;
        if (!name) {
          return;
        }
        selectedPivotValue.value = name;
        selectedPivotLabel.value = dimensionName || "";
      });
      resizePivotCharts();
    };

    const runPivotAnalysis = async () => {
      if (!pivotDateRange.value || pivotDateRange.value.length !== 2) {
        pivotError.value = "请先选择数据分析的日期范围";
        return;
      }
      if (!pivotTopDimension.value) {
        pivotError.value = "请选择一个顶层分析列";
        return;
      }
      pivotError.value = "";
      pivotPath.value = {};
      selectedPivotValue.value = "";
      selectedPivotLabel.value = "";
      showPivotResult.value = false;
      const trend = await queryPivot("trend", "", true);
      const dist = await queryPivot("distribution", pivotTopDimension.value, true);
      topLevelColumns.value = dist.columns || [];
      cascadeColumns.value = analysisColumns.value
        .filter((c) => !parseBoolean(c.is_top_level))
        .map((c) => ({ columnKey: c.column_key, columnName: c.column_name }));
      showPivotResult.value = true;
      await Vue.nextTick();
      renderTrendChart(trend.data || []);
      renderDistributionChart(dist.data || [], columnKeyToName(pivotTopDimension.value), dist.chartType || "pie");
    };

    const runCascadeAnalysis = async () => {
      if (!selectedPivotValue.value) {
        pivotError.value = "请先点击分布图中的某个扇区或柱子";
        return;
      }
      if (!cascadeDimension.value) {
        pivotError.value = "请选择级联分析列";
        return;
      }
      pivotError.value = "";
      const currentDim = pivotPath.value.__lastDim || pivotTopDimension.value;
      const nextPath = Object.assign({}, pivotPath.value);
      nextPath[currentDim] = selectedPivotValue.value;
      delete nextPath.__lastDim;
      pivotPath.value = Object.assign({}, nextPath, { __lastDim: cascadeDimension.value });
      const cleanPath = Object.assign({}, nextPath);
      const startDate = pivotDateRange.value[0];
      const endDate = pivotDateRange.value[1];
      const res = await api("/pivot/query", {
        method: "POST",
        body: JSON.stringify({
          mode: "distribution",
          startDate,
          endDate,
          granularity: trendGranularity.value,
          dimension: cascadeDimension.value,
          topLevelOnly: false,
          selectedPath: cleanPath
        })
      });
      const dist = await res.json();
      await Vue.nextTick();
      renderDistributionChart(dist.data || [], columnKeyToName(cascadeDimension.value), dist.chartType || "pie");
      selectedPivotLabel.value = columnKeyToName(cascadeDimension.value);
      selectedPivotValue.value = "";
    };

    const parseBoolean = (value) => {
      if (value === true || value === "true" || value === 1 || value === "1") {
        return true;
      }
      return false;
    };

    const columnKeyToName = (key) => {
      const found = analysisColumns.value.find((c) => c.column_key === key);
      return found ? found.column_name : key;
    };

    const removeCurrentColumn = (columnKey) => {
      activeAnalysisColumnKeys.value = activeAnalysisColumnKeys.value.filter((k) => k !== columnKey);
      refreshWorkflowStep();
    };

    const resetCurrentColumns = () => {
      activeAnalysisColumnKeys.value = analysisColumns.value.map((c) => c.column_key);
      refreshWorkflowStep();
    };

    const onResultPageChange = (page) => {
      resultPage.value = page;
    };

    const onPersistedPageChange = (page) => {
      persistedPage.value = page;
    };

    const activeAnalysisColumns = Vue.computed(() =>
      analysisColumns.value
        .filter((c) => activeAnalysisColumnKeys.value.includes(c.column_key))
        .map((c) => {
          const row = Object.assign({}, c);
          const allowed = parseAllowedValues(c.allowed_values);
          row.allowedText = allowed.join(" / ");
          return row;
        })
    );
    const topLevelAnalysisColumns = Vue.computed(() =>
      analysisColumns.value
        .filter((c) => parseBoolean(c.is_top_level))
        .map((c) => ({ columnKey: c.column_key, columnName: c.column_name }))
    );

    const parseAllowedValues = (rawAllowed) => {
      if (!rawAllowed) {
        return [];
      }
      if (Array.isArray(rawAllowed)) {
        return rawAllowed;
      }
      if (typeof rawAllowed === "object") {
        if (Array.isArray(rawAllowed.values)) {
          return rawAllowed.values;
        }
        if (rawAllowed.value) {
          try {
            const fromValue = JSON.parse(rawAllowed.value);
            return Array.isArray(fromValue) ? fromValue : [];
          } catch (e) {
            return [];
          }
        }
      }
      if (typeof rawAllowed === "string") {
        try {
          const parsed = JSON.parse(rawAllowed);
          return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
          return [];
        }
      }
      return [];
    };
    const extractCreatedDate = (row) => {
      const v = row["起始日期"];
      if (v == null || v === "") {
        return "";
      }
      if (typeof v === "string") {
        return v;
      }
      try {
        return String(v);
      } catch (e) {
        return "";
      }
    };
    const normalizeRows = (rows) => {
      const result = [];
      for (let i = 0; i < rows.length; i++) {
        const row = rows[i];
        const analysis = Object.assign({}, sessionAnalysisByYw[row.yw_no] || {});
        const merged = {
          yw_no: row.yw_no,
          created_date: extractCreatedDate(row),
          task_status: taskStatusByYw.value[row.yw_no] || "",
          task_error: taskErrorByYw.value[row.yw_no] || ""
        };
        for (let r = 0; r < ticketFieldColumns.length; r++) {
          const key = ticketFieldColumns[r];
          const val = row[key];
          merged["raw_" + key] = val != null && val !== undefined ? val : "";
        }
        for (let c = 0; c < analysisColumns.value.length; c++) {
          const col = analysisColumns.value[c];
          if (!activeAnalysisColumnKeys.value.includes(col.column_key)) {
            continue;
          }
          const name = col.column_name;
          merged["ana_" + name] = analysis[name] || "";
        }
        result.push(merged);
      }
      return result;
    };

    loadConfigs();
    loadTicketColumns();
    searchResultRows();
    Vue.watch(backgroundPrompt, refreshWorkflowStep);
    Vue.watch(activeAnalysisColumnKeys, function () {
      mergeRowsFromCache();
    });
    Vue.watch(activeMenu, function (m) {
      if (m === "ticket-query") {
        searchPersistedTickets();
      }
      if (m === "data-analysis") {
        topLevelColumns.value = topLevelAnalysisColumns.value;
        if (!pivotTopDimension.value && topLevelColumns.value.length > 0) {
          pivotTopDimension.value = topLevelColumns.value[0].columnKey;
        }
        setTimeout(resizePivotCharts, 120);
      }
    });
    return {
      activeMenu,
      onMenuSelect,
      workflowStep,
      step1Completed,
      step2Completed,
      importMessage,
      importExcel,
      isAnalyzing,
      analysisStarted,
      analyzePercent,
      startAnalyze,
      stopAnalyze,
      saveAnalysisToDb,
      canSaveAnalysis,
      saveMessage,
      clearSaveMessage,
      analysisColumns,
      activeAnalysisColumns,
      configForm,
      showAddConfigDialog,
      configError,
      backgroundPrompt,
      addConfig,
      removeCurrentColumn,
      resetCurrentColumns,
      resultYwKeyword,
      resultRows,
      resultPage,
      pagedResultRows,
      importedColumnsToShow,
      searchResultRows,
      onResultPageChange,
      exportTickets,
      pivotDateRange,
      trendGranularity,
      pivotTopDimension,
      cascadeDimension,
      topLevelColumns,
      cascadeColumns,
      showPivotResult,
      selectedPivotValue,
      selectedPivotLabel,
      pivotError,
      clearPivotError,
      runPivotAnalysis,
      runCascadeAnalysis,
      persistedYwKeyword,
      persistedRows,
      persistedPage,
      pagedPersistedRows,
      searchPersistedTickets,
      onPersistedPageChange,
      ticketFieldColumns
    };
  }
}).use(ElementPlus).mount("#app");
