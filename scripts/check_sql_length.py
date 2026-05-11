import re

# SQL 파일에서 sentence 컬럼 텍스트 길이 분포 확인
lengths = []
long_samples = []

with open(r'C:\Users\이우진\Downloads\bible.sql', encoding='utf-8', errors='replace') as f:
    for line in f:
        if not line.startswith('('):
            continue
        # INSERT VALUES 행에서 sentence 추출 (6번째 컬럼)
        m = re.match(r"\((\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*'(.*?)',\s*'", line)
        if m:
            text = m.group(6)
            l = len(text)
            lengths.append(l)
            if l >= 80:
                long_samples.append((m.group(3), m.group(4), m.group(5), l, text))

if lengths:
    print(f'총 {len(lengths)}절')
    print(f'최대 {max(lengths)}자 / 평균 {sum(lengths)/len(lengths):.1f}자')
    print(f'80자 이상: {len(long_samples)}절')
    print()
    print('가장 긴 절 top 5:')
    for s in sorted(long_samples, key=lambda x: -x[3])[:5]:
        print(f'  book={s[0]}, {s[1]}:{s[2]} ({s[3]}자): {s[4][:60]}...')
else:
    print('데이터를 찾지 못했습니다')
