# -*- coding: utf-8 -*-
"""构造 500 条多样化 GaussDB 工单 + 分析结果测试数据。"""
import random
import sys
from datetime import datetime, timedelta

try:
    import psycopg2
except ImportError:
    print("请先安装依赖: python -m pip install psycopg2-binary")
    sys.exit(1)

DB_CONFIG = {
    "host": "localhost",
    "port": 8000,
    "database": "wangzhijun",
    "user": "wangzhijun",
    "password": "Gauss@123#"
}

PHASES = ["问题审核", "运维人员分析", "开发人员分析", "开发人员闭环", "运维人员闭环", "问题关闭"]
SITES = ["北京局", "上海局", "深圳局", "杭州局", "成都局", "广州局", "武汉局", "南京局", "西安局", "重庆局", "苏州局", "青岛局"]
HANDLERS = ["张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十", "郑十一", "王十二", "冯十三", "陈十四"]
VERSIONS = ["V2.1.0", "V2.1.1", "V2.2.0", "V2.3.0", "V3.0.0", "V3.1.0", "V3.1.2", "V3.2.0", "V3.2.4", "V3.3.0"]
KERNELS = ["K505.1", "K505.2", "K506.0", "K506.3", "K507.1", "K507.4", "K508.0", "K508.2", "K509.0", "K510.1"]

SCENARIOS = [
    ("连接管理", "连接池耗尽导致业务连接超时", "连接池参数偏小且连接泄漏", "内核问题", "是", "满"),
    ("SQL执行", "复杂SQL出现大范围排序溢写", "work_mem配置不足且SQL未命中索引", "内核问题", "是", "慢"),
    ("事务并发", "高峰期频繁出现死锁回滚", "事务粒度过粗与锁顺序不一致", "内核问题", "是", "夯"),
    ("主备复制", "主备延迟持续上升", "网络抖动叠加大事务提交", "内核问题", "是", "慢"),
    ("高可用", "故障切换后VIP漂移失败", "自动切换脚本超时未重试", "管控问题", "是", "宕"),
    ("存储管理", "表空间告警持续触发", "历史归档清理策略缺失", "管控问题", "否", "满"),
    ("参数治理", "checkpoint周期不合理导致抖动", "参数变更未按场景分级验证", "管控问题", "否", "慢"),
    ("监控告警", "慢SQL告警漏报", "采样阈值配置偏高", "管控问题", "否", "错"),
    ("迁移兼容", "Oracle函数迁移后语义偏差", "兼容函数映射规则缺失", "内核问题", "是", "错"),
    ("备份恢复", "恢复校验失败", "备份链不完整且缺增量校验", "管控问题", "是", "错"),
    ("权限安全", "业务账号误授权写权限", "权限模板配置不严格", "管控问题", "是", "错"),
    ("资源调度", "CPU争抢导致延迟飙升", "并发度与资源队列不匹配", "内核问题", "是", "慢"),
    ("分区表", "冷热分区裁剪失效", "统计信息陈旧", "内核问题", "是", "慢"),
    ("索引维护", "BTree膨胀导致查询回表增加", "长期未REINDEX", "管控问题", "否", "慢"),
    ("WAL日志", "WAL积压导致磁盘快速增长", "归档通道阻塞", "管控问题", "是", "满"),
    ("网络链路", "短时网络抖动触发连接重建风暴", "连接重试退避策略缺失", "内核问题", "是", "夯"),
    ("咨询答疑", "咨询分布式表选型策略", "需结合业务访问模式设计", "咨询问题", "否", None),
    ("咨询答疑", "咨询资源隔离最佳实践", "需评估队列与租户策略", "咨询问题", "否", None),
    ("咨询答疑", "咨询版本升级窗口与回滚方案", "需制定演练流程", "咨询问题", "否", None),
    ("审计合规", "审计日志保留策略不达标", "缺少日志分级归档机制", "管控问题", "否", "错")
]

FOLLOW_UPS = ["已定位影响范围并形成处理建议", "已完成参数回滚与灰度验证", "已协调现场复测并收集性能指标", "已提交补丁并进入观察期", "已输出RCA并制定防复发措施"]
REPLIES = ["问题已缓解，建议持续观察48小时", "已恢复业务，建议后续优化配置", "风险已解除，建议纳入标准巡检项", "已给出整改方案并完成确认", "已完成处理，建议安排复盘评审"]


def make_yw_no(i):
    return "YW2026%06d" % i


def random_date():
    start = datetime(2024, 1, 1)
    end = datetime(2026, 4, 1)
    d = random.randint(0, (end - start).days)
    return start + timedelta(days=d, hours=random.randint(0, 23), minutes=random.randint(0, 59))


def main():
    random.seed(20260414)
    conn = psycopg2.connect(**DB_CONFIG)
    cur = conn.cursor()

    cur.execute("ALTER TABLE ticket_analysis_result ADD COLUMN IF NOT EXISTS product_category TEXT")
    cur.execute("ALTER TABLE ticket_analysis_result ADD COLUMN IF NOT EXISTS is_quality_issue TEXT")
    cur.execute("ALTER TABLE ticket_analysis_result ADD COLUMN IF NOT EXISTS level1_category TEXT")

    cur.execute("DELETE FROM ticket_analysis_result WHERE yw_no LIKE 'YW2026%'")
    cur.execute("DELETE FROM ticket WHERE yw_no LIKE 'YW2026%'")

    tickets = []
    analysis = []
    for i in range(1, 501):
        yw_no = make_yw_no(i)
        t = random_date()
        phase = random.choice(PHASES)
        site = random.choice(SITES)
        handler = random.choice(HANDLERS)
        ver = random.choice(VERSIONS)
        kernel = random.choice(KERNELS)
        domain, problem, root, category, quality, lvl1 = SCENARIOS[i % len(SCENARIOS)]
        title = "%s-%s（%s）" % (domain, problem, yw_no)
        progress = random.choice(FOLLOW_UPS)
        reply = random.choice(REPLIES)
        desc = "【%s】%s；现场表现：%s。" % (domain, problem, random.choice(["延迟波动", "连接中断", "告警频发", "吞吐下降", "偶发报错"]))
        root_text = "根因：%s；建议：%s。" % (root, random.choice(["优化参数", "升级补丁", "完善监控", "调整SQL", "扩容资源"]))

        tickets.append((
            yw_no, t, phase, site, handler, title + " " + desc, progress, ver, kernel, root_text, reply
        ))
        analysis.append((yw_no, category, quality, lvl1))

    cur.executemany(
        """
        INSERT INTO ticket(yw_no, "起始日期", "当前阶段", "局点", "当前处理人", "问题描述", "进展概述", "管控版本", "内核版本", "问题根因", "对外答复", updated_at)
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,NOW())
        ON CONFLICT (yw_no) DO UPDATE SET
            "起始日期"=EXCLUDED."起始日期",
            "当前阶段"=EXCLUDED."当前阶段",
            "局点"=EXCLUDED."局点",
            "当前处理人"=EXCLUDED."当前处理人",
            "问题描述"=EXCLUDED."问题描述",
            "进展概述"=EXCLUDED."进展概述",
            "管控版本"=EXCLUDED."管控版本",
            "内核版本"=EXCLUDED."内核版本",
            "问题根因"=EXCLUDED."问题根因",
            "对外答复"=EXCLUDED."对外答复",
            updated_at=NOW()
        """,
        tickets
    )

    cur.executemany(
        """
        INSERT INTO ticket_analysis_result(yw_no, product_category, is_quality_issue, level1_category, updated_at)
        VALUES (%s,%s,%s,%s,NOW())
        ON CONFLICT (yw_no) DO UPDATE SET
            product_category=EXCLUDED.product_category,
            is_quality_issue=EXCLUDED.is_quality_issue,
            level1_category=EXCLUDED.level1_category,
            updated_at=NOW()
        """,
        analysis
    )

    conn.commit()

    cur.execute("SELECT product_category, COUNT(*) FROM ticket_analysis_result WHERE yw_no LIKE 'YW2026%' GROUP BY product_category ORDER BY 2 DESC")
    cat_stats = cur.fetchall()
    cur.execute("SELECT is_quality_issue, COUNT(*) FROM ticket_analysis_result WHERE yw_no LIKE 'YW2026%' GROUP BY is_quality_issue ORDER BY 2 DESC")
    quality_stats = cur.fetchall()
    cur.execute("SELECT level1_category, COUNT(*) FROM ticket_analysis_result WHERE yw_no LIKE 'YW2026%' AND level1_category IS NOT NULL GROUP BY level1_category ORDER BY 2 DESC")
    level_stats = cur.fetchall()

    print("已生成并写入 500 条工单与分析结果数据（YW2026xxxxxx）")
    print("产品分类分布:", cat_stats)
    print("是否质量问题分布:", quality_stats)
    print("一级分类分布:", level_stats)

    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
