package com.example.gaussdb;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 工单表业务字段（与 Excel 表头、数据库列名一致，便于透视与导入）。
 */
public final class TicketColumns {

    public static final List<String> DATA_COLUMNS = Collections.unmodifiableList(Arrays.asList(
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
            "部署形态"));

    private TicketColumns() {
    }

    public static String quote(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
