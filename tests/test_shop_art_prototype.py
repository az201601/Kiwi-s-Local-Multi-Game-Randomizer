from pathlib import Path
import unittest

from tools import shop_art_prototype as art

ROM = Path(__file__).resolve().parents[1] / 'Harvest Moon GB (USA) (SGB Enhanced).gbc'


class ShopArtPrototypeTests(unittest.TestCase):
    def test_catalog_items_are_assigned_to_exactly_one_counter(self):
        self.assertEqual(sorted(item for counter in art.COUNTERS for item in counter), list(range(14)))
        nina, eve = art.COUNTERS
        self.assertIn(art.APPLE, nina)
        self.assertIn(art.MILK, nina)
        self.assertIn(art.TURNIP, eve)
        self.assertIn(art.POTATO, eve)

    def test_rejects_unknown_input(self):
        with self.assertRaises(ValueError):
            art.build(b'not the supported ROM')

    @unittest.skipUnless(ROM.is_file(), 'verified Harvest Moon ROM is not available')
    def test_patches_only_counter_dispatch_and_atlas_space_in_both_modes(self):
        rom = ROM.read_bytes()
        patched = art.build(rom)
        self.assertEqual(len(patched), len(rom))
        self.assertEqual(int.from_bytes(patched[0x14E:0x150], 'big'),
                         sum(patched[:0x14E] + patched[0x150:]) & 0xFFFF)
        touched = {0x14E, 0x14F}
        for mode in art.MODES:
            records, atlas = art._catalog(rom, mode)
            atlas_start = mode.space + art.ATLAS_OFFSET
            self.assertEqual(patched[atlas_start:atlas_start + 14 * 64], atlas)
            self.assertTrue(all(any(atlas[i:i + 64]) for i in range(0, len(atlas), 64)))
            touched.update(range(mode.space, atlas_start + len(atlas)))
            self.assertEqual(records[:18], rom[mode.seeds:mode.seeds + 8] + bytes([art.TURNIP])
                             + rom[mode.seeds + 9:mode.seeds + 17] + bytes([art.POTATO]))
            for hooks in mode.hooks:
                entries = set()
                for hook in hooks:
                    touched.update((hook, hook + 1))
                    entries.add(int.from_bytes(patched[hook:hook + 2], 'little'))
                    self.assertNotEqual(patched[hook:hook + 2], rom[hook:hook + 2])
                self.assertEqual(len(entries), 1)
                self.assertTrue(art._addr(mode.space) <= entries.pop() < art._addr(atlas_start))
        self.assertFalse(any(i not in touched and old != new for i, (old, new) in enumerate(zip(rom, patched))))
