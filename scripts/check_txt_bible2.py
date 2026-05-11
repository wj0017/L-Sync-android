path = r'C:\Users\이우진\Downloads\개역개정4판(구약+신약).txt'

with open(path, encoding='cp949') as f:
    lines = f.readlines()

print(f'총 {len(lines)}줄')
print('처음 5줄:')
for l in lines[:5]:
    print(' ', l.rstrip())

# 가장 긴 줄
longest = max(lines, key=len)
print(f'\n가장 긴 줄 ({len(longest.rstrip())}자):')
print(' ', longest.rstrip())

# 에스더 8:9 찾기
print('\n에스더 8:9:')
for l in lines:
    if '8:9' in l and ('스더' in l or '에스' in l):
        print(' ', l.rstrip())

# 포맷 확인: 첫 줄 파싱
first = lines[0].rstrip()
print(f'\n포맷: {repr(first[:30])}')
