#!/usr/bin/env python3
"""
SM83 (Game Boy / Game Boy Color CPU) disassembler.

Implements the full Sharp SM83 instruction set including the CB prefix.
Used as the foundation for ROM reverse-engineering in this project.

Usage as a library:
    from sm83 import disassemble
    for addr, size, text in disassemble(rom_bytes, start=0x150, end=0x200):
        print(f"{addr:04X}: {text}")

Usage as CLI:
    python3 sm83.py <rom> [start_hex] [end_hex] [bank]
"""

# Register encoding order: B=0 C=1 D=2 E=3 H=4 L=5 (HL)=6 A=7
REGS = ["B", "C", "D", "E", "H", "L", "(HL)", "A"]
# Register pair encoding: BC=0 DE=1 HL=2 SP=3 (for LD rr,d16 / INC / DEC / ADD HL,rr)
RP   = ["BC", "DE", "HL", "SP"]
# Register pair encoding for PUSH/POP: BC=0 DE=1 HL=2 AF=3
RP2  = ["BC", "DE", "HL", "AF"]
# ALU operations: ADD=0 ADC=1 SUB=2 SBC=3 AND=4 XOR=5 OR=6 CP=7
ALU  = ["ADD A", "ADC A", "SUB", "SBC A", "AND", "XOR", "OR", "CP"]
# Conditions: NZ=0 Z=1 NC=2 C=3
CC   = ["NZ", "Z", "NC", "C"]
# CB rotate/shift ops: RLC=0 RRC=1 RL=2 RR=3 SLA=4 SRA=5 SWAP=6 SRL=7
CBOPS = ["RLC", "RRC", "RL", "RR", "SLA", "SRA", "SWAP", "SRL"]


def _signed8(v):
    return v - 256 if v >= 128 else v


def _fmt_r8(v):
    """Format a signed relative offset as +$xx / -$xx."""
    s = _signed8(v)
    return f"+${s:02X}" if s >= 0 else f"-${-s:02X}"


def _fmt_sp_r8(v):
    """Format ADD SP,r8 / LD HL,SP+r8 operand."""
    s = _signed8(v)
    return f"SP+${s:02X}" if s >= 0 else f"SP-${-s:02X}"


def decode(data, pc):
    """
    Decode one instruction at data[pc].
    Returns (size, text). size is instruction length in bytes.
    Raises IndexError if not enough data.
    """
    op = data[pc]
    b1 = data[pc + 1] if pc + 1 < len(data) else 0
    b2 = data[pc + 2] if pc + 2 < len(data) else 0
    d16 = b1 | (b2 << 8)

    # --- CB prefix ---
    if op == 0xCB:
        cb = b1
        r = REGS[cb & 7]
        if cb < 0x40:
            return 2, f"{CBOPS[cb >> 3]} {r}"
        elif cb < 0x80:
            return 2, f"BIT {cb >> 3 & 7}, {r}"
        elif cb < 0xC0:
            return 2, f"RES {cb >> 3 & 7}, {r}"
        else:
            return 2, f"SET {cb >> 3 & 7}, {r}"

    # --- LD r,r' block (0x40-0x7F) ---
    if 0x40 <= op <= 0x7F:
        if op == 0x76:
            return 1, "HALT"
        return 1, f"LD {REGS[(op >> 3) & 7]}, {REGS[op & 7]}"

    # --- ALU A,r block (0x80-0xBF) ---
    if 0x80 <= op <= 0xBF:
        return 1, f"{ALU[(op >> 3) & 7]} {REGS[op & 7]}"

    # --- Main table, by high nibble patterns ---
    # LD rr,d16 (0x01,0x11,0x21,0x31)
    if op & 0xCF == 0x01:
        return 3, f"LD {RP[(op >> 4) & 3]}, ${d16:04X}"
    # INC rr (0x03,0x13,0x23,0x33)
    if op & 0xCF == 0x03:
        return 1, f"INC {RP[(op >> 4) & 3]}"
    # DEC rr (0x0B,0x1B,0x2B,0x3B)
    if op & 0xCF == 0x0B:
        return 1, f"DEC {RP[(op >> 4) & 3]}"
    # INC r (0x04,0x0C,...,0x3C)
    if op & 0xC7 == 0x04:
        return 1, f"INC {REGS[(op >> 3) & 7]}"
    # DEC r (0x05,0x0D,...,0x3D)
    if op & 0xC7 == 0x05:
        return 1, f"DEC {REGS[(op >> 3) & 7]}"
    # LD r,d8 (0x06,0x0E,...,0x3E)
    if op & 0xC7 == 0x06:
        return 2, f"LD {REGS[(op >> 3) & 7]}, ${b1:02X}"
    # ADD HL,rr (0x09,0x19,0x29,0x39)
    if op & 0xCF == 0x09:
        return 1, f"ADD HL,{RP[(op >> 4) & 3]}"
    # RET cc (0xC0,0xC8,0xD0,0xD8)
    if op & 0xE7 == 0xC0:
        return 1, f"RET {CC[(op >> 3) & 3]}"
    # POP rr (0xC1,0xD1,0xE1,0xF1)
    if op & 0xCF == 0xC1:
        return 1, f"POP {RP2[(op >> 4) & 3]}"
    # JP cc,a16 (0xC2,0xCA,0xD2,0xDA)
    if op & 0xE7 == 0xC2:
        return 3, f"JP {CC[(op >> 3) & 3]},${d16:04X}"
    # CALL cc,a16 (0xC4,0xCC,0xD4,0xDC)
    if op & 0xE7 == 0xC4:
        return 3, f"CALL {CC[(op >> 3) & 3]},${d16:04X}"
    # PUSH rr (0xC5,0xD5,0xE5,0xF5)
    if op & 0xCF == 0xC5:
        return 1, f"PUSH {RP2[(op >> 4) & 3]}"
    # ALU A,d8 (0xC6,0xCE,0xD6,0xDE,0xE6,0xEE,0xF6,0xFE)
    if op & 0xC7 == 0xC6:
        return 2, f"{ALU[(op >> 3) & 7]} ${b1:02X}"
    # RST (0xC7,0xCF,...,0xFF)
    if op & 0xC7 == 0xC7:
        return 1, f"RST ${op & 0x38:02X}H"
    # JR cc,r8 (0x20,0x28,0x30,0x38)
    if op & 0xE7 == 0x20:
        return 2, f"JR {CC[(op >> 3) & 3]},{_fmt_r8(b1)}"

    # --- Single opcodes ---
    single = {
        0x00: "NOP",
        0x02: "LD (BC),A",
        0x07: "RLCA",
        0x08: f"LD (${d16:04X}),SP",
        0x0A: "LD A,(BC)",
        0x0F: "RRCA",
        0x10: "STOP",
        0x12: "LD (DE),A",
        0x17: "RLA",
        0x18: f"JR {_fmt_r8(b1)}",
        0x1A: "LD A,(DE)",
        0x1F: "RRA",
        0x22: "LD (HL+),A",
        0x27: "DAA",
        0x2A: "LD A,(HL+)",
        0x2F: "CPL",
        0x32: "LD (HL-),A",
        0x37: "SCF",
        0x3A: "LD A,(HL-)",
        0x3F: "CCF",
        0xC3: f"JP ${d16:04X}",
        0xC9: "RET",
        0xCD: f"CALL ${d16:04X}",
        0xD9: "RETI",
        0xE0: f"LDH (${b1:02X}),A",
        0xE2: "LD (C),A",
        0xE8: f"ADD SP,{_fmt_sp_r8(b1)}",
        0xE9: "JP (HL)",
        0xEA: f"LD (${d16:04X}),A",
        0xF0: f"LDH A,(${b1:02X})",
        0xF2: "LD A,(C)",
        0xF3: "DI",
        0xF8: f"LD HL,{_fmt_sp_r8(b1)}",
        0xF9: "LD SP,HL",
        0xFA: f"LD A,(${d16:04X})",
        0xFB: "EI",
    }
    if op in single:
        # Instruction lengths for the single-opcode table
        size_map = {
            0x08: 3,  # LD (a16),SP
            0x10: 2,  # STOP (0x10 0x00)
            0x18: 2,  # JR r8
            0xC3: 3,  # JP a16
            0xCD: 3,  # CALL a16
            0xE0: 2,  # LDH (a8),A
            0xE8: 2,  # ADD SP,r8
            0xEA: 3,  # LD (a16),A
            0xF0: 2,  # LDH A,(a8)
            0xF8: 2,  # LD HL,SP+r8
            0xFA: 3,  # LD A,(a16)
        }
        return size_map.get(op, 1), single[op]

    # Invalid opcodes on SM83
    if op in (0xD3, 0xDB, 0xDD, 0xE3, 0xE4, 0xEB, 0xEC, 0xED, 0xF4, 0xFC, 0xFD):
        return 1, f"DB ${op:02X}   ; (invalid opcode)"

    return 1, f"DB ${op:02X}"


def disassemble(data, start, end, bank_base=0):
    """
    Linear-sweep disassembly of data[start:end].
    Yields (address, size, text) where address is the CPU address
    (bank_base + offset within the window).
    """
    pc = start
    while pc < end:
        try:
            size, text = decode(data, pc)
        except IndexError:
            break
        yield bank_base + (pc - start), size, text
        pc += size


def disassemble_region(rom, cpu_start, cpu_end, bank=0):
    """
    Disassemble a CPU address range of a ROM.
    bank 0: CPU 0x0000-0x3FFF maps to file offset 0x0000.
    bank N>0: CPU 0x4000-0x7FFF maps to file offset N*0x4000.
    Returns list of (cpu_addr, size, text).
    """
    if bank == 0:
        file_off = cpu_start
    else:
        file_off = bank * 0x4000 + (cpu_start - 0x4000)
    length = cpu_end - cpu_start
    chunk = rom[file_off:file_off + length]
    return list(disassemble(chunk, 0, length, bank_base=cpu_start))


if __name__ == "__main__":
    import sys

    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    rom_path = sys.argv[1]
    start = int(sys.argv[2], 16) if len(sys.argv) > 2 else 0x0000
    end = int(sys.argv[3], 16) if len(sys.argv) > 3 else start + 0x100
    bank = int(sys.argv[4]) if len(sys.argv) > 4 else 0

    with open(rom_path, "rb") as f:
        rom = f.read()

    if bank == 0:
        file_off = start
    else:
        file_off = bank * 0x4000 + (start - 0x4000)

    for addr, size, text in disassemble_region(rom, start, end, bank):
        raw = rom[file_off + (addr - start): file_off + (addr - start) + size]
        hexs = " ".join(f"{b:02X}" for b in raw)
        print(f"{addr:04X}: {hexs:<9} {text}")
