import sqlite3, re, os

with open(r'C:/Users/이우진/Downloads/bible.sql', 'r', encoding='utf-8') as f:
    content = f.read()

out_path = r'C:/GitHub/L-Sync/app/src/main/assets/bible.db'
os.makedirs(os.path.dirname(out_path), exist_ok=True)

if os.path.exists(out_path):
    os.remove(out_path)

conn = sqlite3.connect(out_path)
cur = conn.cursor()

cur.execute("""
CREATE TABLE bible_verses (
    idx INTEGER PRIMARY KEY,
    book INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verse INTEGER NOT NULL,
    text TEXT NOT NULL,
    testament TEXT NOT NULL,
    book_name TEXT NOT NULL,
    book_short TEXT NOT NULL
)
""")
cur.execute("CREATE INDEX idx_book_chapter ON bible_verses (book, chapter)")

pattern = re.compile(
    r'\((\d+),\s*\d+,\s*(\d+),\s*(\d+),\s*(\d+),\s*\'((?:[^\']|\'\')*)\',\s*\'([^\']+)\',\s*\'([^\']+)\',\s*\'([^\']+)\'\)'
)

rows = []
for m in pattern.finditer(content):
    idx, book, chapter, verse, text, testament, book_name, book_short = m.groups()
    text = text.replace("''", "'")
    rows.append((int(idx), int(book), int(chapter), int(verse), text, testament, book_name, book_short))

cur.executemany("INSERT INTO bible_verses VALUES (?,?,?,?,?,?,?,?)", rows)
conn.commit()
conn.close()

size = os.path.getsize(out_path) / 1024 / 1024
print("완료: {}절 bible.db {:.1f}MB".format(len(rows), size))
