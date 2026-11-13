#!/usr/bin/env python3
"""
render_tiles.py — render a ROM region as a 2bpp Game Boy tile sheet PNG.

Usage:
    python3 render_tiles.py ROM.gbc --offset 0x44000 --length 0x4000 --out work/bank11_tiles.png [--cols 16] [--scale 2]
"""
import argparse
from PIL import Image

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('rom'); ap.add_argument('--offset', required=True, type=lambda x: int(x, 0))
    ap.add_argument('--length', required=True, type=lambda x: int(x, 0))
    ap.add_argument('--out', required=True)
    ap.add_argument('--cols', type=int, default=16)
    ap.add_argument('--scale', type=int, default=2)
    args = ap.parse_args()

    data = open(args.rom, 'rb').read()[args.offset:args.offset + args.length]
    TILE = 16
    ntiles = len(data) // TILE
    cols = args.cols
    rows = (ntiles + cols - 1) // cols
    s = args.scale
    img = Image.new('RGB', (cols * 8 * s, rows * 8 * s), (0, 0, 0))
    palette = [255, 170, 85, 0]
    for t in range(ntiles):
        tile = data[t * TILE:(t + 1) * TILE]
        cx, cy = (t % cols) * 8 * s, (t // cols) * 8 * s
        for y in range(8):
            p0, p1 = tile[y * 2], tile[y * 2 + 1]
            for x in range(8):
                b = 7 - x
                v = ((p0 >> b) & 1) | (((p1 >> b) & 1) << 1)
                g = palette[v]
                for dy in range(s):
                    for dx in range(s):
                        img.putpixel((cx + x * s + dx, cy + y * s + dy), (g, g, g))
    img.save(args.out)
    print(f'{ntiles} tiles -> {args.out} ({img.size[0]}x{img.size[1]})')

if __name__ == '__main__':
    main()
