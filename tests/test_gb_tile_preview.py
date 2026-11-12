import unittest

from tools.gb_tile_preview import tile_pixels


class GameBoyTilePreviewTests(unittest.TestCase):
    def test_tile_bitplanes_are_read_most_significant_pixel_first(self):
        # Bit 7 of the low plane sets pixel zero to shade 1; bit 6 of the
        # high plane sets pixel one to shade 2.
        tile = bytes((0x80, 0x40)) + bytes(14)
        width, height, pixels = tile_pixels(tile, columns=1, scale=1)
        self.assertEqual((width, height), (8, 8))
        self.assertEqual(pixels[0:4], bytes((170, 170, 170, 255)))
        self.assertEqual(pixels[4:8], bytes((85, 85, 85, 255)))
        self.assertEqual(pixels[8:12], bytes((255, 255, 255, 255)))

    def test_atlas_dimensions_and_scaling(self):
        # Three tiles, two per row, rendered at 3x scale.
        width, height, pixels = tile_pixels(bytes(48), columns=2, scale=3)
        self.assertEqual((width, height), (48, 48))
        self.assertEqual(len(pixels), width * height * 4)

    def test_partial_tile_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "whole number of 16-byte tiles"):
            tile_pixels(bytes(15), columns=1, scale=1)


if __name__ == "__main__":
    unittest.main()
