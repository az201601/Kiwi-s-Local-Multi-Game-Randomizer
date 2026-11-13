#!/usr/bin/env python3
"""
extract_text.py — harvest Harvest Moon GB (USA) dialogue records.

Strategy: find maximal runs of text-ish bytes, then keep those that look like
English. A run byte belongs to the glyph alphabet:
    $00-$53 (letters/digits/emphatic caps glyph space),
    $EF (space), $8E (comma), $E9 (line end),
    $A0-$A3 (player-name farcall tokens)
Code bytes in this range can form runs, so each candidate is scored:
    - letters (A-Z/a-z ranges) ratio >= 0.45
    - at least one space $EF OR sentence punctuation
    - at least 2 distinct letters
Output: <bank>:<offset>: <decoded>
Writes ROM-derived text to stdout; redirect to work/ (never commit).
"""
import sys, argparse

TEXTISH = set(range(0x00, 0x54)) | {0x8E, 0xEF, 0xE9, 0xFE, 0xFF,
                                    0xA0, 0xA1, 0xA2, 0xA3}

def decode_byte(b):
    if b == 0xEF: return ' '
    if b == 0x8E: return ','
    if b == 0xE9: return '\n'
    if 0xA0 <= b <= 0xA3: return f'<N{b & 3}>'
    if b == 0x40: return '!'
    if b == 0x41: return '?'
    if b == 0x4B: return "'"
    if b == 0x4C: return '.'
    if b == 0x53: return "'s"
    if b == 0x3E: return '['
    if b == 0x3F: return ']'
    if b == 0x44: return ''           # author-marked hyphenation point
    if 0x1A <= b <= 0x33: return chr(ord('A') + b - 0x1A)
    if 0x34 <= b <= 0x3D: return chr(ord('0') + b - 0x34)
    if 0x00 <= b <= 0x19: return chr(ord('A') + b).lower()  # bold caps, shown as lowercase
    return f'[{b:02x}]'

def scan(data):
    i = 0
    n = len(data)
    while i < n:
        if data[i] in TEXTISH:
            j = i
            while j < n and data[j] in TEXTISH:
                j += 1
            yield i, j - i
            i = j
        else:
            i += 1

def looks_english(seg):
    if len(seg) < 8:
        return False
    letters = sum(1 for b in seg
                  if (0x1a <= b <= 0x33) or (0x00 <= b <= 0x19))
    if letters / len(seg) < 0.45:
        return False
    has_space = 0xef in seg
    has_punct = any(b in (0x40, 0x41, 0x4C, 0x8E) for b in seg)
    if not (has_space or has_punct):
        return False
    distinct = len({b for b in seg if 0x1a <= b <= 0x33} |
                   {b for b in seg if 0x00 <= b <= 0x19})
    return distinct >= 2

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('rom')
    ap.add_argument('--min-len', type=int, default=8)
    args = ap.parse_args()
    data = open(args.rom, 'rb').read()
    count = 0
    for off, ln in scan(data):
        seg = data[off:off + ln]
        if not looks_english(seg):
            continue
        bank = off // 0x4000
        bank_off = off % 0x4000
        if bank == 0:
            addr = off
        else:
            addr = 0x4000 + bank_off
        decoded = ''.join(decode_byte(b) for b in seg).rstrip()
        print(f'{bank:02x}:{off:#x} ({ln}b): {decoded}')
        print('---')
        count += 1
    print(f'# {count} candidate records', file=sys.stderr)

if __name__ == '__main__':
    main()
