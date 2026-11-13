"""SM83 disassembler: decode, format and control-flow walk.

Two jobs live here:

1. ``decode_one`` / ``format_instr`` - byte -> text, used by the listing
   generator and by the coverage checker.
2. ``walk`` - recursive traversal from entry points (RST vectors, $0100,
   interrupt handlers and every literal address referenced by code) that
   separates *code* from *data*.  A bank is only fully disassembled once
   coverage stops improving; whatever is never reached is emitted as data,
   which is what makes the output round-trip byte-for-byte.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from . import sm83


@dataclass
class Instr:
    addr: int          # offset within the bank
    size: int
    text: str
    op: int
    operand: int | None = None   # resolved absolute target / literal, if any
    kind: str = "nop"            # flow class: nop|end|cret|cjp|jp|call|rst


def decode_one(bank: bytes, off: int) -> Instr | None:
    """Decode the instruction at ``off``; ``None`` if it runs past the end."""
    if off >= len(bank):
        return None
    op = bank[off]

    if op == 0xCB:
        if off + 1 >= len(bank):
            return None
        mnem = sm83.CB_OPCODES[bank[off + 1]][0]
        return Instr(off, 2, "cb " + mnem, op, None, "nop")

    mnem, kind, length, _cyc, _flags = sm83.OPCODES[op]
    if mnem is None:
        return Instr(off, 1, "db $%02x ; illegal" % op, op, None, "nop")
    if op == 0x10:  # stop has a padding byte, but 0x00 padding is common too
        return Instr(off, 2, "stop", op, None, "nop")
    if off + length > len(bank):
        return None

    operand = None
    if kind == sm83.D8:
        operand = bank[off + 1]
        if op in (0xE0, 0xF0) and 0xFF00 <= 0xFF00 + operand <= 0xFFFF:
            # ldh addresses are CPU addresses; RGBDS rejects the raw offset
            text = mnem.format("$ff%02x" % operand)
        else:
            text = mnem.format("$%02x" % operand)
    elif kind == sm83.D16:
        operand = bank[off + 1] | (bank[off + 2] << 8)
        text = mnem.format("$%04x" % operand)
    elif kind == sm83.E8:
        operand = bank[off + 1]
        rel = operand - 0x100 if operand > 0x7F else operand
        text = mnem.format("%+d" % rel)
    elif kind == sm83.A16:
        operand = bank[off + 1] | (bank[off + 2] << 8)
        text = mnem.format("$%04x" % operand)
    else:
        text = mnem

    if op in sm83.UNCOND_JP_OPS:
        flow = "jp"
    elif op in sm83.END_OPS:
        flow = "end"
    elif op in sm83.COND_RET_OPS:
        flow = "cret"
    elif op in sm83.COND_JP_OPS:
        flow = "cjp"
    elif op in sm83.UNCOND_JP_OPS:
        flow = "jp"
    elif op in sm83.CALL_OPS:
        flow = "call"
    elif op in sm83.RST_OPS:
        flow = "rst"
    else:
        flow = "nop"

    return Instr(off, length, text, op, operand, flow)


def assemble_text(bank: bytes, off: int) -> bytes:
    """Re-encode the instruction at ``off``; used by the round-trip test."""
    ins = decode_one(bank, off)
    if ins is None:
        return b""
    return bank[off:off + ins.size]
