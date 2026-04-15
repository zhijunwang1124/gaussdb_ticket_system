import psycopg2

conn = psycopg2.connect(
    host='localhost',
    port=8000,
    database='wangzhijun',
    user='wangzhijun',
    password='Gauss@123#'
)
cur = conn.cursor()
cur.execute('SELECT column_key, column_name, is_top_level FROM analysis_column_config')
rows = cur.fetchall()
print('Analysis column config:')
for r in rows:
    print(f'  key={r[0]}, name={r[1]}, is_top_level={r[2]}')
cur.close()
conn.close()