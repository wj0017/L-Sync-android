import sqlite3

HASH = 'bbcb8e4dc1c97c0a643d53a21af498d9'
conn = sqlite3.connect('app/src/main/assets/esv.db')
conn.execute('CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)')
conn.execute('INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)', (HASH,))
conn.commit()
print('room_master_table:', conn.execute('SELECT * FROM room_master_table').fetchall())
print('esv_verses 수:', conn.execute('SELECT COUNT(*) FROM esv_verses').fetchone()[0])
conn.close()
print('완료')
