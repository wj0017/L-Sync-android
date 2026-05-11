import json, sqlite3, os, urllib.request

ESV_URL = "https://bolls.life/static/translations/ESV.json"
OUT_PATH = "app/src/main/assets/esv.db"

print("ESV.json 다운로드 중...")
with urllib.request.urlopen(ESV_URL) as r:
    verses = json.loads(r.read().decode())
print(f"다운로드 완료: {len(verses)}절")

if os.path.exists(OUT_PATH):
    os.remove(OUT_PATH)

conn = sqlite3.connect(OUT_PATH)
conn.execute("""
CREATE TABLE IF NOT EXISTS esv_verses (
    idx     INTEGER NOT NULL,
    book    INTEGER NOT NULL,
    chapter INTEGER NOT NULL,
    verse   INTEGER NOT NULL,
    text    TEXT    NOT NULL,
    PRIMARY KEY(idx)
)
""")
conn.executemany(
    "INSERT INTO esv_verses(idx, book, chapter, verse, text) VALUES(?,?,?,?,?)",
    [(v["pk"], v["book"], v["chapter"], v["verse"], v["text"]) for v in verses],
)
conn.execute("CREATE INDEX IF NOT EXISTS idx_esv_book_chapter ON esv_verses(book, chapter)")
conn.execute("PRAGMA user_version = 1")
conn.commit()

# room_master_table — 나중에 hash 확인 후 추가
count = conn.execute("SELECT COUNT(*) FROM esv_verses").fetchone()[0]
for row in conn.execute("PRAGMA table_info(esv_verses)"):
    print(f"  {row[1]:10} notnull={row[3]} pk={row[5]}")
conn.close()

size = os.path.getsize(OUT_PATH) / 1024 / 1024
print(f"저장 완료: {count}절, {size:.1f}MB → {OUT_PATH}")
