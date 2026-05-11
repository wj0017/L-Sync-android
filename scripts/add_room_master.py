import sqlite3

HASH = 'c8932dc76d12d3284711c1459a4b9439'

conn = sqlite3.connect('app/src/main/assets/bible.db')
conn.execute('CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)')
conn.execute('INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)', (HASH,))
conn.commit()

cur = conn.cursor()
cur.execute('SELECT * FROM room_master_table')
print('room_master_table:', cur.fetchall())
cur.execute('SELECT COUNT(*) FROM bible_verses')
print('절 수:', cur.fetchone()[0])
conn.close()
print('완료')
