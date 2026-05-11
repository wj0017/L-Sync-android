import sys

path = r'C:\Users\이우진\Downloads\개역개정4판(구약+신약).txt'

for enc in ['utf-8', 'utf-8-sig', 'cp949', 'euc-kr']:
    try:
        with open(path, encoding=enc) as f:
            lines = [f.readline() for _ in range(5)]
        print(f'[{enc}]')
        for l in lines:
            print(' ', repr(l[:80]))
        print()
        break
    except Exception as e:
        print(f'[{enc}] 실패: {e}')
