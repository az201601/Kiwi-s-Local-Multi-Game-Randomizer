#!/usr/bin/env python3
"""
bankscan.py — quick structural scan of the ROM: bank tags, fill bytes, entropy,
and twin-build similarity. Superseded by rominfo.py for checksums; this one is a
fast overview for RE sessions.
"""
import sys, math
from collections import Counter

def main():
    data = open(sys.argv[1], 'rb').read()
    B = 0x4000
    n = len(data) // B
    for b in range(n):
        seg = data[b * B:(b + 1) * B]
        c = Counter(seg)
        ent = -sum((v / B) * math.log2(v / B) for v in c.values())
        print(f'bank {b:02x} @ {b * B:#07x}: hi={sum(v for k, v in c.items() if k >= 0x80) / B * 100:5.1f}% '
              f'ff={seg.count(0xff) / B * 100:5.1f}% 00={seg.count(0) / B * 100:5.1f}% ent={ent:.2f}')

if __name__ == '__main__':
    main()
