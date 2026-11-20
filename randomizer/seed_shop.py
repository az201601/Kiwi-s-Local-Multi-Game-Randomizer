"""Seeded Flower Shop inventories for the verified Harvest Moon GB ROM."""

import random

from . import crop_graphics

GRASS = 8
EMPTY = 0xFF
VANILLA = ((0, 1, GRASS), (2, 3, GRASS), (4, 5, GRASS), (6, 7, EMPTY))
SEED_PREFIX = "harvest-moon-gbc/seed-shop/"
INIT_OFFSETS = (0x2491D, 0xA4900)
# Immediate operands of cp after ld a, [$B884] (season) and ld a, [$B8A0]
# (weather) in the overnight crop handlers, in shop catalog order.
GROWTH_CHECKS = (
    ((0xAB43, 0xAB6E), (0xAB86, 0xABB1), (0xA9E2, 0xAA0D), (0xAAD8, 0xAB03),
     (0xABC9, 0xABF4), (0xAC0C, 0xAC37), (0xAC4F, 0xAC7A), (0xAC92, 0xACBD)),
    ((0x8AAA3, 0x8AACE), (0x8AAE6, 0x8AB11), (0x8A942, 0x8A96D), (0x8AA38, 0x8AA63),
     (0x8AB29, 0x8AB54), (0x8AB6C, 0x8AB97), (0x8ABAF, 0x8ABDA), (0x8ABF2, 0x8AC1D)),
)


def inventory(seed: str, randomize_stock: bool, shuffle_slots: bool) -> list[list[int]]:
    crops = list(range(GRASS))
    if randomize_stock:
        random.Random(f"{SEED_PREFIX}stock:{seed}").shuffle(crops)
    rows = [list(row) for row in VANILLA]
    for season, row in enumerate(rows):
        if randomize_stock:
            row[:2] = sorted(crops[season * 2 : season * 2 + 2])
        if shuffle_slots:
            count = 2 if row[2] == EMPTY else 3
            visible = row[:count]
            random.Random(f"{SEED_PREFIX}slots:{season}:{seed}").shuffle(visible)
            row[:count] = visible
    return rows


def patch(rom: bytearray, seed: str, randomize_stock: bool, shuffle_slots: bool) -> None:
    if not (randomize_stock or shuffle_slots):
        return
    inventory_rows = inventory(seed, randomize_stock, shuffle_slots)
    rows = bytes(crop for row in inventory_rows for crop in row)
    # See SeedShop.java for the annotated assembly of this 52-byte dispatcher.
    for start, setup, append in zip(INIT_OFFSETS, (0x4B09, 0x4AEC), (0x49AE, 0x4991)):
        origin = 0x4000 + start % 0x4000
        code = bytearray.fromhex(
            "c5 cd 00 00 af ea 33 cd fa 84 b8 4f 87 81 4f 06 00 21 00 00 09 06 03 "
            "2a fe ff 28 13 c5 e5 4f 87 87 87 81 5f 16 00 21 00 00 19 cd 00 00 e1 c1 05 20 e5 e1 c9"
        )
        for operand, value in ((2, setup), (18, origin + 52), (39, origin + 64), (43, append)):
            code[operand : operand + 2] = value.to_bytes(2, "little")
        rom[start : start + 64] = code + rows
        if randomize_stock:
            for crop in range(GRASS):
                masks = start + 64 + crop * 9 + 2
                rom[masks : masks + 2] = b"\x0f\x1f"
    if randomize_stock:
        crop_graphics.patch(rom, inventory_rows)
        for checks in GROWTH_CHECKS:
            for season, row in enumerate(inventory_rows):
                for crop in row:
                    if crop < GRASS:
                        season_check, weather_check = checks[crop]
                        rom[season_check] = season
                        rom[weather_check] = 2 if season == 3 else 1  # snow in Winter; rain otherwise
