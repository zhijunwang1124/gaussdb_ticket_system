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
    const today = new Date();
    const sixMonthsAgo = new Date(today.getFullYear(), today.getMonth() - 6, today.getDate());
    const formatDate = (d) => d.toISOString().slice(0, 10);
    const pivotDateRange = ref([formatDate(sixMonthsAgo), formatDate(today)]);
    const pivotMaxDate = formatDate(today);
    const pivotError = ref("");
    const pivotDimension = ref("");
    const pivotDimensionColumns = ref([]);
    const pivotCascadeColumns = ref([]);
    const showPivotResult = ref(false);
    const distPivotResults = ref([]);
    const distCharts = {};
    const pivotDrillPath = ref([]);
    const drillDialogVisible = ref(false);
    const drillColumn = ref("");
    const drillTarget = ref(null);
    const pivotActiveTab = ref("time");
    let trendMonthChart = null;
    let trendWeekChart = null;
    let trendDayChart = null;
    let personnelOpsChart = null;
    let personnelDevChart = null;
    let personnelTransferChart = null;

    const clearPivotError = () => {
      pivotError.value = "";
    };

    const parseBoolean = (value) => {
      if (value === true || value === "true" || value === 1 || value === "1") {
        return true;
      }
      return false;
    };

    const resizePivotCharts = () => {
      if (trendMonthChart) {
        trendMonthChart.resize();
      }
      if (trendWeekChart) {
        trendWeekChart.resize();
      }
      if (trendDayChart) {
        trendDayChart.resize();
      }
      if (personnelOpsChart) {
        personnelOpsChart.resize();
      }
      if (personnelDevChart) {
        personnelDevChart.resize();
      }
      if (personnelTransferChart) {
        personnelTransferChart.resize();
      }
      Object.values(distCharts).forEach((chart) => {
        if (chart) {
          chart.resize();
        }
      });
    };

    // TAB切换时调用对应的resize
    const onPivotTabChange = async (tabName) => {
      await Vue.nextTick();
      // 根据当前TAB只resize对应的图表
      if (tabName === "time") {
        if (trendMonthChart) trendMonthChart.resize();
        if (trendWeekChart) trendWeekChart.resize();
        if (trendDayChart) trendDayChart.resize();
      } else if (tabName === "category") {
        Object.values(distCharts).forEach((chart) => {
          if (chart) chart.resize();
        });
      } else if (tabName === "personnel") {
        if (personnelOpsChart) personnelOpsChart.resize();
        if (personnelDevChart) personnelDevChart.resize();
        if (personnelTransferChart) personnelTransferChart.resize();
      }
    };

    // 刷新时间维度
    const refreshTimeDimension = async () => {
      if (!pivotDateRange.value || pivotDateRange.value.length !== 2) {
        pivotError.value = "请先选择数据分析的日期范围";
        return;
      }
      pivotError.value = "";
      try {
        const trendByMonth = await queryPivot("trend", "", "month");
        const trendByWeek = await queryPivot("trend", "", "week");
        const trendByDay = await queryPivot("trend", "", "day");
        renderTrendChart("trend-chart-month", trendByMonth.data || [], "按月工单数");
        renderTrendChart("trend-chart-week", trendByWeek.data || [], "按周工单数");
        renderTrendChart("trend-chart-day", trendByDay.data || [], "按日工单数");
      } catch (err) {
        pivotError.value = "刷新时间维度失败: " + (err && err.message ? err.message : "未知错误");
      }
    };

    // 刷新分类维度
    const refreshCategoryDimension = async () => {
      if (!pivotDateRange.value || pivotDateRange.value.length !== 2) {
        pivotError.value = "请先选择数据分析的日期范围";
        return;
      }
      pivotError.value = "";
      // 清空现有分类图表
      distPivotResults.value.forEach((result) => {
        if (distCharts[result.id]) {
          distCharts[result.id].dispose();
          delete distCharts[result.id];
        }
      });
      distPivotResults.value = [];
      pivotDrillPath.value = [];
      // 重新执行分类分析
      await runDimensionPivot();
    };

    // 刷新人员维度
    const refreshPersonnelDimension = async () => {
      if (!pivotDateRange.value || pivotDateRange.value.length !== 2) {
        pivotError.value = "请先选择数据分析的日期范围";
        return;
      }
      pivotError.value = "";
      try {
        const startDate = pivotDateRange.value[0];
        const endDate = pivotDateRange.value[1];
        const personnelOpsData = await queryPersonnelPivot("运维人员分析人", startDate, endDate);
        const personnelDevData = await queryPersonnelPivot("开发人员分析人", startDate, endDate);
        const personnelTransferData = await queryPersonnelTransferPivot(startDate, endDate);
        renderPersonnelChart("personnel-ops-chart", personnelOpsData, "运维人员处理工单数");
        renderPersonnelChart("personnel-dev-chart", personnelDevData, "开发人员处理工单数");
        renderPersonnelChart("personnel-transfer-chart", personnelTransferData, "运维人员透传工单数");
      } catch (err) {
        pivotError.value = "刷新人员维度失败: " + (err && err.message ? err.message : "未知错误");
      }
    };

    const onWindowResize = () => {
      resizePivotCharts();
    };
    window.addEventListener("resize", onWindowResize);

    const disposePivotCharts = () => {
      window.removeEventListener("resize", onWindowResize);
      if (trendMonthChart) {
        trendMonthChart.dispose();
        trendMonthChart = null;
      }
      if (trendWeekChart) {
        trendWeekChart.dispose();
        trendWeekChart = null;
      }
      if (trendDayChart) {
        trendDayChart.dispose();
        trendDayChart = null;
      }
      if (personnelOpsChart) {
        personnelOpsChart.dispose();
        personnelOpsChart = null;
      }
      if (personnelDevChart) {
        personnelDevChart.dispose();
        personnelDevChart = null;
      }
      if (personnelTransferChart) {
        personnelTransferChart.dispose();
        personnelTransferChart = null;
      }
      Object.keys(distCharts).forEach((key) => {
        if (distCharts[key]) {
          distCharts[key].dispose();
          delete distCharts[key];
        }
      });
      distPivotResults.value = [];
    };
    if (typeof Vue.onUnmounted === "function") {
      Vue.onUnmounted(disposePivotCharts);
    }
    const ticketFieldColumns = [
      "当前阶段",
      "问题严重性",
      "局点",
      "实例简称",
      "业务名称",
      "当前处理人",
      "描述",
      "滞留时间",
      "SLA时间",
      "问题审核人",
      "运维人员分析人",
      "开发人员分析人",
      "开发人员闭环人",
      "运维人员闭环人",
      "问题审核关闭人",
      "进展概述",
      "创建日期",
      "问题审核总时长",
      "运维人员分析总时长",
      "开发人员分析总时长",
      "开发人员闭环总时长",
      "运维人员闭环总时长",
      "问题审核关闭总时长",
      "管控版本",
      "对外答复",
      "故障到恢复用时",
      "是否涉及故障恢复",
      "磐石版本是否涉及",
      "是否需要预警",
      "问题根因",
      "DTS单号",
      "规避措施",
      "是否质量问题",
      "恢复方法",
      "是否咨询问题",
      "是否涉及内核升级",
      "协同处理人",
      "高斯版本",
      "HCS版本号",
      "HCS/轻量化",
      "问题类型",
      "根因分类",
      "部署形态"
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
      try {
        const res = await api("/analysis-columns");
        analysisColumns.value = await res.json();
        console.log("Loaded analysisColumns:", analysisColumns.value);
        if (activeAnalysisColumnKeys.value.length === 0) {
          activeAnalysisColumnKeys.value = analysisColumns.value.map((c) => c.column_key);
        }
        // 填充顶层分析列下拉选项
        const topLevelColumns = analysisColumns.value.filter((c) => parseBoolean(c.is_top_level));
        console.log("Top level columns:", topLevelColumns);
        pivotDimensionColumns.value = topLevelColumns.map((c) => ({
          columnKey: c.column_key,
          columnName: c.column_name,
          isTopLevel: true
        }));
        // 填充非顶层分析列（用于下钻）
        const cascadeColumns = analysisColumns.value.filter((c) => !parseBoolean(c.is_top_level));
        pivotCascadeColumns.value = cascadeColumns.map((c) => ({
          columnKey: c.column_key,
          columnName: c.column_name,
          isTopLevel: false
        }));
        if (!pivotDimension.value && pivotDimensionColumns.value.length > 0) {
          pivotDimension.value = pivotDimensionColumns.value[0].columnKey;
        }
        console.log("pivotDimensionColumns:", pivotDimensionColumns.value, "pivotCascadeColumns:", pivotCascadeColumns.value, "pivotDimension:", pivotDimension.value);
        refreshWorkflowStep();
      } catch (err) {
        console.error("loadConfigs error:", err);
      }
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

    const queryPivot = async (mode, dimension, granularity) => {
      const startDate = pivotDateRange.value && pivotDateRange.value[0] ? pivotDateRange.value[0] : "";
      const endDate = pivotDateRange.value && pivotDateRange.value[1] ? pivotDateRange.value[1] : "";
      const res = await api("/pivot/query", {
        method: "POST",
        body: JSON.stringify({
          mode,
          startDate,
          endDate,
          granularity: granularity || "month",
          dimension,
          topLevelOnly: false,
          selectedPath: {}
        })
      });
      return await res.json();
    };

    const filterRecentTrendData = (rows, domId) => {
      if (!rows || rows.length === 0) return rows;
      const sorted = rows.slice().sort((a, b) => String(a.name).localeCompare(String(b.name)));
      const limit = domId === "trend-chart-month" ? 12 : domId === "trend-chart-week" ? 8 : 14;
      if (sorted.length <= limit) return sorted;
      return sorted.slice(sorted.length - limit);
    };

    const renderTrendChart = (domId, rows, title) => {
      const dom = document.getElementById(domId);
      if (!dom) {
        return;
      }
      let chartRef = null;
      if (domId === "trend-chart-month") {
        if (!trendMonthChart) {
          trendMonthChart = echarts.init(dom);
        }
        chartRef = trendMonthChart;
      } else if (domId === "trend-chart-week") {
        if (!trendWeekChart) {
          trendWeekChart = echarts.init(dom);
        }
        chartRef = trendWeekChart;
      } else {
        if (!trendDayChart) {
          trendDayChart = echarts.init(dom);
        }
        chartRef = trendDayChart;
      }
      if (!rows || rows.length === 0) {
        chartRef.setOption({
          title: {
            text: title,
            left: "center",
            top: 6,
            textStyle: { fontSize: 13, fontWeight: 600 }
          },
          graphic: {
            type: "text",
            left: "center",
            top: "middle",
            style: {
              text: "该时间范围内无工单数据",
              fill: "#999",
              fontSize: 12
            }
          },
          xAxis: { show: false },
          yAxis: { show: false },
          series: []
        }, true);
        resizePivotCharts();
        return;
      }
      const filtered = filterRecentTrendData(rows, domId);
      const names = filtered.map((r) => r.name);
      const counts = filtered.map((r) => r.count);
      chartRef.setOption({
        title: { text: title, left: "center", top: 6, textStyle: { fontSize: 13, fontWeight: 600 } },
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

    const renderDistributionChart = (domId, data, dimensionName, chartType, dimensionKey) => {
      const dom = document.getElementById(domId);
      if (!dom) {
        return;
      }
      if (!distCharts[domId]) {
        distCharts[domId] = echarts.init(dom);
      }
      const chart = distCharts[domId];
      chart.off("click");
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
      chart.setOption(option, true);
      
      // 添加点击事件，用于下钻分析
      chart.on("click", (params) => {
        if (params.componentType === "series") {
          const clickedValue = params.name;
          drillTarget.value = {
            dimensionKey: dimensionKey,
            dimensionName: dimensionName,
            value: clickedValue
          };
          drillColumn.value = "";
          drillDialogVisible.value = true;
        }
      });
      
      resizePivotCharts();
    };

    const runPivotAnalysis = async () => {
      if (!pivotDateRange.value || pivotDateRange.value.length !== 2) {
        pivotError.value = "请先选择数据分析的日期范围";
        return;
      }
      pivotError.value = "";
      
      // 执行分析，结束后保持时间维度TAB（默认）
      await runPivotAnalysisInternal();
      pivotActiveTab.value = "time";
    };

    // 查询人员透视数据
    const queryPersonnelPivot = async (field, startDate, endDate) => {
      const res = await api("/pivot/personnel", {
        method: "POST",
        body: JSON.stringify({ field, startDate, endDate })
      });
      return await res.json();
    };

    // 查询人员透传数据（有开发人员或协同人员的工单，按运维人员统计）
    const queryPersonnelTransferPivot = async (startDate, endDate) => {
      const res = await api("/pivot/personnel-transfer", {
        method: "POST",
        body: JSON.stringify({ startDate, endDate })
      });
      return await res.json();
    };

    // 渲染人员柱状图
    const renderPersonnelChart = (domId, data, title) => {
      const dom = document.getElementById(domId);
      if (!dom) return;
      
      if (domId === "personnel-ops-chart") {
        if (!personnelOpsChart) personnelOpsChart = echarts.init(dom);
      } else if (domId === "personnel-dev-chart") {
        if (!personnelDevChart) personnelDevChart = echarts.init(dom);
      } else if (domId === "personnel-transfer-chart") {
        if (!personnelTransferChart) personnelTransferChart = echarts.init(dom);
      }
      
      const chart = domId === "personnel-ops-chart" ? personnelOpsChart 
        : domId === "personnel-dev-chart" ? personnelDevChart 
        : personnelTransferChart;
      
      if (!data || data.length === 0) {
        chart.setOption({
          title: { text: title, left: "center", top: 6, textStyle: { fontSize: 13, fontWeight: 600 } },
          graphic: { type: "text", left: "center", top: "middle", style: { text: "无数据", fill: "#999", fontSize: 12 } },
          xAxis: { show: false },
          yAxis: { show: false },
          series: []
        }, true);
        resizePivotCharts();
        return;
      }
      
      const names = data.map((r) => r.name);
      const counts = data.map((r) => r.count);
      
      chart.setOption({
        title: { text: title, left: "center", top: 6, textStyle: { fontSize: 13, fontWeight: 600 } },
        grid: { left: 48, right: 24, top: 44, bottom: names.length > 12 ? 56 : 36 },
        tooltip: { trigger: "axis" },
        toolbox: { right: 12, top: 6, feature: { saveAsImage: { title: "保存为图片" } } },
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

    const runDimensionPivot = async () => {
      console.log("runDimensionPivot called");
      console.log("pivotDateRange:", pivotDateRange.value);
      console.log("pivotDimension:", pivotDimension.value);
      console.log("pivotDimensionColumns:", pivotDimensionColumns.value);
      
      if (!pivotDateRange.value || pivotDateRange.value.length !== 2) {
        pivotError.value = "请先选择数据分析的日期范围";
        console.log("Error: no date range");
        return;
      }
      if (!pivotDimension.value) {
        if (pivotDimensionColumns.value.length > 0) {
          pivotDimension.value = pivotDimensionColumns.value[0].columnKey;
          console.log("Auto-set pivotDimension to:", pivotDimension.value);
        } else {
          pivotError.value = "当前没有可透视的分析列";
          console.log("Error: no dimension columns");
          return;
        }
      }
      pivotError.value = "";
      showPivotResult.value = true;
      await Vue.nextTick();
      try {
        console.log("Calling queryPivot with dimension:", pivotDimension.value);
        const dist = await queryPivot("distribution", pivotDimension.value, "month");
        console.log("queryPivot result:", dist);
        const id = "dist-" + Date.now();
        const resultItem = {
          id,
          data: dist.data || [],
          dimensionKey: pivotDimension.value,
          dimensionName: columnKeyToName(pivotDimension.value),
          chartType: "pie"  // 分类维度强制使用饼图
        };
        console.log("Adding resultItem:", resultItem);
        distPivotResults.value.push(resultItem);
        await Vue.nextTick();
        console.log("Rendering chart with id:", id);
        renderDistributionChart(id, resultItem.data, resultItem.dimensionName, resultItem.chartType, resultItem.dimensionKey);
        console.log("Chart rendered successfully");
        // Switch to category tab
        pivotActiveTab.value = "category";
      } catch (err) {
        console.error("runDimensionPivot error:", err);
        pivotError.value = "透视分析失败: " + (err && err.message ? err.message : "未知错误");
      }
    };

    // 下钻分析
    const runDrillPivot = async () => {
      if (!drillTarget.value || !drillColumn.value) {
        return;
      }
      drillDialogVisible.value = false;
      
      // 更新下钻路径
      pivotDrillPath.value.push({
        dimensionKey: drillTarget.value.dimensionKey,
        dimensionName: drillTarget.value.dimensionName,
        value: drillTarget.value.value
      });
      
      // 构建selectedPath
      const selectedPath = {};
      for (const step of pivotDrillPath.value) {
        selectedPath[step.dimensionKey] = step.value;
      }
      
      try {
        const startDate = pivotDateRange.value[0];
        const endDate = pivotDateRange.value[1];
        const res = await api("/pivot/query", {
          method: "POST",
          body: JSON.stringify({
            mode: "distribution",
            startDate,
            endDate,
            granularity: "month",
            dimension: drillColumn.value,
            topLevelOnly: false,
            selectedPath
          })
        });
        const dist = await res.json();
        const id = "dist-" + Date.now();
        const resultItem = {
          id,
          data: dist.data || [],
          dimensionKey: drillColumn.value,
          dimensionName: columnKeyToName(drillColumn.value),
          chartType: "pie"  // 分类维度强制使用饼图
        };
        distPivotResults.value.push(resultItem);
        await Vue.nextTick();
        renderDistributionChart(id, resultItem.data, resultItem.dimensionName, resultItem.chartType, resultItem.dimensionKey);
      } catch (err) {
        pivotError.value = "下钻分析失败: " + (err && err.message ? err.message : "未知错误");
      }
    };

    // 清除下钻路径
    const clearDrillPath = () => {
      pivotDrillPath.value = [];
    };

    // 删除单个透视图表
    const removeDistChart = (index) => {
      const result = distPivotResults.value[index];
      if (result && distCharts[result.id]) {
        distCharts[result.id].dispose();
        delete distCharts[result.id];
      }
      distPivotResults.value.splice(index, 1);
      // 如果删除的不是第一个，需要更新下钻路径
      if (index > 0 && pivotDrillPath.value.length >= index) {
        pivotDrillPath.value = pivotDrillPath.value.slice(0, index);
      }
    };

    // 刷新所有图表（保持当前TAB和日期范围）
    const refreshAllPivot = async () => {
      if (!pivotDateRange.value || pivotDateRange.value.length !== 2) {
        pivotError.value = "请先选择数据分析的日期范围";
        return;
      }
      pivotError.value = "";
      
      // 重新执行分析，不切换TAB
      const currentTab = pivotActiveTab.value;
      await runPivotAnalysisInternal();
      pivotActiveTab.value = currentTab;
    };

    // 内部执行分析（不改变TAB）
    const runPivotAnalysisInternal = async () => {
      // 销毁旧图表
      if (trendMonthChart) {
        trendMonthChart.dispose();
        trendMonthChart = null;
      }
      if (trendWeekChart) {
        trendWeekChart.dispose();
        trendWeekChart = null;
      }
      if (trendDayChart) {
        trendDayChart.dispose();
        trendDayChart = null;
      }
      if (personnelOpsChart) {
        personnelOpsChart.dispose();
        personnelOpsChart = null;
      }
      if (personnelDevChart) {
        personnelDevChart.dispose();
        personnelDevChart = null;
      }
      if (personnelTransferChart) {
        personnelTransferChart.dispose();
        personnelTransferChart = null;
      }
      
      // 清空分类维度图表
      distPivotResults.value.forEach((result) => {
        if (distCharts[result.id]) {
          distCharts[result.id].dispose();
          delete distCharts[result.id];
        }
      });
      distPivotResults.value = [];
      
      // 时间维度数据
      const trendByMonth = await queryPivot("trend", "", "month");
      const trendByWeek = await queryPivot("trend", "", "week");
      const trendByDay = await queryPivot("trend", "", "day");
      
      // 人员维度数据
      const startDate = pivotDateRange.value[0];
      const endDate = pivotDateRange.value[1];
const personnelOpsData = await queryPersonnelPivot("运维人员分析人", startDate, endDate);
        const personnelDevData = await queryPersonnelPivot("开发人员分析人", startDate, endDate);
      const personnelTransferData = await queryPersonnelTransferPivot(startDate, endDate);
      
      showPivotResult.value = true;
      await Vue.nextTick();
      
      // 渲染时间维度图表
      renderTrendChart("trend-chart-month", trendByMonth.data || [], "按月工单数");
      renderTrendChart("trend-chart-week", trendByWeek.data || [], "按周工单数");
      renderTrendChart("trend-chart-day", trendByDay.data || [], "按日工单数");
      
      // 渲染人员维度图表
      renderPersonnelChart("personnel-ops-chart", personnelOpsData, "运维人员处理工单数");
      renderPersonnelChart("personnel-dev-chart", personnelDevData, "开发人员处理工单数");
      renderPersonnelChart("personnel-transfer-chart", personnelTransferData, "运维人员透传工单数");
      
      if (distPivotResults.value.length === 0) {
        await runDimensionPivot();
      }
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
      const v = row["创建日期"];
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
        pivotDimensionColumns.value = analysisColumns.value
          .filter((c) => parseBoolean(c.is_top_level))
          .map((c) => ({
            columnKey: c.column_key,
            columnName: c.column_name,
            isTopLevel: true
          }));
        pivotCascadeColumns.value = analysisColumns.value
          .filter((c) => !parseBoolean(c.is_top_level))
          .map((c) => ({
            columnKey: c.column_key,
            columnName: c.column_name,
            isTopLevel: false
          }));
        if (!pivotDimension.value && pivotDimensionColumns.value.length > 0) {
          pivotDimension.value = pivotDimensionColumns.value[0].columnKey;
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
      pivotMaxDate,
      pivotDimension,
      pivotDimensionColumns,
      pivotCascadeColumns,
      pivotActiveTab,
      showPivotResult,
      pivotError,
      clearPivotError,
      runPivotAnalysis,
      onPivotTabChange,
      refreshTimeDimension,
      refreshCategoryDimension,
      refreshPersonnelDimension,
      runDimensionPivot,
      distPivotResults,
      pivotDrillPath,
      drillDialogVisible,
      drillColumn,
      drillTarget,
      runDrillPivot,
      clearDrillPath,
      removeDistChart,
      persistedYwKeyword,
      persistedRows,
      persistedPage,
      pagedPersistedRows,
      searchPersistedTickets,
      onPersistedPageChange,
      ticketFieldColumns
    };
  }
}).use(ElementPlus, {
  locale: ElementPlusLocaleZhCn
}).mount("#app");
