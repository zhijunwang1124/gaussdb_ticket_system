DROP TABLE IF EXISTS analyze_task_item;
DROP TABLE IF EXISTS analyze_task;
DROP TABLE IF EXISTS ticket_analysis_snapshot;
DROP TABLE IF EXISTS ticket_analysis_result;
DROP TABLE IF EXISTS analysis_column_config;
DROP TABLE IF EXISTS ticket;

CREATE TABLE ticket (
    id BIGSERIAL PRIMARY KEY,
    yw_no VARCHAR(64) NOT NULL UNIQUE,
    "当前阶段" VARCHAR(128),
    "问题严重性" VARCHAR(128),
    "局点" VARCHAR(256),
    "实例简称" VARCHAR(128),
    "业务名称" VARCHAR(256),
    "当前处理人" VARCHAR(128),
    "描述" TEXT,
    "滞留时间" VARCHAR(64),
    "SLA时间" VARCHAR(64),
    "问题审核人" VARCHAR(128),
    "运维人员分析人" VARCHAR(128),
    "开发人员分析人" VARCHAR(128),
    "开发人员闭环人" VARCHAR(128),
    "运维人员闭环人" VARCHAR(128),
    "问题审核关闭人" VARCHAR(128),
    "进展概述" TEXT,
    "创建日期" TIMESTAMP,
    "问题审核总时长" VARCHAR(64),
    "运维人员分析总时长" VARCHAR(64),
    "开发人员分析总时长" VARCHAR(64),
    "开发人员闭环总时长" VARCHAR(64),
    "运维人员闭环总时长" VARCHAR(64),
    "问题审核关闭总时长" VARCHAR(64),
    "管控版本" VARCHAR(128),
    "对外答复" TEXT,
    "故障到恢复用时" VARCHAR(64),
    "是否涉及故障恢复" VARCHAR(32),
    "磐石版本是否涉及" VARCHAR(32),
    "是否需要预警" VARCHAR(32),
    "问题根因" TEXT,
    "DTS单号" VARCHAR(64),
    "规避措施" TEXT,
    "是否质量问题" VARCHAR(32),
    "恢复方法" TEXT,
    "是否咨询问题" VARCHAR(32),
    "是否涉及内核升级" VARCHAR(32),
    "协同处理人" VARCHAR(256),
    "高斯版本" VARCHAR(128),
    "HCS版本号" VARCHAR(128),
    "HCS/轻量化" VARCHAR(32),
    "问题类型" VARCHAR(128),
    "根因分类" VARCHAR(128),
    "部署形态" VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE analysis_column_config (
    id BIGSERIAL PRIMARY KEY,
    column_key VARCHAR(64) NOT NULL UNIQUE,
    column_name VARCHAR(128) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    allowed_values JSONB NOT NULL,
    is_top_level BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ticket_analysis_result (
    yw_no VARCHAR(64) PRIMARY KEY REFERENCES ticket(yw_no) ON DELETE CASCADE,
    product_category TEXT,
    is_quality_issue TEXT,
    level1_category TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ticket_analysis_snapshot (
    id BIGSERIAL PRIMARY KEY,
    yw_no VARCHAR(64) NOT NULL UNIQUE,
    analysis_result_json JSONB NOT NULL,
    analyzed_at TIMESTAMP NOT NULL,
    model_name VARCHAR(128),
    task_id BIGINT
);

CREATE TABLE analyze_task (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE analyze_task_item (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    yw_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO analysis_column_config (column_key, column_name, description, allowed_values, is_top_level)
VALUES
('product_category', '产品分类', '根据工单内容判断产品类别', '["管控问题","内核问题","咨询问题"]', TRUE),
('is_quality_issue', '是否质量问题', '判断是否属于质量问题', '["是","否"]', TRUE),
('level1_category', '一级分类', '根据故障性质划分一级分类', '["慢","满","夯","宕","错"]', FALSE);

WITH seed AS (
    SELECT
        g AS i,
        ('YW2026' || lpad(g::text, 6, '0')) AS yw_no,
        (TIMESTAMP '2024-01-01' + ((g * 17) % 820) * INTERVAL '1 day' + ((g * 13) % 24) * INTERVAL '1 hour') AS start_dt,
        (ARRAY['问题审核','运维人员分析','开发人员分析','开发人员闭环','运维人员闭环','问题关闭'])[((g % 6) + 1)] AS phase_name,
        (ARRAY['严重','较严重','一般','轻微'])[((g % 4) + 1)] AS severity,
        (ARRAY['北京局','上海局','深圳局','杭州局','成都局','广州局','武汉局','南京局','西安局','重庆局','苏州局','青岛局'])[((g % 12) + 1)] AS site_name,
        (ARRAY['inst001','inst002','inst003','inst004','inst005','inst006'])[((g % 6) + 1)] AS instance_name,
        (ARRAY['核心业务','交易系统','报表系统','网关服务','监控系统'])[((g % 5) + 1)] AS business_name,
        (ARRAY['张三','李四','王五','赵六','钱七','孙八','周九','吴十','郑十一','王十二','冯十三','陈十四'])[((g % 12) + 1)] AS handler_name,
        (ARRAY['张三','李四','王五','赵六','钱七','孙八','周九','吴十','郑十一','王十二','冯十三','陈十四'])[((g % 12) + 1)] AS reviewer,
        (ARRAY['张三','李四','王五','赵六','钱七','孙八','周九','吴十','郑十一','王十二','冯十三','陈十四'])[((g % 12) + 1)] AS ops_analyzer,
        (ARRAY['张三','李四','王五','赵六','钱七','孙八','周九','吴十','郑十一','王十二','冯十三','陈十四'])[((g % 12) + 1)] AS dev_analyzer,
        (ARRAY['张三','李四','王五','赵六','钱七','孙八','周九','吴十','郑十一','王十二','冯十三','陈十四'])[((g % 12) + 1)] AS dev_closer,
        (ARRAY['张三','李四','王五','赵六','钱七','孙八','周九','吴十','郑十一','王十二','冯十三','陈十四'])[((g % 12) + 1)] AS ops_closer,
        (ARRAY['张三','李四','王五','赵六','钱七','孙八','周九','吴十','郑十一','王十二','冯十三','陈十四'])[((g % 12) + 1)] AS closer,
        (ARRAY['2小时','4小时','8小时','16小时','24小时','48小时','72小时'])[((g % 7) + 1)] AS retention_time,
        (ARRAY['4小时','8小时','24小时','48小时','72小时'])[((g % 5) + 1)] AS sla_time,
        (ARRAY['30分钟','1小时','2小时','3小时','4小时','5小时','6小时'])[((g % 7) + 1)] AS total_duration,
        (ARRAY['V2.1.0','V2.1.1','V2.2.0','V2.3.0','V3.0.0','V3.1.0','V3.1.2','V3.2.0','V3.2.4','V3.3.0'])[((g % 10) + 1)] AS ctrl_ver,
        (ARRAY['K505.1','K505.2','K506.0','K506.3','K507.1','K507.4','K508.0','K508.2','K509.0','K510.1'])[((g % 10) + 1)] AS kernel_ver,
        (ARRAY['是','否'])[((g % 2) + 1)] AS is_incident,
        (ARRAY['是','否'])[((g % 2) + 1)] AS is_panshi,
        (ARRAY['是','否'])[((g % 2) + 1)] AS need_warning,
        (ARRAY['DTS000001','DTS000002','DTS000003','DTS000004','DTS000005'])[((g % 5) + 1)] AS dts_no,
        (ARRAY['是','否'])[((g % 2) + 1)] AS is_quality,
        (ARRAY['是','否'])[((g % 2) + 1)] AS is_consult,
        (ARRAY['是','否'])[((g % 2) + 1)] AS kernel_upgrade,
        (ARRAY['HCS 8.0.1','HCS 8.0.2','HCS 8.0.3','HCS 8.1.0'])[((g % 4) + 1)] AS hcs_ver,
        (ARRAY['HCS','轻量化'])[((g % 2) + 1)] AS hcs_type,
        (ARRAY['性能问题','功能缺陷','配置问题','网络问题','资源问题'])[((g % 5) + 1)] AS problem_type,
        (ARRAY['代码缺陷','配置错误','资源不足','网络故障','第三方问题'])[((g % 5) + 1)] AS root_cause_category,
        (ARRAY['集中式','分布式'])[((g % 2) + 1)] AS deploy_type,
        (ARRAY[
            '连接池耗尽导致业务连接超时',
            '复杂SQL出现大范围排序溢写',
            '高峰期频繁出现死锁回滚',
            '主备延迟持续上升',
            '故障切换后VIP漂移失败',
            '表空间告警持续触发',
            'checkpoint周期不合理导致抖动',
            '慢SQL告警漏报',
            'Oracle函数迁移后语义偏差',
            '恢复校验失败',
            '业务账号误授权写权限',
            'CPU争抢导致延迟飙升',
            '冷热分区裁剪失效',
            '索引膨胀导致查询回表增加',
            'WAL积压导致磁盘快速增长',
            '网络抖动触发连接重建风暴',
            '咨询分布式表选型策略',
            '咨询资源隔离最佳实践',
            '咨询版本升级窗口与回滚方案',
            '审计日志保留策略不达标'
        ])[((g % 20) + 1)] AS problem_text
    FROM generate_series(1, 500) AS g
),
classified AS (
    SELECT
        s.*,
        CASE
            WHEN s.i % 20 IN (16,17,18) THEN '咨询问题'
            WHEN s.i % 20 IN (4,5,6,7,9,10,13,14,19,0) THEN '管控问题'
            ELSE '内核问题'
        END AS product_category,
        CASE
            WHEN s.i % 20 IN (16,17,18,6,7,13,19) THEN '否'
            ELSE '是'
        END AS is_quality_issue,
        CASE
            WHEN s.i % 20 IN (0,1,3,11,12) THEN '慢'
            WHEN s.i % 20 IN (5,8,14,15) THEN '满'
            WHEN s.i % 20 IN (2,9,10) THEN '错'
            WHEN s.i % 20 IN (4) THEN '宕'
            WHEN s.i % 20 IN (6,7) THEN '夯'
            ELSE NULL
        END AS level1_category
    FROM seed s
)
INSERT INTO ticket (
    yw_no, "当前阶段", "问题严重性", "局点", "实例简称", "业务名称", "当前处理人", "描述", "滞留时间", "SLA时间",
    "问题审核人", "运维人员分析人", "开发人员分析人", "开发人员闭环人", "运维人员闭环人", "问题审核关闭人", "进展概述", "创建日期",
    "问题审核总时长", "运维人员分析总时长", "开发人员分析总时长", "开发人员闭环总时长", "运维人员闭环总时长", "问题审核关闭总时长",
    "管控版本", "对外答复", "故障到恢复用时", "是否涉及故障恢复", "磐石版本是否涉及", "是否需要预警", "问题根因", "DTS单号", "规避措施",
    "是否质量问题", "恢复方法", "是否咨询问题", "是否涉及内核升级", "协同处理人", "高斯版本", "HCS版本号", "HCS/轻量化",
    "问题类型", "根因分类", "部署形态", updated_at
)
SELECT
    c.yw_no,
    c.phase_name,
    c.severity,
    c.site_name,
    c.instance_name,
    c.business_name,
    c.handler_name,
    '【GaussDB】' || c.problem_text || '（工单' || c.yw_no || '）',
    c.retention_time,
    c.sla_time,
    c.reviewer,
    c.ops_analyzer,
    c.dev_analyzer,
    c.dev_closer,
    c.ops_closer,
    c.closer,
    (ARRAY['已完成现场信息收集','已复现并定位范围','已输出临时规避措施','已提交修复方案','已安排变更验证'])[((c.i % 5) + 1)],
    c.start_dt,
    c.total_duration,
    c.total_duration,
    c.total_duration,
    c.total_duration,
    c.total_duration,
    c.total_duration,
    c.ctrl_ver,
    (ARRAY['问题已缓解，建议持续观察48小时','已恢复业务，建议后续优化配置','风险已解除，建议纳入巡检项','已完成处理，请安排复盘'])[((c.i % 4) + 1)],
    (ARRAY['30分钟','1小时','2小时','4小时','8小时'])[((c.i % 5) + 1)],
    c.is_incident,
    c.is_panshi,
    c.need_warning,
    '根因分析：' || c.problem_text || '；建议：' || (ARRAY['优化参数','升级补丁','完善监控','调整SQL','扩容资源'])[((c.i % 5) + 1)],
    c.dts_no,
    (ARRAY['临时规避方案','重启服务','参数调整','扩容资源'])[((c.i % 4) + 1)],
    c.is_quality,
    (ARRAY['自动恢复','人工干预','服务重启','回滚版本'])[((c.i % 4) + 1)],
    c.is_consult,
    c.kernel_upgrade,
    (ARRAY['张三,李四','王五,赵六','钱七,孙八','周九,吴十'])[((c.i % 4) + 1)],
    c.kernel_ver,
    c.hcs_ver,
    c.hcs_type,
    c.problem_type,
    c.root_cause_category,
    c.deploy_type,
    NOW()
FROM classified c;

WITH classified AS (
    SELECT
        ('YW2026' || lpad(g::text, 6, '0')) AS yw_no,
        CASE
            WHEN g % 20 IN (16,17,18) THEN '咨询问题'
            WHEN g % 20 IN (4,5,6,7,9,10,13,14,19,0) THEN '管控问题'
            ELSE '内核问题'
        END AS product_category,
        CASE
            WHEN g % 20 IN (16,17,18,6,7,13,19) THEN '否'
            ELSE '是'
        END AS is_quality_issue,
        CASE
            WHEN g % 20 IN (0,1,3,11,12) THEN '慢'
            WHEN g % 20 IN (5,8,14,15) THEN '满'
            WHEN g % 20 IN (2,9,10) THEN '错'
            WHEN g % 20 IN (4) THEN '宕'
            WHEN g % 20 IN (6,7) THEN '夯'
            ELSE NULL
        END AS level1_category
    FROM generate_series(1, 500) AS g
)
INSERT INTO ticket_analysis_result (yw_no, product_category, is_quality_issue, level1_category, updated_at)
SELECT yw_no, product_category, is_quality_issue, level1_category, NOW()
FROM classified;
