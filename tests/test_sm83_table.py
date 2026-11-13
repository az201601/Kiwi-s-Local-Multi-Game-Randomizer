"""Validate the opcode table against an independent reference.

The reference below is transcribed from the classic 256-entry opcode length
table (Pan Docs / GBDev "opcode table"), not generated from ``sm83.py``.  It
is deliberately a *different* representation (one hex digit per opcode), so a
mistake in one cannot be mirrored in the other.

0 = 1 byte, 1 = 2 bytes, 2 = 3 bytes.
"""

import pytest

from tools.gb import sm83

# fmt: off
_ROWS = (
    # 0x00                                                            0x0f
    "0" "2" "0" "0" "0" "0" "1" "0"  "2" "0" "0" "0" "0" "0" "1" "0"
    "1" "2" "0" "0" "0" "0" "1" "0"  "1" "0" "0" "0" "0" "0" "1" "0"
    "1" "2" "0" "0" "0" "0" "1" "0"  "1" "0" "0" "0" "0" "0" "1" "0"
    "1" "2" "0" "0" "0" "0" "1" "0"  "1" "0" "0" "0" "0" "0" "1" "0"
    # 0x40-0x7f: ld r,r' block, all single byte
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    # 0x80-0xbf: alu a,r block, all single byte
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    "0" "0" "0" "0" "0" "0" "0" "0"  "0" "0" "0" "0" "0" "0" "0" "0"
    # 0xc0-0xff
    "0" "0" "2" "2" "2" "0" "1" "0"  "0" "0" "2" "1" "2" "2" "1" "0"
    "0" "0" "2" "0" "2" "0" "1" "0"  "0" "0" "2" "0" "2" "0" "1" "0"
    "1" "0" "0" "0" "0" "0" "1" "0"  "1" "0" "2" "0" "0" "0" "1" "0"
    "1" "0" "0" "0" "0" "0" "1" "0"  "1" "0" "2" "0" "0" "0" "1" "0"
)
REF_LENGTHS = "".join(_ROWS)

# fmt: on


def test_reference_length_table_is_256_entries():
    assert len(REF_LENGTHS) == 256


def test_lengths_match_reference():
    bad = []
    for op in range(256):
        length = sm83.OPCODES[op][2]
        # the reference stores length-1 in a single digit
        if length - 1 != int(REF_LENGTHS[op]):
            bad.append((op, length, int(REF_LENGTHS[op]) + 1))
    assert not bad, f"length mismatches (op, table, reference): {bad}"


def test_every_opcode_present_and_valid_mnemonic():
    assert len(sm83.OPCODES) == 256
    assert len(sm83.CB_OPCODES) == 256
    for op, (mnem, kind, length, cycles, flags) in sm83.OPCODES.items():
        assert length in (1, 2, 3)
        assert cycles > 0
        assert len(flags) in (5, 8), f"{op:02x} flags {flags!r}"
        if mnem is not None and "{}" in mnem:
            assert kind != sm83.NONE, f"{op:02x} has placeholder but no operand"
        if kind != sm83.NONE and mnem is not None:
            assert "{}" in mnem, f"{op:02x} has operand but no placeholder"


@pytest.mark.parametrize("op", sorted(sm83.INVALID))
def test_illegal_opcodes_are_marked(op):
    assert sm83.OPCODES[op][0] is None


def test_control_flow_sets_are_consistent():
    for op in sm83.END_OPS | sm83.COND_JP_OPS | sm83.UNCOND_JP_OPS | sm83.CALL_OPS:
        assert sm83.OPCODES[op][2] == 3 or op in (0xC9, 0xD9, 0xE9)
    for op in sm83.RST_OPS:
        text = sm83.OPCODES[op][0]
        assert text.startswith("rst $")
        vector = int(text[5:], 16)
        assert vector % 8 == 0
