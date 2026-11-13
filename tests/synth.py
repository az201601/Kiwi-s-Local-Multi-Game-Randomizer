"""Synthesise a legal Game Boy cartridge for end-to-end tests.

The randomizer's analysis pipeline is validated on this ROM *before* it ever
touches a commercial cartridge: if disasm + emit + re-assemble round-trips a
generated ROM, it will round-trip the real one too (the emitter's job does not
care who wrote the bytes).
"""

from __future__ import annotations

import random

from tools.gb import rgbds

NINTENDO_LOGO_HEX = (
    "ceed6666cc0d000b03730083000c000d"
    "0008111f8889000edccc6ee6ddddd999"
    "bbbb67636e0eecccdddc999fbbb9333e"
)


def make_rom(nbanks: int = 4, seed: int = 0xC0FFEE) -> bytes:
    rnd = random.Random(seed)
    sections = []

    # --- bank 0: vectors, header, entry point --------------------------
    bank0 = bytearray(0x4000)
    # rst vectors: nop; ret
    for vec in range(0, 0x40, 8):
        bank0[vec] = 0xC9  # ret
    # interrupt vectors -> reti
    for vec in (0x40, 0x48, 0x50, 0x58, 0x60):
        bank0[vec] = 0xD9
    # entry point at $0100
    bank0[0x100] = 0x00  # nop
    bank0[0x101] = 0xC3  # jp $0150
    bank0[0x102] = 0x50
    bank0[0x103] = 0x01
    # header
    title = b"SYNTH TEST"
    bank0[0x134:0x134 + len(title)] = title
    bank0[0x104:0x134] = bytes.fromhex(NINTENDO_LOGO_HEX)
    bank0[0x143] = 0x80  # CGB enhanced
    bank0[0x147] = 0x1B  # MBC5+RAM+BATTERY (same family as retail carts)
    bank0[0x148] = (nbanks.bit_length() - 2) & 0xFF
    bank0[0x149] = 0x03
    bank0[0x14A] = 0x01
    # header checksum
    chk = 0
    for i in range(0x134, 0x14D):
        chk = (chk - bank0[i] - 1) & 0xFF
    bank0[0x14D] = chk

    # real code at $0150: a small loop + calls, exercising every flow class
    code = bytearray()
    code += bytes([0xF3])                    # di
    code += bytes([0x31, 0xFF, 0xDF])        # ld sp,$dfff
    code += bytes([0x3E, 0x00])              # ld a,0
    code += bytes([0x21, 0x00, 0xC0])        # ld hl,$c000
    code += bytes([0x06, 0x10])              # ld b,$10
    # loop: ld [hli],a ; inc a ; dec b ; jr nz,loop
    loop_addr = 0x150 + len(code)
    code += bytes([0x22, 0x3C, 0x05, 0x20, 0xFB])
    code += bytes([0xCD, 0x00, 0x42])        # call $4200 (other bank)
    code += bytes([0x18, 0xFE])              # jr $
    bank0[0x150:0x150 + len(code)] = code

    # a data table in bank 0 that collides with opcode-space on purpose
    table = bytes([0x01, 0x02, 0x03, 0xFF, 0xFE])
    bank0[0x200:0x200 + len(table)] = table

    sections.append(("bank00", "ROM0", None, bytes(bank0)))

    # --- other banks: code that is reached via a literal address -------
    for b in range(1, nbanks):
        bank = bytearray()
        if b == 1:
            # callable routine at $4200 within bank 1
            bank[0x200:0x200 + 8] = bytes(
                [0x3E, 0x42, 0xC9, 0x00, 0x00, 0x00, 0x00, 0x00])  # ld a,$42; ret
        # filler that deliberately looks like code
        for i in range(0x400, 0x1400, 4):
            bank[i:i + 4] = bytes([0x00, 0x00, 0x00, 0x00])  # nops
        # random bytes to exercise the data path
        blob = bytes(rnd.randrange(256) for _ in range(0x200))
        bank[0x2000:0x2200] = blob
        sections.append((f"bank{b:02d}", "ROMX", b, bytes(bank)))

    # --- assemble ------------------------------------------------------
    objects = []
    for name, section_type, bank, data in sections:
        header = f'SECTION "{name}", {section_type}'
        if bank is not None:
            header += f", BANK[{bank}]"
        src = [header]
        for i in range(0, len(data), 32):
            chunk = data[i:i + 32]
            src.append("    db " + ",".join(f"${b:02x}" for b in chunk))
        objects.append(rgbds.assemble("\n".join(src) + "\n"))
    return rgbds.link(objects, link_script=link_script(nbanks))


def link_script(nbanks: int) -> str:
    lines = ["ROM0", '  "bank00"']
    for b in range(1, nbanks):
        lines.append(f"ROMX ${b:02X}")
        lines.append(f'  "bank{b:02d}"')
    return "\n".join(lines) + "\n"
