#!/usr/bin/env python3
"""
textcodec.py — Harvest Moon GB (USA) dialogue text codec.

The US build stores dialogue as 1 byte per glyph. Bytes map to the font in VRAM
(the ROM font tile order differs from the text code order; the loader reorders it,
so glyph codes == tile indices in VRAM, NOT in ROM).

Confirmed mappings (byte-position verified + glyph identity verified against
rendered US text in the opening cutscene, 2026-11-13):
    $1A-$33   A-Z (regular)             # verified everywhere
    $34-$3D   0-9 ($34='0', slashed zero)  # "6AM", "75G", "200G"
    $00-$19   Bold A-Z (bold A=$00)     # sentence-initial emphasis / speaker names
    $3E       '['                       # "[SHIPPING BOX]" signs (bracket, not paren)
    $3F       ']'
    $40       '!'                       # "successor!", "Ranch Master!!" (render-verified)
    $41       '?'                       # yes/no prompts ("MONEY?")  (corpus-consistent)
    $44       author-marked word-break point (renders '-' at line end; invisible
              otherwise — "COL{44}DER", "HURRI{44}CANE"). The font tile at index
              $44 IS '-' (render-verified in names).
    $4B       apostrophe                # "WEREN'T", "I'M"
    $4C       '.' (period)              # "AAAA." (render-verified)
    $53       "'s" ligature             # "GRANDPA'S", "IT'S"
    $8E       ',' (comma)               # "Wake Up,", "So,"  (render-verified)
    $A0-$A3   player-name farcall tokens (FF $a0 = name slot 1..4) -> "<N0>".."<N3>"
    $EF       space
    $E9       end of display line
    $FE       record/section delimiter
    $FF       record end
    other     reported as [xx]

Name-slot rendering: the text VM's player-name token emits codes $A0-$A3
(four farcalls to $17:$7739, which return pointers to $b8f4-$b8f7 = the farmer
name in SRAM). Name bytes bypass the opcode layer and are drawn as direct font
indices, so e.g. $44 in a name draws '-' (em-dash/hyphen glyph), unlike records.

Record structure (observed):
    FE <padding 00*> <header 2-8 bytes> <padding 00*> <TEXT... E9>
The meaning of the header bytes is NOT yet decoded (see open-questions.md).

Usage:
    python3 textcodec.py --offset 0x1d5f0 --length 0x200 ROM.gbc
    python3 textcodec.py --offset 0x1d5f0 --length 0x200 ROM.gbc --raw
"""
import argparse

def decode_byte(b):
    if b == 0xEF: return ' '
    if b == 0x8E: return ','
    if b == 0xE9: return '\n    '
    if b == 0xFE: return '\n>>> '
    if b == 0xFF: return '\n<<< '
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

def decode(buf):
    out = []
    for b in buf:
        ch = decode_byte(b)
        out.append(ch)
    return ''.join(out)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('rom')
    ap.add_argument('--offset', required=True, type=lambda x: int(x, 0))
    ap.add_argument('--length', required=True, type=lambda x: int(x, 0))
    args = ap.parse_args()
    data = open(args.rom, 'rb').read()
    seg = data[args.offset:args.offset + args.length]
    text = decode(seg)
    print(f'--- {args.offset:#x}..{args.offset + len(seg):#x} ---')
    print(text)

if __name__ == '__main__':
    main()
