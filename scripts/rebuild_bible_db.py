import sqlite3, os

src = sqlite3.connect('app/src/main/assets/bible.db')
verses = src.execute(
    'SELECT idx, book, chapter, verse, text, testament, book_name, book_short '
    'FROM bible_verses ORDER BY idx'
).fetchall()
src.close()
print(f'읽은 절 수: {len(verses)}')

new_path = 'app/src/main/assets/bible_new.db'
if os.path.exists(new_path):
    os.remove(new_path)

dst = sqlite3.connect(new_path)
# Room이 생성하는 정확한 DDL — idx에 NOT NULL 명시 (notnull=1)
dst.execute("""
CREATE TABLE IF NOT EXISTS bible_verses (
    idx     INTEGER NOT NULL,
    book    INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verse   INTEGER NOT NULL,
    text    TEXT    NOT NULL,
    testament  TEXT NOT NULL,
    book_name  TEXT NOT NULL,
    book_short TEXT NOT NULL,
    PRIMARY KEY(idx)
)
""")
dst.executemany(
    'INSERT INTO bible_verses VALUES (?,?,?,?,?,?,?,?)',
    verses,
)
dst.execute(
    'CREATE INDEX IF NOT EXISTS index_bible_verses_book_chapter '
    'ON bible_verses (book, chapter)'
)
dst.execute('PRAGMA user_version = 1')
dst.commit()

count = dst.execute('SELECT COUNT(*) FROM bible_verses').fetchone()[0]
print(f'저장된 절 수: {count}')

for row in dst.execute('PRAGMA table_info(bible_verses)'):
    print(f'  {row[1]:12} type={row[2]:8} notnull={row[3]} pk={row[5]}')

dst.close()
os.replace(new_path, 'app/src/main/assets/bible.db')
print('bible.db 교체 완료')
