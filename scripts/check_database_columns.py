# -*- coding: utf-8 -*-
"""
检查数据库表的实际列名，并与TicketColumns.DATA_COLUMNS对比
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

def main():
    print("检查数据库列名...")
    print("=" * 80)
    
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        
        # 获取实际列名
        cur.execute("""
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_name = 'ticket' AND column_name NOT IN ('id', 'yw_no', 'updated_at')
            ORDER BY ordinal_position
        """)
        
        actual_columns = [row[0] for row in cur.fetchall()]
        
        print(f"\n预期列数: {len(EXPECTED_COLUMNS)}")
        print(f"实际列数: {len(actual_columns)}")
        
        # 对比差异
        print("\n" + "=" * 80)
        print("字段对比:")
        print("=" * 80)
        
        missing_columns = []
        extra_columns = []
        
        for col in EXPECTED_COLUMNS:
            if col not in actual_columns:
                missing_columns.append(col)
                print(f"[缺失] {col}")
            else:
                print(f"[匹配] {col}")
        
        for col in actual_columns:
            if col not in EXPECTED_COLUMNS:
                extra_columns.append(col)
        
        if extra_columns:
            print("\n" + "=" * 80)
            print("额外的字段（不在预期中）:")
            print("=" * 80)
            for col in extra_columns:
                print(f"[额外] {col}")
        
        # 生成Java格式的列定义
        print("\n" + "=" * 80)
        print("建议的TicketColumns.DATA_COLUMNS定义:")
        print("=" * 80)
        print('public static final List<String> DATA_COLUMNS = Collections.unmodifiableList(Arrays.asList(')
        for i, col in enumerate(actual_columns, 1):
            comma = "," if i < len(actual_columns) else ""
            print(f'            "{col}"{comma}')
        print('));')
        
        # 测试查询
        print("\n" + "=" * 80)
        print("测试查询:")
        print("=" * 80)
        
        try:
            # 查询一条测试数据
            cur.execute("SELECT * FROM ticket LIMIT 1")
            row = cur.fetchone()
            cur.description
            
            print(f"查询成功！返回列数: {len(cur.description)}")
            print("\n列名列表:")
            for i, desc in enumerate(cur.description, 1):
                print(f"  {i:2d}. {desc[0]}")
                
        except Exception as e:
            print(f"查询失败: {e}")
        
        cur.close()
        conn.close()
        
        if missing_columns:
            print(f"\n[警告] 缺失 {len(missing_columns)} 个字段，需要添加")
        if extra_columns:
            print(f"\n[信息] 发现 {len(extra_columns)} 个额外字段")
        
        if not missing_columns and not extra_columns:
            print("\n[成功] 所有字段匹配！")
        
    except Exception as e:
        print(f"错误: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()