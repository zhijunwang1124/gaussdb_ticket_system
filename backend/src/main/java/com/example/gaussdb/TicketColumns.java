package com.example.gaussdb;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 工单表业务字段（与 Excel 表头、数据库列名一致，便于透视与导入）。
 */
public final class TicketColumns {

    public static final List<String> DATA_COLUMNS = Collections.unmodifiableList(Arrays.asList(
            "起始日期",
            "当前阶段",
            "局点",
            "当前处理人",
            "问题描述",
            "进展概述",
            "管控版本",
            "内核版本",
            "问题根因",
            "对外答复"));

    private TicketColumns() {
    }

    public static String quote(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
