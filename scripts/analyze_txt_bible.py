import re

path = r'C:\Users\이우진\Downloads\개역개정4판(구약+신약).txt'

books = {}
pattern = re.compile(r'^(\S+?)(\d+):(\d+(?:-\d+)?)\s+(.*)')

with open(path, encoding='cp949') as f:
    for line in f:
        line = line.rstrip()
        m = pattern.match(line)
        if m:
            abbr = m.group(1)
            if abbr not in books:
                books[abbr] = 0
            books[abbr] += 1

print(f'책 약자 목록 ({len(books)}권):')
for i, (abbr, cnt) in enumerate(books.items(), 1):
    print(f'  {i:2}. {abbr} ({cnt}절)')
