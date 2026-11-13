"""Game Boy (SM83 / LR35902) instruction table.

Single source of truth for the disassembler. Operand kinds:

    NONE - no inline bytes
    D8   - 1 unsigned immediate byte
    D16  - 2 immediate bytes, little-endian word
    E8   - 1 signed relative byte (jr / add sp,e8 / ld hl,sp+e8)
    A16  - 2 bytes used as an absolute address (jp/call/ld [nnnn],a ...)

The table is validated against an independent reference in
``tests/test_sm83_table.py`` (lengths + operand shapes), so a typo here
cannot silently pass.

Cycle counts are base (branch-not-taken) M-cycles.
"""

from __future__ import annotations

NONE = "NONE"
D8 = "D8"
D16 = "D16"
E8 = "E8"
A16 = "A16"

# opcode -> (mnemonic template, operand kind, length, base cycles, flags)
Table = dict[int, tuple[str, str, int, int, str]]

OPCODES: Table = {}
CB_OPCODES: Table = {}


def _op(table: Table, code: int, mnem: str, kind: str = NONE, length: int = 1,
        cycles: int = 4, flags: str = "--------") -> None:
    # Flags are a 4-symbol mask (Z N H C); pad so every entry is comparable.
    flags = (flags + "-----")[:5]
    table[code] = (mnem, kind, length, cycles, flags)


def _build() -> None:
    o = lambda *a, **k: _op(OPCODES, *a, **k)  # noqa: E731

    # 0x00..0x3F ---------------------------------------------------------
    o(0x00, "nop")
    o(0x01, "ld bc,{}", D16, 3, 12)
    o(0x02, "ld [bc],a", NONE, 1, 8)
    o(0x03, "inc bc", NONE, 1, 8)
    o(0x04, "inc b", NONE, 1, 4, "Z0H--")
    o(0x05, "dec b", NONE, 1, 4, "Z1H--")
    o(0x06, "ld b,{}", D8, 2, 8)
    o(0x07, "rlca", NONE, 1, 4, "-0-0-")
    o(0x08, "ld [{}],sp", A16, 3, 20)
    o(0x09, "add hl,bc", NONE, 1, 8, "-0HC-")
    o(0x0A, "ld a,[bc]", NONE, 1, 8)
    o(0x0B, "dec bc", NONE, 1, 8)
    o(0x0C, "inc c", NONE, 1, 4, "Z0H--")
    o(0x0D, "dec c", NONE, 1, 4, "Z1H--")
    o(0x0E, "ld c,{}", D8, 2, 8)
    o(0x0F, "rrca", NONE, 1, 4, "-0-0-")

    o(0x10, "stop", NONE, 2, 4)
    o(0x11, "ld de,{}", D16, 3, 12)
    o(0x12, "ld [de],a", NONE, 1, 8)
    o(0x13, "inc de", NONE, 1, 8)
    o(0x14, "inc d", NONE, 1, 4, "Z0H--")
    o(0x15, "dec d", NONE, 1, 4, "Z1H--")
    o(0x16, "ld d,{}", D8, 2, 8)
    o(0x17, "rla", NONE, 1, 4, "-0-0-")
    o(0x18, "jr {}", E8, 2, 12)
    o(0x19, "add hl,de", NONE, 1, 8, "-0HC-")
    o(0x1A, "ld a,[de]", NONE, 1, 8)
    o(0x1B, "dec de", NONE, 1, 8)
    o(0x1C, "inc e", NONE, 1, 4, "Z0H--")
    o(0x1D, "dec e", NONE, 1, 4, "Z1H--")
    o(0x1E, "ld e,{}", D8, 2, 8)
    o(0x1F, "rra", NONE, 1, 4, "-0-0-")

    o(0x20, "jr nz,{}", E8, 2, 8)
    o(0x21, "ld hl,{}", D16, 3, 12)
    o(0x22, "ld [hli],a", NONE, 1, 8)
    o(0x23, "inc hl", NONE, 1, 8)
    o(0x24, "inc h", NONE, 1, 4, "Z0H--")
    o(0x25, "dec h", NONE, 1, 4, "Z1H--")
    o(0x26, "ld h,{}", D8, 2, 8)
    o(0x27, "daa", NONE, 1, 4, "Z-0C-")
    o(0x28, "jr z,{}", E8, 2, 8)
    o(0x29, "add hl,hl", NONE, 1, 8, "-0HC-")
    o(0x2A, "ld a,[hli]", NONE, 1, 8)
    o(0x2B, "dec hl", NONE, 1, 8)
    o(0x2C, "inc l", NONE, 1, 4, "Z0H--")
    o(0x2D, "dec l", NONE, 1, 4, "Z1H--")
    o(0x2E, "ld l,{}", D8, 2, 8)
    o(0x2F, "cpl", NONE, 1, 4, "-11")

    o(0x30, "jr nc,{}", E8, 2, 8)
    o(0x31, "ld sp,{}", D16, 3, 12)
    o(0x32, "ld [hld],a", NONE, 1, 8)
    o(0x33, "inc sp", NONE, 1, 8)
    o(0x34, "inc [hl]", NONE, 1, 12, "Z0H--")
    o(0x35, "dec [hl]", NONE, 1, 12, "Z1H--")
    o(0x36, "ld [hl],{}", D8, 2, 12)
    o(0x37, "scf", NONE, 1, 4, "-001-")
    o(0x38, "jr c,{}", E8, 2, 8)
    o(0x39, "add hl,sp", NONE, 1, 8, "-0HC-")
    o(0x3A, "ld a,[hld]", NONE, 1, 8)
    o(0x3B, "dec sp", NONE, 1, 8)
    o(0x3C, "inc a", NONE, 1, 4, "Z0H--")
    o(0x3D, "dec a", NONE, 1, 4, "Z1H--")
    o(0x3E, "ld a,{}", D8, 2, 8)
    o(0x3F, "ccf", NONE, 1, 4, "-00C-")

    # 0x40..0x7F: ld r,r' (0x76 == halt) --------------------------------
    regs = ["b", "c", "d", "e", "h", "l", "[hl]", "a"]
    for hi in range(8):
        for lo in range(8):
            code = 0x40 | (hi << 3) | lo
            if code == 0x76:
                o(code, "halt", NONE, 1, 4)
                continue
            # [hl] addressee costs 12 cycles for 'ld [hl],n'; the ld r,r'
            # forms are 8 cycles either way.
            o(code, f"ld {regs[hi]},{regs[lo]}", NONE, 1, 8)

    # 0x80..0xBF: alu a,r -------------------------------------------------
    alu = ["add", "adc", "sub", "sbc", "and", "xor", "or", "cp"]
    for hi, name in enumerate(alu):
        for lo in range(8):
            code = 0x80 | (hi << 3) | lo
            o(code, f"{name} a,{regs[lo]}", NONE, 1, 8, "Z0HC-")

    # 0xC0..0xFF ----------------------------------------------------------
    o(0xC0, "ret nz", NONE, 1, 8)
    o(0xC1, "pop bc", NONE, 1, 12)
    o(0xC2, "jp nz,{}", A16, 3, 12)
    o(0xC3, "jp {}", A16, 3, 16)
    o(0xC4, "call nz,{}", A16, 3, 12)
    o(0xC5, "push bc", NONE, 1, 16)
    o(0xC6, "add a,{}", D8, 2, 8, "Z0HC-")
    o(0xC7, "rst $00", NONE, 1, 16)
    o(0xC8, "ret z", NONE, 1, 8)
    o(0xC9, "ret", NONE, 1, 16)
    o(0xCA, "jp z,{}", A16, 3, 12)
    o(0xCB, "cb", NONE, 2, 8)  # replaced by CB page during decode
    o(0xCC, "call z,{}", A16, 3, 12)
    o(0xCD, "call {}", A16, 3, 24)
    o(0xCE, "adc a,{}", D8, 2, 8, "Z0HC-")
    o(0xCF, "rst $08", NONE, 1, 16)

    o(0xD0, "ret nc", NONE, 1, 8)
    o(0xD1, "pop de", NONE, 1, 12)
    o(0xD2, "jp nc,{}", A16, 3, 12)
    o(0xD3, None, NONE, 1, 4)  # illegal
    o(0xD4, "call nc,{}", A16, 3, 12)
    o(0xD5, "push de", NONE, 1, 16)
    o(0xD6, "sub a,{}", D8, 2, 8, "Z1HC-")
    o(0xD7, "rst $10", NONE, 1, 16)
    o(0xD8, "ret c", NONE, 1, 8)
    o(0xD9, "reti", NONE, 1, 16)
    o(0xDA, "jp c,{}", A16, 3, 12)
    o(0xDB, None, NONE, 1, 4)
    o(0xDC, "call c,{}", A16, 3, 12)
    o(0xDD, None, NONE, 1, 4)
    o(0xDE, "sbc a,{}", D8, 2, 8, "Z1HC-")
    o(0xDF, "rst $18", NONE, 1, 16)

    o(0xE0, "ldh [{}],a", D8, 2, 12)
    o(0xE1, "pop hl", NONE, 1, 12)
    o(0xE2, "ld [c],a", NONE, 1, 8)
    o(0xE3, None, NONE, 1, 4)
    o(0xE4, None, NONE, 1, 4)
    o(0xE5, "push hl", NONE, 1, 16)
    o(0xE6, "and a,{}", D8, 2, 8, "Z010-")
    o(0xE7, "rst $20", NONE, 1, 16)
    o(0xE8, "add sp,{}", E8, 2, 16, "00HC-")
    o(0xE9, "jp hl", NONE, 1, 4)
    o(0xEA, "ld [{}],a", A16, 3, 16)
    o(0xEB, None, NONE, 1, 4)
    o(0xEC, None, NONE, 1, 4)
    o(0xED, None, NONE, 1, 4)
    o(0xEE, "xor a,{}", D8, 2, 8, "Z010-")
    o(0xEF, "rst $28", NONE, 1, 16)

    o(0xF0, "ldh a,[{}]", D8, 2, 12)
    o(0xF1, "pop af", NONE, 1, 12)
    o(0xF2, "ld a,[c]", NONE, 1, 8)
    o(0xF3, "di", NONE, 1, 4)
    o(0xF4, None, NONE, 1, 4)
    o(0xF5, "push af", NONE, 1, 16)
    o(0xF6, "or a,{}", D8, 2, 8, "Z010-")
    o(0xF7, "rst $30", NONE, 1, 16)
    o(0xF8, "ld hl,sp+{}", E8, 2, 12, "00HC-")
    o(0xF9, "ld sp,hl", NONE, 1, 8)
    o(0xFA, "ld a,[{}]", A16, 3, 16)
    o(0xFB, "ei", NONE, 1, 4)
    o(0xFC, None, NONE, 1, 4)
    o(0xFD, None, NONE, 1, 4)
    o(0xFE, "cp a,{}", D8, 2, 8, "Z1HC-")
    o(0xFF, "rst $38", NONE, 1, 16)

    # CB page -------------------------------------------------------------
    cb = lambda *a, **k: _op(CB_OPCODES, *a, **k)  # noqa: E731
    r = ["b", "c", "d", "e", "h", "l", "[hl]", "a"]
    shifts = ["rlc", "rrc", "rl", "rr", "sla", "sra", "swap", "srl"]
    for t, name in enumerate(shifts):
        for i in range(8):
            cb((t << 3) | i, f"{name} {r[i]}", NONE, 2, 16 if i == 6 else 8,
               "Z00C" if name == "swap" else "Z00C")
    for b in range(8):
        for i in range(8):
            cb(0x40 | (b << 3) | i, f"bit {b},{r[i]}", NONE, 2,
               12 if i == 6 else 8, "Z01--")
    for b in range(8):
        for i in range(8):
            cb(0x80 | (b << 3) | i, f"res {b},{r[i]}", NONE, 2,
               16 if i == 6 else 8)
    for b in range(8):
        for i in range(8):
            cb(0xC0 | (b << 3) | i, f"set {b},{r[i]}", NONE, 2,
               16 if i == 6 else 8)


_build()

#: Cycles when a conditional jump/call/ret is actually taken.
TAKEN_CYCLES = {
    0x20: 12, 0x28: 12, 0x30: 12, 0x38: 12,
    0xC0: 20, 0xC2: 16, 0xC4: 24, 0xC8: 20, 0xCA: 16, 0xCC: 24,
    0xD0: 20, 0xD2: 16, 0xD4: 24, 0xD8: 20, 0xDA: 16, 0xDC: 24,
}

#: Opcodes that end a basic block unconditionally.
END_OPS = frozenset({0xC9, 0xD9, 0xC3, 0xE9})
#: Conditional returns (block continues on fall-through).
COND_RET_OPS = frozenset({0xC0, 0xC8, 0xD0, 0xD8})
COND_JP_OPS = frozenset({0xC2, 0xCA, 0xD2, 0xDA})
COND_JR_OPS = frozenset({0x20, 0x28, 0x30, 0x38})
UNCOND_JP_OPS = frozenset({0xC3, 0xE9})
CALL_OPS = frozenset({0xC4, 0xCC, 0xD4, 0xDC, 0xCD})
RST_OPS = frozenset({0xC7, 0xCF, 0xD7, 0xDF, 0xE7, 0xEF, 0xF7, 0xFF})
#: All opcodes whose behaviour terminates the instruction stream.
TERMINATOR_OPS = END_OPS | RST_OPS

INVALID = frozenset({0xD3, 0xDB, 0xDD, 0xE3, 0xE4, 0xEB, 0xEC, 0xED,
                     0xF4, 0xFC, 0xFD})
