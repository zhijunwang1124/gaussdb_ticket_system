import psycopg2

conn = psycopg2.connect(
    host='localhost',
    port=8000,
    database='wangzhijun',
    user='wangzhijun',
    password='Gauss@123#'
)
cur = conn.cursor()

# Check if personnel columns exist
columns = ['运维人员', '开发人员', '协同人员']
print('Checking personnel columns...')
for col in columns:
    cur.execute(f"SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket' AND column_name = '{col}'")
    result = cur.fetchone()
    if result:
        print(f'  {col}: EXISTS')
    else:
        print(f'  {col}: NOT EXISTS')

# Add missing columns
for col in columns:
    cur.execute(f"SELECT column_name FROM information_schema.columns WHERE table_name = 'ticket' AND column_name = '{col}'")
    result = cur.fetchone()
    if not result:
        print(f'\nAdding column: {col}')
        cur.execute(f'ALTER TABLE ticket ADD COLUMN "{col}" VARCHAR(128)')

conn.commit()
print('\nColumn check complete')

cur.close()
conn.close()