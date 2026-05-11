import sqlite3
conn = sqlite3.connect('app/src/main/assets/bible.db')

# 에스더 8:9
rows = conn.execute(
    "SELECT book, chapter, verse, length(text), text FROM bible_verses "
    "WHERE book_name LIKE '%스더%' AND chapter=8 AND verse=9"
).fetchall()
for r in rows:
    print(f'{r[1]}:{r[2]} ({r[3]}자)')
    print(r[4])
    print()

# 빈 텍스트
empty = conn.execute("SELECT COUNT(*) FROM bible_verses WHERE text IS NULL OR trim(text)=''").fetchone()[0]
print(f'빈 절: {empty}개')

# 최대/평균 길이
maxlen = conn.execute('SELECT MAX(length(text)) FROM bible_verses').fetchone()[0]
avglen = conn.execute('SELECT AVG(length(text)) FROM bible_verses').fetchone()[0]
print(f'최대 {maxlen}자 / 평균 {avglen:.0f}자')

conn.close()
