"""Lists javac 'cannot find symbol' errors with the missing symbol: python tools/errsym.py <javac output file>"""
import re, sys

lines = open(sys.argv[1], encoding='utf-8', errors='replace').read().split('\n')
out = set()
for i, line in enumerate(lines):
    m = re.search(r'botwclimb[\\/](.*?\.java):(\d+): error: (.*)', line)
    if not m:
        continue
    msg = m.group(3)
    code = lines[i + 1].strip() if i + 1 < len(lines) else ''
    sym = ''
    for j in range(i + 2, min(i + 7, len(lines))):
        if 'symbol:' in lines[j]:
            sym = lines[j].strip()
            break
    out.add(f"{m.group(1).replace(chr(92), '/')}:{m.group(2)} | {msg[:60]} | {sym} | {code[:80]}")
print('\n'.join(sorted(out)))
