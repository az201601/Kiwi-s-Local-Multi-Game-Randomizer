import hashlib
import json
import unittest
from pathlib import Path

from tools.gb_lzss import decode_rom_stream, decode_stream


ROM = Path(__file__).resolve().parents[1] / "Harvest Moon GB (U) [C][!].gbc"


class HarvestMoonLzssTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rom = ROM.read_bytes()

    def test_cgb_boot_tile_stream_matches_runtime_vram(self):
        result = decode_rom_stream(self.rom, 0x12, 0x7A58)
        self.assertEqual(result.output_size, 0x0800)
        self.assertEqual(result.consumed, 1153)
        self.assertEqual(result.literals, 658)
        self.assertEqual(result.backreferences, 193)
        self.assertEqual(
            hashlib.sha256(result.data).hexdigest(),
            "e135d1e86c8293d887ca344e1120ec777ae24fcab1da8a7a95efed3aae7180ef",
        )

    def test_cgb_boot_tilemap_stream_matches_runtime_vram(self):
        result = decode_rom_stream(self.rom, 0x12, 0x7ED9)
        self.assertEqual(result.output_size, 0x0400)
        self.assertEqual(result.consumed, 227)
        self.assertEqual(result.literals, 98)
        self.assertEqual(result.backreferences, 54)
        self.assertEqual(
            hashlib.sha256(result.data).hexdigest(),
            "5e86604589c95c035cd59dd1535df0a8d7834e9eed6f0536858990026f1f3773",
        )

    def test_every_recorded_opening_trace_output_matches_offline_decoder(self):
        observations = Path(__file__).resolve().parents[1] / "reverse-engineering" / "observations"
        total = 0
        for mode in ("cgb", "dmg"):
            trace_path = observations / f"compression-trace-600-{mode}.json"
            trace = json.loads(trace_path.read_text(encoding="utf-8"))
            self.assertEqual(trace["mode"], mode)
            self.assertEqual(trace["frames"], 600)
            self.assertFalse(trace["incomplete_calls"])
            for call in trace["calls"]:
                if mode == "dmg":
                    self.assertIsNone(call["vram_bank_at_entry"])
                    self.assertIsNone(call["vram_bank_at_return"])
                with self.subTest(mode=mode, source_bank=call["source_bank"],
                                  source=call["source_address"], destination=call["destination_start"]):
                    result = decode_rom_stream(self.rom, call["source_bank"], call["source_address"])
                    self.assertEqual(result.output_size, call["declared_output_size"])
                    self.assertEqual(result.output_size, call["output_length"])
                    self.assertEqual(result.consumed, call["compressed_bytes_consumed"])
                    self.assertEqual(hashlib.sha256(result.data).hexdigest(), call["output_sha256"])
                    total += 1
        self.assertEqual(total, 19)

    def test_literals_backreferences_and_overlap(self):
        # LSB-first flags: three literals ('A', 'B', 'C'), then distance 3/length 3.
        stream = bytes((6, 0, 0b00000111, ord('A'), ord('B'), ord('C'), 0x30, 0x00))
        self.assertEqual(decode_stream(stream).data, b"ABCABC")

    def test_negative_backreference_prefix_is_zero_filled(self):
        # Literal A, then distance 2/length 3; at output offset 1 the first
        # reference is before the start, then the overlapping copy reaches A.
        stream = bytes((4, 0, 0b00000001, ord('A'), 0x20, 0x00))
        self.assertEqual(decode_stream(stream).data, bytes((ord('A'), 0, ord('A'), 0)))

    def test_zero_distance_requires_caller_memory_and_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "zero-distance"):
            decode_stream(bytes((3, 0, 0, 0, 0)))

    def test_truncated_stream_is_reported(self):
        with self.assertRaisesRegex(ValueError, "truncated"):
            decode_stream(bytes((1, 0, 1)))


if __name__ == "__main__":
    unittest.main()
