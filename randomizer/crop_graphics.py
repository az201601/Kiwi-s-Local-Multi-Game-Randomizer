"""Give each stocked crop its original farm artwork, regardless of season."""

# Each crop's first record and number of stages, in shop catalog order.
STAGES = ((46, 9), (55, 13), (4, 17), (21, 25), (68, 9), (77, 13), (90, 13), (103, 17))
PALETTE = 0x1FD8
PALETTE_HOOKS = (0x1F9E, 0x1FC2)
PALETTE_HELPER = 0x3FE6
EXTRA_PALETTES = 0xE4
# 0x90..0x97 are unused farm background tiles in both graphics sets. The
# extra capacity handles two crops that normally occupy the same palette slots.
EXTRA_TILES = range(0x90, 0x98)
EXTRA_COLORS = bytes((5, 5, 5, 5, 7, 7, 7, 7))
SOIL = (0x02, 0x10, 0x11, 0x12)
# Per mode: live tool metatiles, map/scroll metatiles, farm loader hook,
# trampoline padding, and larger padding in a bank accessible to the loader.
MODES = (
    (0x22500, 0x46BA9, 0x40DA, 0x7FC1, 0x27597),
    (0xA2525, 0xC6BA9, 0x840A3, 0x87FF4, 0xA7567),
)
# Vanilla farm images: two LZ streams (tile IDs 00–7F and 80–FF) per season.
ART = (
    (0x2C001, 0x2C758, 0x2CC1A, 0x2D457, 0x2D9FA, 0x2E150, 0x2E612, 0x2EDC8),
    (0xAC001, 0xAC78A, 0xACC4E, 0xAD4C8, 0xADA75, 0xAE205, 0xAE6C9, 0xAEEC3),
)


def _decode(rom: bytes | bytearray, pos: int) -> bytes:
    """Decode the game's $31A0 LZ format (LSB-first, 1 = literal)."""
    size = int.from_bytes(rom[pos : pos + 2], "little")
    pos += 2
    output = bytearray()
    while len(output) < size:
        flags = rom[pos]
        pos += 1
        for bit in range(8):
            if flags & (1 << bit):
                output.append(rom[pos])
                pos += 1
            else:
                distance = (rom[pos] >> 4) | (rom[pos + 1] << 4)
                length = (rom[pos] & 15) + 3
                pos += 2
                if 0 < distance <= len(output):
                    for _ in range(length):
                        output.append(output[-distance])
                else:
                    output.extend(bytes(length))
            if len(output) == size:
                break
    return bytes(output)


def _cpu_address(offset: int) -> bytes:
    return (0x4000 + offset % 0x4000).to_bytes(2, "little")


def patch(rom: bytearray, rows: list[list[int]]) -> None:
    # See CropGraphics.java for the annotated LR35902 routines.
    rom[EXTRA_PALETTES : EXTRA_PALETTES + len(EXTRA_COLORS)] = EXTRA_COLORS
    helper = bytes.fromhex("f5 21 d8 1f d6 90 fe 08 30 0a fa a7 c0 fe 01 20 03 21 54 00 f1 c9")
    rom[PALETTE_HELPER : PALETTE_HELPER + len(helper)] = helper
    for offset in PALETTE_HOOKS:
        rom[offset : offset + 3] = b"\xcd" + PALETTE_HELPER.to_bytes(2, "little")

    for (live, scroll, hook, trampoline, free), streams in zip(MODES, ART):
        pictures = [_decode(rom, lo) + _decode(rom, hi) for lo, hi in zip(streams[::2], streams[1::2])]
        crop_tiles = [sorted({t for i in range(first, first + count)
                              for t in rom[live + i * 8 + 2 : live + i * 8 + 6]
                              if t not in SOIL}) for first, count in STAGES]
        slots = sorted(set().union(*crop_tiles, EXTRA_TILES))
        colors = dict(zip(EXTRA_TILES, EXTRA_COLORS))
        # Read season, index the four row pointers, then copy each listed tile to VRAM.
        code = bytearray.fromhex("fa 84 b8 87 4f 06 00 21 00 00 09 2a 66 6f 2a b7 c8 cb 37 5f e6 0f ee 08 c6 88 57 7b e6 f0 5f 01 10 00 cd ff 22 18 e7")
        code[8:10] = _cpu_address(free + len(code))
        table = len(code)
        code.extend(bytes(8))
        for season, row in enumerate(rows):
            code[table + season * 2 : table + season * 2 + 2] = _cpu_address(free + len(code))
            first, second = sorted(crop for crop in row if crop < len(STAGES))
            occupied = set(crop_tiles[first]) | set(crop_tiles[second])
            for crop in (first, second):
                remap = {}
                for tile in crop_tiles[crop]:
                    dest = tile
                    if crop == second and tile in crop_tiles[first]:
                        dest = next(t for t in slots if t not in occupied and
                                    colors.get(t, rom[PALETTE + t]) == rom[PALETTE + tile])
                        occupied.add(dest)
                    remap[tile] = dest
                    code.append(dest)
                    code.extend(pictures[crop // 2][tile * 16 : tile * 16 + 16])
                start, count = STAGES[crop]
                for i in range(start, start + count):
                    record = live + i * 8
                    tile = rom[record]
                    quad = bytes(remap.get(part, part) for part in rom[record + 2 : record + 6])
                    rom[record + 2 : record + 6] = quad
                    rom[scroll + tile * 4 : scroll + tile * 4 + 4] = quad
            code.append(0)
        rom[free : free + len(code)] = code
        rom[hook : hook + 3] = b"\xcd" + _cpu_address(trampoline)
        stub = b"\x21" + _cpu_address(free) + bytes((0x3E, free // 0x4000)) + bytes.fromhex("cd 7f 21 fa ea b8 c9")
        rom[trampoline : trampoline + len(stub)] = stub
