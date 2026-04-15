# -*- coding: utf-8 -*-
"""向数据库直接插入100条多样化工单数据和分析结果，用于测试数据分析图表功能"""
import random
import sys
from datetime import datetime, timedelta

try:
    import psycopg2
except ImportError:
    print("请先安装: pip install psycopg2-binary")
    sys.exit(1)

# 数据库连接配置（根据你的 application.yml）
DB_CONFIG = {
    "host": "localhost",
    "port": 8000,
    "database": "wangzhijun",
    "user": "wangzhijun",
    "password": "Gauss@123#"
}

# 多样化的问题场景
PROBLEM_SCENARIOS = [
    ("连接超时", "客户端连接GaussDB时频繁超时", "内核问题", "慢", "是"),
    ("查询性能下降", "复杂SQL查询响应时间从秒级变为分钟级", "内核问题", "慢", "是"),
    ("内存占用异常", "数据库进程内存占用持续增长", "内核问题", "满", "是"),
    ("死锁频繁", "业务高峰期频繁出现死锁告警", "内核问题", "夯", "是"),
    ("节点宕机", "主节点突然宕机，备节点未自动接管", "内核问题", "宕", "是"),
    ("数据校验错误", "数据备份恢复后出现校验失败", "内核问题", "错", "是"),
    ("参数配置咨询", "咨询GaussDB最佳参数配置方案", "咨询问题", None, "否"),
    ("版本升级咨询", "咨询从V2升级到V3的兼容性问题", "咨询问题", None, "否"),
    ("架构设计咨询", "咨询分布式部署架构设计", "咨询问题", None, "否"),
    ("连接池配置", "应用侧连接池参数配置不当导致连接泄漏", "管控问题", "满", "是"),
    ("监控告警误报", "监控阈值设置不当导致频繁误报", "管控问题", None, "否"),
    ("备份策略异常", "增量备份失败，全量备份占用空间过大", "管控问题", "满", "是"),
    ("权限配置错误", "用户权限配置错误导致业务无法访问表", "管控问题", "错", "是"),
    ("主备同步延迟", "主备节点同步延迟超过阈值告警", "内核问题", "慢", "是"),
    ("SQL语法兼容", "Oracle迁移SQL语法不兼容问题", "内核问题", "错", "是"),
    ("表空间不足", "表空间使用率超过90%告警", "内核问题", "满", "是"),
    ("日志文件过大", "WAL日志文件积压未及时清理", "内核问题", "满", "是"),
    ("并发连接数超限", "max_connections设置过低导致连接拒绝", "内核问题", "满", "是"),
    ("索引失效", "统计信息未更新导致索引选择错误", "内核问题", "慢", "是"),
    ("事务超时", "长事务未及时提交导致锁等待", "内核问题", "夯", "是"),
]

PHASES = [
    "问题审核", "运维人员分析", "开发人员分析", 
    "开发人员闭环", "运维人员闭环", "问题关闭"
]

SITES = [
    "北京局", "上海局", "深圳局", "杭州局", 
    "成都局", "广州局", "武汉局", "南京局",
    "西安局", "重庆局"
]

HANDLERS = [
    "张三", "李四", "王五", "赵六", "钱七", "孙八",
    "周九", "吴十", "郑十一", "王十二"
]

VERSIONS = [
    "V1.0.0", "V1.1.0", "V1.2.0", "V2.0.0", "V2.1.0", "V2.2.0",
    "V3.0.0", "V3.1.0", "V3.2.0", "V3.3.0"
]

KERNEL_VERSIONS = [
    "K501.0", "K502.0", "K503.0", "K504.0", "K505.0",
    "K506.0", "K507.0", "K508.0", "K509.0", "K510.0"
]

def generate_yw_no(index):
    """生成工单号"""
    return f"YW202504{index:04d}"

def random_date(start_year=2024, months_range=15):
    """生成随机日期"""
    start = datetime(start_year, 1, 1)
    end = start + timedelta(days=months_range * 30)
    delta = end - start
    random_days = random.randint(0, delta.days)
    return start + timedelta(days=random_days)

def main():
    print("正在连接数据库...")
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        print("数据库连接成功")
    except Exception as e:
        print(f"数据库连接失败: {e}")
        sys.exit(1)

    random.seed(42)  # 固定随机种子，保证数据可重现
    
    # 清理已有的测试数据（可选）
    print("清理旧数据...")
    cur.execute("DELETE FROM ticket_analysis_result WHERE yw_no LIKE 'YW202504%'")
    cur.execute("DELETE FROM ticket WHERE yw_no LIKE 'YW202504%'")
    
    # 插入100条工单数据
    print("插入工单数据...")
    ticket_data = []
    
    for i in range(1, 101):
        yw_no = generate_yw_no(i)
        start_date = random_date(2024, 12)
        phase = random.choice(PHASES)
        site = random.choice(SITES)
        handler = random.choice(HANDLERS)
        version = random.choice(VERSIONS)
        kernel_version = random.choice(KERNEL_VERSIONS)
        
        # 选择一个多样化的问题场景
        scenario_idx = i % len(PROBLEM_SCENARIOS)
        scenario = PROBLEM_SCENARIOS[scenario_idx]
        problem_type = scenario[0]
        problem_desc = f"{scenario[1]}，工单号{yw_no}"
        
        progress = f"已进行{random.choice(['初步排查', '深入分析', '制定方案', '方案实施', '验证效果'])}"
        root_cause = f"根因分析：{scenario[1]}。{random.choice(['需优化参数配置', '需升级内核版本', '需调整业务逻辑', '需扩容资源', '需完善监控'])}"
        reply = f"处理结果：{random.choice(['问题已解决', '正在持续观察', '建议优化配置', '已提交升级计划', '已通知业务侧调整'])}"
        
        ticket_data.append((
            yw_no,
            phase,
            "一般",
            site,
            "inst001",
            "核心业务",
            handler,
            problem_desc,
            "2小时",
            "4小时",
            handler,
            handler,
            handler,
            handler,
            handler,
            handler,
            progress,
            start_date,
            "2小时",
            "2小时",
            "2小时",
            "2小时",
            "2小时",
            "2小时",
            version,
            reply,
            "1小时",
            "否",
            "否",
            "否",
            root_cause,
            f"DTS{i:06d}",
            "临时规避",
            "是",
            "自动恢复",
            "否",
            "否",
            "张三,李四",
            kernel_version,
            "HCS 8.0.1",
            "HCS",
            problem_type,
            "代码缺陷",
            "集中式"
        ))
    
    # 批量插入工单
    insert_ticket_sql = """
        INSERT INTO ticket (yw_no, "当前阶段", "问题严重性", "局点", "实例简称", "业务名称", "当前处理人", 
                           "描述", "滞留时间", "SLA时间", "问题审核人", "运维人员分析人", "开发人员分析人", 
                           "开发人员闭环人", "运维人员闭环人", "问题审核关闭人", "进展概述", "创建日期", 
                           "问题审核总时长", "运维人员分析总时长", "开发人员分析总时长", "开发人员闭环总时长", 
                           "运维人员闭环总时长", "问题审核关闭总时长", "管控版本", "对外答复", "故障到恢复用时", 
                           "是否涉及故障恢复", "磐石版本是否涉及", "是否需要预警", "问题根因", "DTS单号", 
                           "规避措施", "是否质量问题", "恢复方法", "是否咨询问题", "是否涉及内核升级", 
                           "协同处理人", "高斯版本", "HCS版本号", "HCS/轻量化", "问题类型", "根因分类", 
                           "部署形态", updated_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, 
                %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW())
        ON CONFLICT (yw_no) DO UPDATE SET
            "当前阶段" = EXCLUDED."当前阶段",
            "问题严重性" = EXCLUDED."问题严重性",
            "局点" = EXCLUDED."局点",
            "实例简称" = EXCLUDED."实例简称",
            "业务名称" = EXCLUDED."业务名称",
            "当前处理人" = EXCLUDED."当前处理人",
            "描述" = EXCLUDED."描述",
            "滞留时间" = EXCLUDED."滞留时间",
            "SLA时间" = EXCLUDED."SLA时间",
            "问题审核人" = EXCLUDED."问题审核人",
            "运维人员分析人" = EXCLUDED."运维人员分析人",
            "开发人员分析人" = EXCLUDED."开发人员分析人",
            "开发人员闭环人" = EXCLUDED."开发人员闭环人",
            "运维人员闭环人" = EXCLUDED."运维人员闭环人",
            "问题审核关闭人" = EXCLUDED."问题审核关闭人",
            "进展概述" = EXCLUDED."进展概述",
            "创建日期" = EXCLUDED."创建日期",
            "问题审核总时长" = EXCLUDED."问题审核总时长",
            "运维人员分析总时长" = EXCLUDED."运维人员分析总时长",
            "开发人员分析总时长" = EXCLUDED."开发人员分析总时长",
            "开发人员闭环总时长" = EXCLUDED."开发人员闭环总时长",
            "运维人员闭环总时长" = EXCLUDED."运维人员闭环总时长",
            "问题审核关闭总时长" = EXCLUDED."问题审核关闭总时长",
            "管控版本" = EXCLUDED."管控版本",
            "对外答复" = EXCLUDED."对外答复",
            "故障到恢复用时" = EXCLUDED."故障到恢复用时",
            "是否涉及故障恢复" = EXCLUDED."是否涉及故障恢复",
            "磐石版本是否涉及" = EXCLUDED."磐石版本是否涉及",
            "是否需要预警" = EXCLUDED."是否需要预警",
            "问题根因" = EXCLUDED."问题根因",
            "DTS单号" = EXCLUDED."DTS单号",
            "规避措施" = EXCLUDED."规避措施",
            "是否质量问题" = EXCLUDED."是否质量问题",
            "恢复方法" = EXCLUDED."恢复方法",
            "是否咨询问题" = EXCLUDED."是否咨询问题",
            "是否涉及内核升级" = EXCLUDED."是否涉及内核升级",
            "协同处理人" = EXCLUDED."协同处理人",
            "高斯版本" = EXCLUDED."高斯版本",
            "HCS版本号" = EXCLUDED."HCS版本号",
            "HCS/轻量化" = EXCLUDED."HCS/轻量化",
            "问题类型" = EXCLUDED."问题类型",
            "根因分类" = EXCLUDED."根因分类",
            "部署形态" = EXCLUDED."部署形态",
            updated_at = NOW()
    """
    
    cur.executemany(insert_ticket_sql, ticket_data)
    print(f"已插入 {len(ticket_data)} 条工单数据")
    
    # 插入分析结果数据
    print("插入分析结果数据...")
    analysis_data = []
    
    for i in range(1, 101):
        yw_no = generate_yw_no(i)
        scenario_idx = i % len(PROBLEM_SCENARIOS)
        scenario = PROBLEM_SCENARIOS[scenario_idx]
        
        product_category = scenario[2]  # 管控问题/内核问题/咨询问题
        is_quality_issue = scenario[4]  # 是/否
        level1_category = scenario[3]   # 慢/满/夯/宕/错
        
        analysis_data.append((yw_no, product_category, is_quality_issue, level1_category))
    
    # 批量插入分析结果
    insert_analysis_sql = """
        INSERT INTO ticket_analysis_result (yw_no, product_category, is_quality_issue, 
                                           level1_category, updated_at)
        VALUES (%s, %s, %s, %s, NOW())
        ON CONFLICT (yw_no) DO UPDATE SET
            product_category = EXCLUDED.product_category,
            is_quality_issue = EXCLUDED.is_quality_issue,
            level1_category = EXCLUDED.level1_category,
            updated_at = NOW()
    """
    
    cur.executemany(insert_analysis_sql, analysis_data)
    print(f"已插入 {len(analysis_data)} 条分析结果数据")
    
    # 提交并关闭连接
    conn.commit()
    cur.close()
    conn.close()
    
    print("\n数据统计：")
    print(f"- 工单总数: 100")
    print(f"- 问题类型分布:")
    print(f"  - 内核问题: 约60条（涵盖慢/满/夯/宕/错五种一级分类）")
    print(f"  - 管控问题: 约20条")
    print(f"  - 咨询问题: 约20条")
    print(f"- 时间范围: 2024年1月 - 2025年3月")
    print(f"- 局点分布: 10个局点")
    print(f"- 处理人分布: 10位处理人")
    print("\n数据已成功插入，现在可以在数据分析页面测试图表功能！")

if __name__ == "__main__":
    main()