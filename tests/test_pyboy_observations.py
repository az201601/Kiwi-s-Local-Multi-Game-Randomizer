import json
import unittest
from pathlib import Path


OBSERVATIONS = Path(__file__).resolve().parents[1] / "reverse-engineering" / "observations"


class PyBoyObservationTests(unittest.TestCase):
    def test_saved_rst_dispatch_modes_match_their_opening_paths(self):
        cgb = json.loads((OBSERVATIONS / "rst-dispatch-600-cgb.json").read_text(encoding="utf-8"))
        dmg = json.loads((OBSERVATIONS / "rst-dispatch-600-dmg.json").read_text(encoding="utf-8"))
        self.assertEqual((cgb["mode"], dmg["mode"]), ("cgb", "dmg"))
        self.assertEqual((cgb["frames"], dmg["frames"]), (600, 600))

        def rows(trace, rst_site):
            return [row for row in trace["dispatches"]["rst08"] if row["rst_site"] == rst_site]

        self.assertEqual(
            [(r["index"], r["target_bank"], r["target_address"]) for r in rows(cgb, "0x0296")],
            [("0x10", 29, "0x4001"), ("0x20", 29, "0x56EC")],
        )
        self.assertEqual(
            [(r["index"], r["target_bank"], r["target_address"]) for r in rows(dmg, "0x0296")],
            [("0x3A", 61, "0x4001"), ("0x4A", 61, "0x56A7")],
        )
        self.assertEqual(
            [(r["index"], r["target_bank"], r["target_address"]) for r in rows(cgb, "0x0575")],
            [("0x10", 29, "0x40A4"), ("0x20", 29, "0x57A5")],
        )
        self.assertEqual(
            [(r["index"], r["target_bank"], r["target_address"]) for r in rows(dmg, "0x0575")],
            [("0x3A", 61, "0x4097"), ("0x4A", 61, "0x5753")],
        )


if __name__ == "__main__":
    unittest.main()
