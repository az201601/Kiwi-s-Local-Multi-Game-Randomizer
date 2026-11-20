import hashlib
from pathlib import Path
import unittest

from randomizer import crop_graphics
from randomizer.harvest_moon_gbc import patch_rainy_day_money
from randomizer.seed_shop import EMPTY, GRASS, GROWTH_CHECKS, INIT_OFFSETS, VANILLA, inventory

ROM = Path(__file__).resolve().parents[1] / "Harvest Moon GB (USA) (SGB Enhanced).gbc"


class SeedShopTests(unittest.TestCase):
    def test_all_crops_exactly_once_and_grass_in_three_seasons(self):
        for i in range(100):
            seed = f"inventory-{i}"
            stock = inventory(seed, True, False)
            shuffled = inventory(seed, True, True)
            self.assertEqual(sorted(crop for row in shuffled for crop in row if crop < GRASS), list(range(GRASS)))
            for season, row in enumerate(shuffled):
                self.assertEqual(sorted(row), sorted(stock[season]))
                self.assertEqual(row.count(GRASS), int(season < 3))
            self.assertEqual(shuffled[3][2], EMPTY)
            self.assertEqual([row[2] for row in stock], [GRASS, GRASS, GRASS, EMPTY])
        self.assertEqual(inventory("nina-2", True, True),
                         [[3, 4, 8], [0, 8, 5], [8, 1, 7], [2, 6, EMPTY]])

    def test_slots_only_does_not_change_assigned_seeds(self):
        self.assertEqual(inventory("nina-2", False, False), [list(row) for row in VANILLA])
        shuffled = inventory("nina-2", False, True)
        self.assertNotEqual(shuffled, [list(row) for row in VANILLA])
        self.assertEqual([sorted(row) for row in shuffled], [sorted(row) for row in VANILLA])

    @unittest.skipUnless(ROM.is_file(), "verified Harvest Moon ROM is not available")
    def test_patch_both_rom_modes_and_preserve_name_filter_for_slots_only(self):
        original = ROM.read_bytes()
        self.assertEqual(patch_rainy_day_money(original, 1000, seed="nina-2"), original)
        for stock, slots in ((True, False), (False, True), (True, True)):
            patched = patch_rainy_day_money(original, 1000, seed="nina-2",
                                            randomize_seed_stock=stock, shuffle_seed_slots=slots)
            rows = bytes(entry for row in inventory("nina-2", stock, slots) for entry in row)
            allowed = {0x14E, 0x14F}
            for start in INIT_OFFSETS:
                self.assertEqual(patched[start + 52 : start + 64], rows)
                allowed.update(range(start, start + 64))
                for item in range(GRASS + 1):
                    masks = start + 64 + item * 9 + 2
                    if stock and item < GRASS:
                        self.assertEqual(patched[masks : masks + 2], b"\x0f\x1f")
                        allowed.update((masks, masks + 1))
                    else:
                        self.assertEqual(patched[masks : masks + 2], original[masks : masks + 2])
            for checks in GROWTH_CHECKS:
                for season, row in enumerate(inventory("nina-2", stock, slots)):
                    for crop in row:
                        if crop >= GRASS:
                            continue
                        season_check, weather_check = checks[crop]
                        self.assertEqual(original[season_check - 4:season_check], bytes.fromhex("fa84b8fe"))
                        self.assertEqual(original[weather_check - 4:weather_check], bytes.fromhex("faa0b8fe"))
                        if stock:
                            self.assertEqual(patched[season_check], season)
                            self.assertEqual(patched[weather_check], 2 if season == 3 else 1)
                            allowed.update((season_check, weather_check))
                        else:
                            self.assertEqual(patched[season_check], original[season_check])
                            self.assertEqual(patched[weather_check], original[weather_check])
            if stock:
                # Independently attribute changes to the graphics patch; no other
                # bytes apart from the listed shop/growth/checksum patches may differ.
                art_only = bytearray(original)
                crop_graphics.patch(art_only, inventory("nina-2", stock, slots))
                allowed.update(i for i, (old, new) in enumerate(zip(original, art_only)) if old != new)
            changed = {i for i, (old, new) in enumerate(zip(original, patched)) if old != new}
            self.assertTrue(changed <= allowed)
            self.assertEqual(int.from_bytes(patched[0x14E:0x150], "big"),
                             sum(patched[:0x14E] + patched[0x150:]) & 0xFFFF)
        both = patch_rainy_day_money(original, 945, 377, seed="kiwi-demo",
                                    randomize_seed_stock=True, shuffle_seed_slots=True)
        self.assertEqual(hashlib.sha256(both).hexdigest(),
                         "20065739b291e82c4d5032a3954c08064f9ce5f00160fc3bbc528a7dab80d990")
