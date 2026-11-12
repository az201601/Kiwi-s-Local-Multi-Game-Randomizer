#!/usr/bin/env python3
"""Render a decoded stream as a grayscale Game Boy 2bpp tile atlas.

This is a byte-layout viewer, not a palette-aware game renderer. It is useful
for classifying tile patterns; it does not apply tile maps, attributes, or
CGB palettes.
"""

from __future__ import annotations

import argparse
import struct
import zlib
from pathlib import Path

try:
    from .gb_lzss import decode_rom_stream
except ImportError:  # Allow direct execution as `python tools/gb_tile_preview.py`.
    from gb_lzss import decode_rom_stream

# Shade order for the two Game Boy tile bitplanes, from color index 0 to 3.
PALETTE = ((255, 255, 255, 255), (170, 170, 170, 255),
           (85, 85, 85, 255), (0, 0, 0, 255))


def write_rgba_png(path: Path, width: int, height: int, pixels: bytes) -> None:
    if len(pixels) != width * height * 4:
        raise ValueError("pixel buffer length does not match PNG dimensions")
    raw = b"".join(
        b"\x00" + pixels[y * width * 4:(y + 1) * width * 4]
        for y in range(height)
    )

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def tile_pixels(data: bytes, columns: int, scale: int) -> tuple[int, int, bytes]:
    if len(data) % 16:
        raise ValueError(f"decoded stream has {len(data)} bytes, not a whole number of 16-byte tiles")
    if columns <= 0 or scale <= 0:
        raise ValueError("columns and scale must be positive")
    tile_count = len(data) // 16
    rows = (tile_count + columns - 1) // columns
    width, height = columns * 8 * scale, rows * 8 * scale
    pixels = bytearray(width * height * 4)

    for tile_index in range(tile_count):
        tile_x = tile_index % columns
        tile_y = tile_index // columns
        tile = data[tile_index * 16:(tile_index + 1) * 16]
        for y in range(8):
            low, high = tile[2 * y], tile[2 * y + 1]
            for x in range(8):
                bit = 7 - x
                color_index = ((high >> bit) & 1) * 2 + ((low >> bit) & 1)
                rgba = PALETTE[color_index]
                for dy in range(scale):
                    row = (tile_y * 8 + y) * scale + dy
                    for dx in range(scale):
                        column = (tile_x * 8 + x) * scale + dx
                        offset = (row * width + column) * 4
                        pixels[offset:offset + 4] = bytes(rgba)
    return width, height, bytes(pixels)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="local Game Boy ROM")
    parser.add_argument("--bank", type=lambda value: int(value, 0), required=True,
                        help="source physical ROM bank, e.g. 0x12")
    parser.add_argument("--address", type=lambda value: int(value, 0), required=True,
                        help="CPU address of compressed stream, e.g. 0x7A58")
    parser.add_argument("--output", type=Path, required=True, help="PNG output path")
    parser.add_argument("--columns", type=int, default=16, help="tiles per row (default: 16)")
    parser.add_argument("--scale", type=int, default=2, help="integer pixel scale (default: 2)")
    args = parser.parse_args()

    result = decode_rom_stream(args.rom.read_bytes(), args.bank, args.address)
    width, height, pixels = tile_pixels(result.data, args.columns, args.scale)
    write_rgba_png(args.output, width, height, pixels)
    print(f"source bank ${args.bank:02X}:${args.address:04X}: {result.output_size} decoded bytes")
    print(f"rendered {result.output_size // 16} tiles in a {width}x{height} grayscale atlas: {args.output}")
    print("Note: tile maps, attributes, and CGB palettes are not applied.")


if __name__ == "__main__":
    main()
