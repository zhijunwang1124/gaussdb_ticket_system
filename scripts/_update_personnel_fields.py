import psycopg2
import random

conn = psycopg2.connect(
    host='localhost',
    port=8000,
    database='wangzhijun',
    user='wangzhijun',
    password='Gauss@123#'
)
cur = conn.cursor()

# Add personnel columns if they don't exist
columns_to_add = ['运维人员', '开发人员', '协同人员']
for col in columns_to_add:
    cur.execute(f"SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket' AND column_name = '{col}'")
    result = cur.fetchone()
    if not result:
        cur.execute(f'ALTER TABLE ticket ADD COLUMN "{col}" VARCHAR(128)')

# Update existing 500 records with personnel data
ops_persons = ['张三', '李四', '王五', '赵六', '钱七', '孙八', '周九', '吴十']
dev_persons = ['开发甲', '开发乙', '开发丙', '开发丁', None]
coop_persons = ['协同A', '协同B', None]

random.seed(42)

cur.execute('SELECT yw_no FROM ticket ORDER BY id')
rows = cur.fetchall()
yw_nos = [row[0] for row in rows]

for i in range(len(yw_nos)):
    yw_no = yw_nos[i]
    ops = random.choice(ops_persons)
    # 70% chance to have dev person, 20% chance to have coop person
    has_dev = random.random() < 0.7
    dev = dev_persons[random.randint(0, len(dev_persons) - 1)] if has_dev else None
    has_coop = random.random() < 0.2
    coop = coop_persons[random.randint(0, len(coop_persons) - 1)] if has_coop else None
    
    cur.execute(f'''
        UPDATE ticket 
        SET "运维人员" = '{ops}', "开发人员" = '{dev}', "协同人员" = '{coop}'
        WHERE yw_no = '{yw_no}'
    ''')

print(f'Updated {len(yw_nos)} records with personnel fields')
conn.commit()
cur.close()
conn.close()