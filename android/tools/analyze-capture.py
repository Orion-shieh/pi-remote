import io
import json
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else 'android/terminal-emulator/src/test/resources/pi-capture.bin'
data = io.open(path, 'rb').read().decode('utf-8')

CSI = re.compile(r'\x1b\[[0-9;?><=]*[A-Za-z@`~]')
OSC = re.compile(r'\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)')
ESC2 = re.compile(r'\x1b[()][A-Za-z0-9]|\x1b[=>78DMEHcNOP]')

pattern = re.compile('|'.join('(?:%s)' % p.pattern for p in (CSI, OSC, ESC2)))

tokens = []
pos = 0
for m in pattern.finditer(data):
    if m.start() > pos:
        seg = data[pos:m.start()]
        if seg:
            tokens.append(('TEXT', json.dumps(seg, ensure_ascii=True)))
    tokens.append(('SEQ', json.dumps(m.group(0), ensure_ascii=True)))
    pos = m.end()
if pos < len(data):
    tokens.append(('TEXT', json.dumps(data[pos:], ensure_ascii=True)))

print('tokens:', len(tokens))
for i, (kind, value) in enumerate(tokens):
    print('%4d %-5s %s' % (i, kind, value[:100]))
