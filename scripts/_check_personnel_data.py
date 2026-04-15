import psycopg2

conn = psycopg2.connect(
    host='localhost',
    port=8000,
    database='wangzhijun',
    user='wangzhijun',
    password='Gauss@123#'
)
cur = conn.cursor()

cur.execute('SELECT "运维人员", "开发人员", "协同人员" FROM ticket LIMIT 5')
rows = cur.fetchall()
print('Sample personnel data:')
for row in rows:
    print(f'  运维: {row[0]}, 开发: {row[1]}, 协同: {row[2]}')

cur.execute('SELECT COUNT(*) FROM ticket WHERE "运维人员" IS NOT NULL AND "运维人员" != ""')
ops_count = cur.fetchone()[0]

cur.execute('SELECT COUNT(*) FROM ticket WHERE "开发人员" IS NOT NULL AND "开发人员" != ""')
dev_count = cur.fetchone()[0]

cur.execute('SELECT COUNT(*) FROM ticket WHERE "协同人员" IS NOT NULL AND "协同人员" != ""')
coop_count = cur.fetchone()[0]

cur.execute('SELECT COUNT(*) FROM ticket')
total_count = cur.fetchone()[0]

print(f'\nTotal records: {total_count}')
print(f'运维人员填充: {ops_count} ({ops_count/total_count*100:.1f}%)')
print(f'开发人员填充: {dev_count} ({dev_count/total_count*100:.1f}%)')
print(f'协同人员填充: {coop_count} ({coop_count/total_count*100:.1f}%)')

cur.close()
conn.close()