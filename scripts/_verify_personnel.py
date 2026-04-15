import psycopg2

conn = psycopg2.connect(
    host='localhost',
    port=8000,
    database='wangzhijun',
    user='wangzhijun',
    password='Gauss@123#'
)
cur = conn.cursor()

cur.execute('SELECT COUNT(*) FROM ticket')
total = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM ticket WHERE \"运维人员\" IS NOT NULL AND \"运维人员\" <> ''")
ops_filled = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM ticket WHERE \"开发人员\" IS NOT NULL AND \"开发人员\" <> ''")
dev_filled = cur.fetchone()[0]

cur.execute("SELECT COUNT(*) FROM ticket WHERE \"协同人员\" IS NOT NULL AND \"协同人员\" <> ''")
coop_filled = cur.fetchone()[0]

print(f'Total records: {total}')
print(f'Ops filled: {ops_filled}')
print(f'Dev filled: {dev_filled}')
print(f'Coop filled: {coop_filled}')

cur.close()
conn.close()