import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from randomizer.harvest_moon_gbc import (
    GLOBAL_CHECKSUM,
    PAYOUT_OFFSETS,
    RECEIPT_OFFSETS,
    STARTING_MONEY_OFFSETS,
    SUPPORTED_SHA256,
    patch_rainy_day_money,
    roll_rainy_day_money,
    roll_starting_money,
)

ROOT = Path(__file__).resolve().parents[1]
ROM = ROOT / "Harvest Moon GB (USA) (SGB Enhanced).gbc"


class RollTests(unittest.TestCase):
    def test_seed_is_reproducible_with_results_in_range(self):
        rolls = [roll_rainy_day_money(f"test-{i}") for i in range(200)]
        self.assertEqual(rolls, [roll_rainy_day_money(f"test-{i}") for i in range(200)])
        self.assertTrue(all(1 <= amount <= 1000 for amount in rolls))
        self.assertGreater(len(set(rolls)), 1)

    def test_starting_money_is_reproducible_and_in_range(self):
        self.assertEqual(roll_starting_money("kiwi-demo"), 377)
        rolls = [roll_starting_money(str(i)) for i in range(200)]
        self.assertTrue(all(1 <= amount <= 500 for amount in rolls))
        self.assertGreater(len(set(rolls)), 1)
        for amount in (0, 501, True):
            with self.subTest(amount=amount), self.assertRaisesRegex(ValueError, "1 to 500G"):
                patch_rainy_day_money(b"", 945, amount)

    def test_rejects_unsupported_rom(self):
        with self.assertRaisesRegex(ValueError, "unsupported ROM"):
            patch_rainy_day_money(b"wrong ROM", 1)

    def test_rejects_invalid_amounts(self):
        for amount in (0, 1001, 3.5, True):
            with self.subTest(amount=amount), self.assertRaisesRegex(ValueError, "1 to 1000G"):
                patch_rainy_day_money(b"", amount)


@unittest.skipUnless(ROM.is_file(), "verified Harvest Moon ROM is not available")
class PatchTests(unittest.TestCase):
    def test_patch_locations_and_checksums(self):
        source = ROM.read_bytes()
        allowed_changes = set(range(GLOBAL_CHECKSUM.start, GLOBAL_CHECKSUM.stop))
        for start, length in [(i, 2) for i in PAYOUT_OFFSETS] + [(i, 7) for i in RECEIPT_OFFSETS]:
            allowed_changes.update(range(start, start + length))
        for amount, glyphs in ((1, "35064040efefef"), (73, "3b37064040efef"),
                               (945, "3d3839064040ef"), (1000, "35343434064040")):
            with self.subTest(amount=amount):
                patched = patch_rainy_day_money(source, amount)
                self.assertEqual(len(patched), len(source))
                for offset in PAYOUT_OFFSETS:
                    self.assertEqual(int.from_bytes(patched[offset:offset + 2], "little"), amount)
                for offset in RECEIPT_OFFSETS:
                    self.assertEqual(patched[offset:offset + 7], bytes.fromhex(glyphs))
                changed = {i for i, (old, new) in enumerate(zip(source, patched)) if old != new}
                self.assertTrue(changed <= allowed_changes)
                checksum = sum(patched[:0x14E] + patched[0x150:]) & 0xFFFF
                self.assertEqual(patched[GLOBAL_CHECKSUM], checksum.to_bytes(2, "big"))
                if amount == 1000:
                    self.assertEqual(patched, source)

    def test_starting_money_patches_both_modes_and_leaves_drawer_alone(self):
        source = ROM.read_bytes()
        default = patch_rainy_day_money(source, 945)
        self.assertEqual(default, patch_rainy_day_money(source, 945, 500))
        allowed = {offset for pair in STARTING_MONEY_OFFSETS for offset in pair} | {0x14E, 0x14F}
        for amount in (1, 377, 500):
            patched = patch_rainy_day_money(source, 945, amount)
            for low, high in STARTING_MONEY_OFFSETS:
                self.assertEqual(patched[low] + (patched[high] << 8), amount)
            changed = {i for i, (old, new) in enumerate(zip(default, patched)) if old != new}
            self.assertTrue(changed <= allowed)
            self.assertEqual(int.from_bytes(patched[GLOBAL_CHECKSUM], "big"),
                             sum(patched[:0x14E] + patched[0x150:]) & 0xFFFF)

    def test_cli_reproduces_output_and_does_not_overwrite(self):
        with tempfile.TemporaryDirectory() as tmp:
            first, second = [Path(tmp) / f"{i}.gbc" for i in range(2)]
            cli = [sys.executable, "-m", "randomizer.harvest_moon_gbc", str(ROM)]
            for output in (first, second):
                result = subprocess.run(cli + [str(output), "--seed", "kiwi-demo"], cwd=ROOT,
                                        check=True, capture_output=True, text=True)
                self.assertIn("Seed: 'kiwi-demo'", result.stdout)
                self.assertIn(f"{roll_rainy_day_money('kiwi-demo')}G", result.stdout)
            expected = first.read_bytes()
            self.assertEqual(expected, second.read_bytes())
            result = subprocess.run(cli + [str(first), "--seed", "other"], cwd=ROOT,
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(first.read_bytes(), expected)
            self.assertEqual(hashlib.sha256(ROM.read_bytes()).hexdigest(), SUPPORTED_SHA256)
