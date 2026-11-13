"""Unit tests for decoding edge cases that the round-trip cannot catch."""

from tools.gb import disasm, sm83


def dec(data, off=0):
    ins = disasm.decode_one(bytes(data), off)
    return None if ins is None else (ins.size, ins.text, ins.kind)


def test_relative_operands_are_signed():
    size, text, _ = dec([0x18, 0xFE])
    assert (size, text) == (2, "jr -2")
    size, text, _ = dec([0x18, 0x10])
    assert text == "jr +16"


def test_ldh_is_rendered_as_full_address():
    _, text, _ = dec([0xE0, 0x40])
    assert text == "ldh [$ff40],a"
    _, text, _ = dec([0xF0, 0xFF])
    assert text == "ldh a,[$ffff]"


def test_cb_page():
    size, text, _ = dec([0xCB, 0x37])
    assert (size, text) == (2, "cb swap a")
    size, text, _ = dec([0xCB, 0x7E])
    assert text == "cb bit 7,[hl]"


def test_truncated_bank_returns_none():
    assert dec([0x3E]) is None          # ld a,d8 at end of bank
    assert dec([0x21, 0x00]) is None    # ld hl,d16 truncated
    assert dec([0xCB]) is None


def test_illegal_opcodes():
    ins = disasm.decode_one(bytes([0xD3]), 0)
    assert ins.text.startswith("db $d3")
    assert 0xD3 in sm83.INVALID


def test_stop_consumes_padding_byte():
    size, text, _ = dec([0x10, 0x00])
    assert (size, text) == (2, "stop")


def test_flow_classes():
    assert dec([0xC9])[2] == "end"
    assert dec([0xD9])[2] == "end"
    assert dec([0xC3, 0, 0x40])[2] == "jp"
    assert dec([0xE9])[2] == "jp"
    assert dec([0xCD, 0, 0x40])[2] == "call"
    assert dec([0xC0])[2] == "cret"
    assert dec([0xC2, 0, 0x40])[2] == "cjp"
    assert dec([0xFF])[2] == "rst"
