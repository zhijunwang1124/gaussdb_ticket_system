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

# Get all yw_no values
cur.execute('SELECT yw_no FROM ticket ORDER BY id')
rows = cur.fetchall()
yw_nos = [row[0] for row in rows]

# Define personnel data
ops_persons = ['张三', '李四', '王五', '赵六', '钱七', '孙八', '周九', '吴十']
dev_persons = ['开发甲', '开发乙', '开发丙', '开发丁']
coop_persons = ['协同A', '协同B']

random.seed(42)

print(f'Found {len(yw_nos)} records to update')

for i, yw_no in enumerate(yw_nos):
    # Assign ops person (all records must have)
    ops = random.choice(ops_persons)
    
    # 70% chance to have dev person
    has_dev = random.random() < 0.7
    dev = random.choice(dev_persons) if has_dev else None
    
    # 20% chance to have coop person
    has_coop = random.random() < 0.2
    coop = random.choice(coop_persons) if has_coop else None
    
    cur.execute(f'''
        UPDATE ticket 
        SET "运维人员" = '{ops}',
            "开发人员" = {'NULL' if dev is None else f"'{dev}'"},
            "协同人员" = {'NULL' if coop is None else f"'{coop}'"}
        WHERE yw_no = '{yw_no}'
    ''')
    
    if (i + 1) % 50 == 0:
        print(f'Updated {i + 1}/{len(yw_nos)} records...')

conn.commit()
print(f'\nSuccessfully updated {len(yw_nos)} records')

# Verify
cur.execute("SELECT COUNT(*) FROM ticket WHERE \"运维人员\" IS NOT NULL AND \"运维人员\" <> ''")
ops_filled = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM ticket WHERE \"开发人员\" IS NOT NULL AND \"开发人员\" <> ''")
dev_filled = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM ticket WHERE \"协同人员\" IS NOT NULL AND \"协同人员\" <> ''")
coop_filled = cur.fetchone()[0]

print(f'\nVerification:')
print(f'  运维人员: {ops_filled}/{len(yw_nos)}')
print(f'  开发人员: {dev_filled}/{len(yw_nos)}')
print(f'  协同人员: {coop_filled}/{len(yw_nos)}')

cur.close()
conn.close()