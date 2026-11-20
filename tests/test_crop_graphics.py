from itertools import combinations
from pathlib import Path
import unittest

from randomizer import crop_graphics as gfx

ROM = Path(__file__).resolve().parents[1] / "Harvest Moon GB (USA) (SGB Enhanced).gbc"


def pixels(bitmap):
    for low, high in zip(bitmap[::2], bitmap[1::2]):
        for bit in range(8):
            yield (low >> bit & 1) + 2 * (high >> bit & 1)


class GraphicsDecoderTests(unittest.TestCase):
    def test_reference_can_cross_from_padding_into_decoded_data(self):
        # Two literal bytes, then copy five bytes starting one byte before them.
        self.assertEqual(gfx._decode(bytes.fromhex('07 00 03 a5 5a 32 00'), 0),
                         bytes.fromhex('a5 5a 00 a5 5a 00 a5'))


@unittest.skipUnless(ROM.is_file(), "verified Harvest Moon ROM is not available")
class CropGraphicsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = ROM.read_bytes()
        cls.pictures = [[gfx._decode(cls.original, lo) + gfx._decode(cls.original, hi)
                         for lo, hi in zip(streams[::2], streams[1::2])] for streams in gfx.ART]

    def test_every_pair_uses_original_art_including_colliding_tiles(self):
        for pair in combinations(range(8), 2):
            with self.subTest(pair=pair):
                other = [crop for crop in range(8) if crop not in pair]
                rows = [pair, *[other[i : i + 2] for i in range(0, 6, 2)]]
                patched = bytearray(self.original)
                gfx.patch(patched, rows)
                for base in gfx.FARM_COLORS:
                    for season, crops in enumerate(rows):
                        start = base + season * 64
                        palette = start + 56
                        ripe = sorted(set(crops) & {2, 4, 6})
                        self.assertEqual(patched[start:palette], self.original[start:palette])
                        expected = bytearray(self.original[palette:palette + 8])
                        if ripe:
                            expected[:2] = self.original[start + 6:start + 8]
                            first_fruit = base + (ripe[0] // 2) * 64 + 62
                            expected[6:] = self.original[first_fruit:first_fruit + 2]
                            if len(ripe) == 2:
                                second_fruit = base + (ripe[1] // 2) * 64 + 62
                                expected[4:6] = self.original[second_fruit:second_fruit + 2]
                        self.assertEqual(patched[palette:palette + 8], expected)
                for mode, (live, scroll, hook, trampoline, free) in enumerate(gfx.MODES):
                    self.assertEqual(patched[hook : hook + 3], b"\xcd" + gfx._cpu_address(trampoline))
                    self.assertEqual(patched[trampoline : trampoline + 3], b"\x21" + gfx._cpu_address(free))
                    self.assertEqual(patched[free + 14], 0x2A)  # beginning of the tile-copy loop
                    bank = free & ~0x3FFF

                    def rom_pointer(pos):
                        return bank + int.from_bytes(patched[pos : pos + 2], "little") - 0x4000

                    table = rom_pointer(free + 8)
                    self.assertEqual(table, free + 39)
                    for season, crops in enumerate(rows):
                        start = pos = rom_pointer(table + season * 2)
                        tiles = {}
                        while patched[pos] != 0:
                            self.assertNotIn(patched[pos], tiles)
                            tiles[patched[pos]] = patched[pos + 1 : pos + 17]
                            pos += 17
                            self.assertLess(pos, bank + 0x4000)
                        self.assertEqual(self.original[start : pos + 1], b"\xff" * (pos + 1 - start))
                        used = set()
                        ripe = sorted(set(crops) & {2, 4, 6})
                        for crop in crops:
                            native = self.pictures[mode][crop // 2]
                            first, count = gfx.STAGES[crop]
                            for i in range(first, first + count):
                                offset = live + i * 8
                                tile_id = self.original[offset]
                                orig_meta = self.original[offset : offset + 8]
                                new_meta = patched[offset : offset + 8]
                                self.assertEqual(orig_meta[:2] + orig_meta[6:], new_meta[:2] + new_meta[6:])
                                m = scroll + tile_id * 4
                                self.assertEqual(patched[m : m + 4], new_meta[2:6])
                                for src, dst in zip(orig_meta[2:6], new_meta[2:6]):
                                    if src in gfx.SOIL:
                                        self.assertEqual(src, dst)
                                        continue
                                    art = native[16 * src : 16 * (src + 1)]
                                    native_color = self.original[gfx.PALETTE + src]
                                    if mode == 0 and native_color == 7 and len(ripe) == 2:
                                        index_map = (0, 1, 1, 2 if crop == ripe[1] else 3)
                                        for source, loaded in zip(pixels(art), pixels(tiles[dst])):
                                            self.assertEqual(loaded, index_map[source])
                                    else:
                                        self.assertEqual(tiles[dst], art)
                                    color = (gfx.EXTRA_COLORS[dst - gfx.EXTRA_TILES.start] if dst in gfx.EXTRA_TILES
                                             else self.original[gfx.PALETTE + dst])
                                    self.assertEqual(color, 5 if mode == 0 and crop == 0 and native_color == 7
                                                     else native_color)
                                    used.add(dst)
                        self.assertEqual(used, set(tiles))


if __name__ == "__main__":
    unittest.main()
