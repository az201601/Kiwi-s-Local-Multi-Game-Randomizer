"""Build a standalone mixed-counter artwork prototype from the supported USA ROM.

Run from the repository root: python -m tools.shop_art_prototype INPUT OUTPUT
This is a fixed swap for exercising the renderer, not the seeded randomizer.
"""

import argparse
from dataclasses import dataclass
import hashlib
from pathlib import Path

from randomizer.crop_graphics import _decode
from randomizer.harvest_moon_gbc import SUPPORTED_SHA256


@dataclass(frozen=True)
class Mode:
    hooks: tuple[tuple[int, ...], tuple[int, ...]]
    space: int
    seeds: int
    drinks: int
    seed_art: int
    drink_art: int
    setup: int
    append: int
    apple_gate: int


MODES = (
    Mode(((0x248A5, 0x24907), (0x24D05, 0x24D67)), 0x27A00, 0x2495D, 0x24DBB,
         0x717AE, 0x718A4, 0x4B09, 0x49AE, 0x4DE0),
    Mode(((0xA4888, 0xA48EA), (0xA4CE4, 0xA4D46)), 0xA7A00, 0xA4940, 0xA4D9A,
         0xF1936, 0xF1A8D, 0x4AEC, 0x4991, 0x4DBF),
)
TURNIP, POTATO, TOMATO, CORN, EGGPLANT, PEANUT, CARROT, BROCCOLI, GRASS = range(9)
ORANGE, APPLE, GRAPE, TEA, MILK = range(9, 14)
# Keep every item's native checks. Swap Turnip/Potato for Apple/Milk.
COUNTERS = ((APPLE, MILK, TOMATO, CORN, EGGPLANT, PEANUT, CARROT, BROCCOLI, GRASS),
            (ORANGE, TURNIP, POTATO, GRAPE, TEA))
ATLAS_OFFSET = 0x200  # page-aligned within bank; each icon is four 16-byte tiles


def _word(value: int) -> bytes:
    return value.to_bytes(2, 'little')


def _addr(offset: int) -> int:
    return 0x4000 + offset % 0x4000


def _catalog(rom: bytes, mode: Mode) -> tuple[bytes, bytes]:
    """Read native purchase records and join the two shop-local icon banks."""
    seed_art = _decode(rom, mode.seed_art)
    drink_art = _decode(rom, mode.drink_art)
    assert len(seed_art) == 9 * 64 and len(drink_art) == 5 * 64
    seeds = [rom[mode.seeds + i * 9:mode.seeds + (i + 1) * 9] for i in range(9)]
    drinks = [b'\0' * 4 + rom[mode.drinks + i * 5:mode.drinks + (i + 1) * 5] for i in range(2)]
    drinks.extend(rom[mode.drinks + 10 + i * 9:mode.drinks + 19 + i * 9] for i in range(3))
    records = bytearray()
    atlas = bytearray()
    for item, record in enumerate(seeds + drinks):
        tiles = seed_art if item < ORANGE else drink_art
        start = (record[-1] - 0x50) * 16
        assert start >= 0 and start + 64 <= len(tiles)
        atlas.extend(tiles[start:start + 64])
        # During stock assembly the last byte carries an atlas item ID. The
        # renderer converts it to the actual tile number before native drawing.
        records.extend(record[:-1] + bytes([item]))
    return bytes(records), bytes(atlas)


def _upload(atlas: int) -> bytes:
    # ld a,[$CD33]; and a; ret z; ld b,a; ld c,$50; ld hl,$CD38; ld de,$9500
    # row: swap item ID for tile, multiply ID by 64, and select the atlas page
    # copy: wait for STAT mode 0/1 with IRQs on, then recheck with IRQs masked
    # so neither the PPU nor an interrupt can take VRAM away mid-transfer.
    # This runs in the main menu script, not an interrupt handler.
    # advance the five-byte menu record and four-tile slot; loop until count=0
    return (bytes.fromhex('fa33cd a7 c8 47 0e50 2138cd 110095 7e 71 e5 c5 0f 0f 6f e603 c6')
            + bytes((atlas >> 8,))
            + bytes.fromhex('67 7d e6c0 6f 0640 '
                            'f041 e602 20fa f3 f041 e602 2009 2a12 fb 13 05 20ec 1803 fb 18e7 '
                            'c1 e1 7d c605 6f 0c0c0c0c 05 20c8 c9'))


def build(rom: bytes) -> bytes:
    if hashlib.sha256(rom).hexdigest() != SUPPORTED_SHA256:
        raise ValueError('Use the unmodified supported USA ROM as the input.')
    patched = bytearray(rom)
    for mode in MODES:
        records, atlas = _catalog(rom, mode)
        origin = _addr(mode.space)
        atlas_addr = origin + ATLAS_OFFSET
        upload = _upload(atlas_addr)
        record_addr = origin + len(upload)
        code = bytearray(upload + records)
        for hooks, stock in zip(mode.hooks, COUNTERS):
            entry = origin + len(code)
            for hook in hooks:
                patched[hook:hook + 2] = _word(entry)
            # Save script cursor, initialize blank tiles and clear the visible count.
            code += b'\xc5\xcd' + _word(mode.setup) + bytes.fromhex('af ea33cd')
            for item in stock:
                if item == APPLE:
                    # Carry from the vanilla date check: the item is still locked.
                    code += b'\xcd' + _word(mode.apple_gate) + b'\x38\x06'
                elif item == MILK:
                    code += bytes.fromhex('fa42b9 b7 2806')
                immediate = item in (APPLE, ORANGE)
                record = record_addr + item * 9 + (4 if immediate else 0)
                append = mode.append + (18 if immediate else 0)
                code += b'\x21' + _word(record) + b'\xcd' + _word(append)
            code += b'\xcd' + _word(origin) + b'\xe1\xc9'
        assert len(code) <= ATLAS_OFFSET
        start = mode.space
        end = start + ATLAS_OFFSET + len(atlas)
        assert all(value == 0xFF for value in rom[start:end])
        patched[start:start + len(code)] = code
        patched[start + ATLAS_OFFSET:end] = atlas
    patched[0x14E:0x150] = ((sum(patched) - sum(patched[0x14E:0x150])) & 0xFFFF).to_bytes(2, 'big')
    return bytes(patched)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    try:
        result = build(args.input.read_bytes())
        # Never replace the source ROM or an existing output.
        with args.output.open('xb') as output:
            output.write(result)
    except (OSError, ValueError) as error:
        parser.exit(1, f'{error}\n')


if __name__ == '__main__':
    main()
