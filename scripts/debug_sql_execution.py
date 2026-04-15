# -*- coding: utf-8 -*-
"""
调试SQL语句，检查列名匹配问题
"""
import sys

try:
    import psycopg2
except ImportError:
    print("请先安装: pip install psycopg2-binary")
    sys.exit(1)

DB_CONFIG = {
    "host": "localhost",
    "port": 8000,
    "database": "wangzhijun",
    "user": "wangzhijun",
    "password": "Gauss@123#"
}

# TicketColumns.DATA_COLUMNS中的字段
EXPECTED_COLUMNS = [
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
]

def build_sql_like_java():
    """模拟Java代码中的SQL构建"""
    sb = "SELECT t.yw_no"
    for cn in EXPECTED_COLUMNS:
        sb += f', t."{cn}"'
    sb += " FROM ticket t WHERE t.yw_no = ?"
    return sb

def quote(name):
    """模拟Java中的quote方法"""
    return '"' + name.replace('"', '""') + '"'

def build_ticket_select_sql_like_java():
    """模拟Java代码中的buildTicketSelectSql方法"""
    sb = "SELECT t.id, t.yw_no, "
    for i in range(len(EXPECTED_COLUMNS)):
        col = EXPECTED_COLUMNS[i]
        sb += f"t.{quote(col)} AS {quote(col)}"
        if i < len(EXPECTED_COLUMNS) - 1:
            sb += ", "
    sb += ", t.updated_at FROM ticket t ORDER BY t.updated_at DESC"
    return sb

def main():
    print("SQL语句调试")
    print("=" * 80)
    
    # 构建SQL语句
    load_data_sql = build_sql_like_java()
    select_sql = build_ticket_select_sql_like_java()
    
    print("\n1. loadTicketData 中的SQL:")
    print("-" * 80)
    print(load_data_sql)
    print(f"\n期望列数: {1 + len(EXPECTED_COLUMNS)} (yw_no + {len(EXPECTED_COLUMNS)}个业务字段)")
    
    print("\n2. buildTicketSelectSql 中的SQL:")
    print("-" * 80)
    print(select_sql)
    print(f"\n期望列数: {2 + len(EXPECTED_COLUMNS) + 1} (id + yw_no + {len(EXPECTED_COLUMNS)}个业务字段 + updated_at)")
    
    # 测试实际执行
    print("\n" + "=" * 80)
    print("测试实际SQL执行:")
    print("=" * 80)
    
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        
        # 测试第一条SQL
        print("\n测试 loadTicketData SQL:")
        test_sql = load_data_sql.replace("?", "'YW202600000001'")
        print(f"执行: {test_sql}")
        try:
            cur.execute(test_sql)
            row = cur.fetchone()
            print(f"成功! 返回列数: {len(cur.description)}")
            print("\n列名列表:")
            for i, desc in enumerate(cur.description, 1):
                print(f"  {i:2d}. {desc[0]}")
        except Exception as e:
            print(f"失败: {e}")
        
        # 测试第二条SQL
        print("\n测试 buildTicketSelectSql SQL:")
        print(f"执行: {select_sql}")
        try:
            cur.execute(select_sql)
            rows = cur.fetchall()
            print(f"成功! 返回列数: {len(cur.description)}")
            print(f"返回行数: {len(rows)}")
            print("\n列名列表:")
            for i, desc in enumerate(cur.description, 1):
                print(f"  {i:2d}. {desc[0]}")
        except Exception as e:
            print(f"失败: {e}")
        
        cur.close()
        conn.close()
        
    except Exception as e:
        print(f"错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()