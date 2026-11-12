import unittest

from tools.gb_disasm import (
    ILLEGAL,
    decode,
    decode_cb,
    decode_at,
    read_cpu,
)


class LR35902DecoderTests(unittest.TestCase):
    def test_all_base_opcodes_are_classified_with_expected_lengths(self):
        three_byte = {
            0x01, 0x08, 0x11, 0x21, 0x31,
            0xC2, 0xC3, 0xC4, 0xCA, 0xCC, 0xCD,
            0xD2, 0xD4, 0xDA, 0xDC, 0xEA, 0xFA,
        }
        two_byte = {
            0x06, 0x0E, 0x10, 0x16, 0x18, 0x1E, 0x20, 0x26, 0x28,
            0x2E, 0x30, 0x36, 0x38, 0x3E,
            0xC6, 0xCB, 0xCE, 0xD6, 0xDE, 0xE0, 0xE6, 0xE8, 0xEE,
            0xF0, 0xF6, 0xF8, 0xFE,
        }
        for opcode in range(256):
            with self.subTest(opcode=f"{opcode:02X}"):
                size, text, _flow, _target, _conditional = decode(
                    opcode, 0x34, 0x12, 0x2000
                )
                expected_size = 3 if opcode in three_byte else 2 if opcode in two_byte else 1
                self.assertEqual(size, expected_size)
                self.assertTrue(text)
                if opcode in ILLEGAL:
                    self.assertIn("illegal", text)
                else:
                    self.assertFalse(text.startswith("DB "))

    def test_all_cb_opcodes_have_mnemonics(self):
        for opcode in range(256):
            with self.subTest(opcode=f"CB {opcode:02X}"):
                self.assertTrue(decode_cb(opcode))

    def test_rst_inline_dispatch_is_not_treated_as_normal_fallthrough(self):
        self.assertEqual(decode(0xC7, 0, 0, 0x0100)[2], "dispatch")  # RST $00
        self.assertEqual(decode(0xCF, 0, 0, 0x0100)[2], "dispatch")  # RST $08
        self.assertEqual(decode(0xD7, 0, 0, 0x0100)[2], "call")      # RST $10

    def test_bootstrap_bytes_decode_as_expected(self):
        self.assertEqual(decode(0xCD, 0x82, 0x22, 0x0169)[1], "CALL $2282")
        self.assertEqual(decode(0x20, 0xF9, 0x00, 0x2287)[1], "JR NZ,$2282")
        self.assertEqual(decode(0xEA, 0xF7, 0xDF, 0x0154)[1], "LD ($DFF7),A")
        self.assertEqual(decode(0xAF, 0x00, 0x00, 0x0157)[1], "XOR A")
        self.assertEqual(decode_cb(0x7E), "BIT 7,(HL)")

    def test_cpu_rom_mapping_is_bank_aware(self):
        rom = bytes([0x00]) * 0x4000 + bytes([0x01]) * 0x4000 + bytes([0x02]) * 0x4000
        self.assertEqual(read_cpu(rom, 2, 0x3FFF), (0x00, 0x3FFF))
        self.assertEqual(read_cpu(rom, 2, 0x4000), (0x02, 0x8000))
        self.assertEqual(read_cpu(rom, 1, 0x4000), (0x01, 0x4000))

    def test_cb_prefix_consumes_two_bytes(self):
        rom = bytearray(0x4000)
        rom[0x0100:0x0102] = bytes((0xCB, 0x7E))
        instruction, offset = decode_at(bytes(rom), 0, 0x0100)
        self.assertEqual(offset, 0x0100)
        self.assertEqual(instruction.raw, bytes((0xCB, 0x7E)))
        self.assertEqual(instruction.size, 2)
        self.assertEqual(instruction.text, "BIT 7,(HL)")


if __name__ == "__main__":
    unittest.main()
