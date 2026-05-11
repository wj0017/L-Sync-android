import re, sqlite3, os

TXT_PATH  = r'C:\Users\이우진\Downloads\개역개정4판(구약+신약).txt'
OLD_DB    = 'app/src/main/assets/bible.db'
OUT_PATH  = 'app/src/main/assets/bible_new.db'
ROOM_HASH = 'c8932dc76d12d3284711c1459a4b9439'

# 기존 DB에서 책 번호별 메타데이터 (book_name, book_short, testament) 가져오기
old = sqlite3.connect(OLD_DB)
meta = {}  # book_num → (book_name, book_short, testament)
for row in old.execute(
    'SELECT DISTINCT book, book_name, book_short, testament FROM bible_verses ORDER BY book'
):
    meta[row[0]] = (row[1], row[2], row[3])
old.close()
print(f'메타데이터: {len(meta)}권 로드')

# 텍스트 파일 파싱
pattern = re.compile(r'^(\S+?)(\d+):(\d+)(?:-\d+)?\s+(.*)')
lines_parsed = []
book_order = {}  # abbr → book_num (순서 기반)

with open(TXT_PATH, encoding='cp949') as f:
    for line in f:
        line = line.rstrip()
        m = pattern.match(line)
        if not m:
            continue
        abbr, chapter, verse, text = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
        if abbr not in book_order:
            book_order[abbr] = len(book_order) + 1
        book_num = book_order[abbr]
        lines_parsed.append((book_num, chapter, verse, text))

print(f'파싱 완료: {len(lines_parsed)}절, {len(book_order)}권')

# 새 DB 생성
if os.path.exists(OUT_PATH):
    os.remove(OUT_PATH)
dst = sqlite3.connect(OUT_PATH)

dst.execute("""
CREATE TABLE IF NOT EXISTS bible_verses (
    idx        INTEGER NOT NULL,
    book       INTEGER NOT NULL,
    chapter    INTEGER NOT NULL,
    verse      INTEGER NOT NULL,
    text       TEXT    NOT NULL,
    testament  TEXT    NOT NULL,
    book_name  TEXT    NOT NULL,
    book_short TEXT    NOT NULL,
    PRIMARY KEY(idx)
)
""")

rows = []
for i, (book, chapter, verse, text) in enumerate(lines_parsed, 1):
    m = meta.get(book, ('', '', ''))
    book_name, book_short, testament = m
    rows.append((i, book, chapter, verse, text, testament, book_name, book_short))

dst.executemany(
    'INSERT INTO bible_verses VALUES (?,?,?,?,?,?,?,?)', rows
)
dst.execute('CREATE INDEX IF NOT EXISTS idx_bv_book_chapter ON bible_verses(book, chapter)')
dst.execute('PRAGMA user_version = 1')

# room_master_table
dst.execute('CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)')
dst.execute('INSERT OR REPLACE INTO room_master_table VALUES (42, ?)', (ROOM_HASH,))
dst.commit()

# 검증
count = dst.execute('SELECT COUNT(*) FROM bible_verses').fetchone()[0]
maxlen = dst.execute('SELECT MAX(length(text)) FROM bible_verses').fetchone()[0]
avglen = dst.execute('SELECT AVG(length(text)) FROM bible_verses').fetchone()[0]

# 에스더 8:9
esther_row = dst.execute(
    'SELECT length(text), text FROM bible_verses WHERE book=17 AND chapter=8 AND verse=9'
).fetchone()

print(f'저장: {count}절')
print(f'텍스트 최대 {maxlen}자 / 평균 {avglen:.1f}자')
if esther_row:
    print(f'에스더 8:9 ({esther_row[0]}자): {esther_row[1][:40]}...')

dst.close()

size = os.path.getsize(OUT_PATH) / 1024 / 1024
print(f'파일 크기: {size:.1f}MB')
os.replace(OUT_PATH, OLD_DB)
print('bible.db 교체 완료')
