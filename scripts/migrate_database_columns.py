# -*- coding: utf-8 -*-
"""
数据库迁移脚本：更新ticket表结构，添加缺失的字段
自动检测并添加缺失的字段，避免数据丢失
"""
import sys

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

# 需要检查并添加的字段
COLUMNS_TO_ADD = [
    ("问题严重性", "VARCHAR(128)"),
    ("实例简称", "VARCHAR(128)"),
    ("业务名称", "VARCHAR(256)"),
    ("描述", "TEXT"),
    ("滞留时间", "VARCHAR(64)"),
    ("SLA时间", "VARCHAR(64)"),
    ("问题审核人", "VARCHAR(128)"),
    ("运维人员分析人", "VARCHAR(128)"),
    ("开发人员分析人", "VARCHAR(128)"),
    ("开发人员闭环人", "VARCHAR(128)"),
    ("运维人员闭环人", "VARCHAR(128)"),
    ("问题审核关闭人", "VARCHAR(128)"),
    ("创建日期", "TIMESTAMP"),
    ("问题审核总时长", "VARCHAR(64)"),
    ("运维人员分析总时长", "VARCHAR(64)"),
    ("开发人员分析总时长", "VARCHAR(64)"),
    ("开发人员闭环总时长", "VARCHAR(64)"),
    ("运维人员闭环总时长", "VARCHAR(64)"),
    ("问题审核关闭总时长", "VARCHAR(64)"),
    ("故障到恢复用时", "VARCHAR(64)"),
    ("是否涉及故障恢复", "VARCHAR(32)"),
    ("磐石版本是否涉及", "VARCHAR(32)"),
    ("是否需要预警", "VARCHAR(32)"),
    ("DTS单号", "VARCHAR(64)"),
    ("规避措施", "TEXT"),
    ("是否质量问题", "VARCHAR(32)"),
    ("恢复方法", "TEXT"),
    ("是否咨询问题", "VARCHAR(32)"),
    ("是否涉及内核升级", "VARCHAR(32)"),
    ("协同处理人", "VARCHAR(256)"),
    ("高斯版本", "VARCHAR(128)"),
    ("HCS版本号", "VARCHAR(128)"),
    ("HCS/轻量化", "VARCHAR(32)"),
    ("问题类型", "VARCHAR(128)"),
    ("根因分类", "VARCHAR(128)"),
    ("部署形态", "VARCHAR(64)"),
]

def get_existing_columns(cursor):
    """获取表中已存在的列"""
    cursor.execute("""
        SELECT column_name, data_type
        FROM information_schema.columns
        WHERE table_name = 'ticket'
        ORDER BY ordinal_position
    """)
    return {row[0]: row[1] for row in cursor.fetchall()}

def check_and_add_columns(cursor):
    """检查并添加缺失的列"""
    existing_columns = get_existing_columns(cursor)
    added_columns = []
    
    print(f"当前ticket表共有 {len(existing_columns)} 个字段")
    print("\n检查缺失字段...")
    
    for column_name, column_type in COLUMNS_TO_ADD:
        if column_name not in existing_columns:
            try:
                alter_sql = f'ALTER TABLE ticket ADD COLUMN "{column_name}" {column_type}'
                cursor.execute(alter_sql)
                added_columns.append(column_name)
                print(f"  [添加] {column_name} ({column_type})")
            except Exception as e:
                print(f"  [失败] {column_name}: {e}")
        else:
            print(f"  [已存在] {column_name}")
    
    return added_columns

def migrate_data_if_needed(cursor):
    """如果存在旧的'起始日期'字段，将其值复制到'创建日期'"""
    cursor.execute("""
        SELECT column_name FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '起始日期'
    """)
    has_start_date = cursor.fetchone() is not None
    
    cursor.execute("""
        SELECT column_name FROM information_schema.columns
        WHERE table_name = 'ticket' AND column_name = '创建日期'
    """)
    has_create_date = cursor.fetchone() is not None
    
    if has_start_date and has_create_date:
        # 检查是否有空的创建日期字段
        cursor.execute("""
            SELECT COUNT(*) FROM ticket WHERE "创建日期" IS NULL AND "起始日期" IS NOT NULL
        """)
        count = cursor.fetchone()[0]
        
        if count > 0:
            print(f"\n数据迁移：将'起始日期'的值复制到'创建日期' ({count} 条记录)")
            cursor.execute("""
                UPDATE ticket SET "创建日期" = "起始日期" WHERE "创建日期" IS NULL
            """)
            print(f"  [完成] 已迁移 {count} 条记录")
    
    return has_start_date

def show_final_structure(cursor):
    """显示最终的表结构"""
    print("\n" + "=" * 60)
    print("最终的ticket表结构：")
    print("=" * 60)
    
    cursor.execute("""
        SELECT column_name, data_type, character_maximum_length
        FROM information_schema.columns
        WHERE table_name = 'ticket'
        ORDER BY ordinal_position
    """)
    
    columns = cursor.fetchall()
    for idx, (col_name, data_type, max_length) in enumerate(columns, 1):
        type_str = data_type
        if max_length:
            type_str = f"{data_type}({max_length})"
        print(f"  {idx:2d}. {col_name:20s} - {type_str}")

def main():
    print("开始数据库迁移...")
    print("=" * 60)
    
    try:
        print(f"正在连接数据库: {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}")
        conn = psycopg2.connect(**DB_CONFIG)
        conn.autocommit = False
        cur = conn.cursor()
        print("数据库连接成功")
    except Exception as e:
        print(f"数据库连接失败: {e}")
        sys.exit(1)
    
    try:
        # 添加缺失的列
        added_columns = check_and_add_columns(cur)
        
        # 数据迁移
        migrate_data_if_needed(cur)
        
        # 提交事务
        conn.commit()
        
        print(f"\n成功添加 {len(added_columns)} 个新字段")
        
        # 显示最终结构
        show_final_structure(cur)
        
        print("\n" + "=" * 60)
        print("数据库迁移完成！")
        print("=" * 60)
        
        if added_columns:
            print(f"\n新增字段列表: {', '.join(added_columns)}")
        
        cur.close()
        conn.close()
        
        print("\n现在可以重启应用并测试导入功能了！")
        
    except Exception as e:
        conn.rollback()
        print(f"\n迁移过程中发生错误: {e}")
        print("已回滚所有更改")
        cur.close()
        conn.close()
        sys.exit(1)

if __name__ == "__main__":
    main()