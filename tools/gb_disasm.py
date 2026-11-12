#!/usr/bin/env python3
"""Bank-aware LR35902 candidate control-flow sweep for local Game Boy ROMs.

This tool follows direct branches from explicit seeds, but it does not yet model
mapper state, indirect dispatch, call-stack semantics, or inline data. Its
output is a hypothesis list, not a verified disassembly. A ROM contains code
and non-code data; reconcile every output range against the ROM map and runtime
evidence before assigning code labels.
"""

from __future__ import annotations

import argparse
from collections import deque
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

BANK_SIZE = 0x4000
ROM_WINDOW_END = 0x8000
REGS = ("B", "C", "D", "E", "H", "L", "(HL)", "A")
RP = ("BC", "DE", "HL", "SP")
RP2 = ("BC", "DE", "HL", "AF")
CONDS = ("NZ", "Z", "NC", "C")
ALU = ("ADD A", "ADC A", "SUB", "SBC A", "AND", "XOR", "OR", "CP")
CB_GROUPS = ("RLC", "RRC", "RL", "RR", "SLA", "SRA", "SWAP", "SRL")
ILLEGAL = {0xD3, 0xDB, 0xDD, 0xE3, 0xE4, 0xEB, 0xEC, 0xED, 0xF4, 0xFC, 0xFD}


@dataclass(frozen=True)
class Instruction:
    pc: int
    size: int
    raw: bytes
    text: str
    flow: str = "next"
    target: int | None = None
    conditional: bool = False


def signed8(value: int) -> int:
    return value - 0x100 if value & 0x80 else value


def fmt8(value: int) -> str:
    return f"${value:02X}"


def fmt16(value: int) -> str:
    return f"${value:04X}"


def decode(op: int, b1: int, b2: int, pc: int) -> tuple[int, str, str, int | None, bool]:
    """Return size, mnemonic, flow, direct target, and conditional flag."""
    x, y, z = op >> 6, (op >> 3) & 7, op & 7
    p, q = y >> 1, y & 1

    if op in ILLEGAL:
        return 1, f"DB {fmt8(op)} ; illegal opcode", "illegal", None, False

    # 0x00–0x3F: miscellaneous, 16-bit arithmetic, and immediate loads.
    if x == 0:
        if z == 0:
            if y == 0:
                return 1, "NOP", "next", None, False
            if y == 1:
                value = b1 | (b2 << 8)
                return 3, f"LD ({fmt16(value)}),SP", "next", None, False
            if y == 2:
                return 2, "STOP", "stop", None, False
            if y == 3:
                target = (pc + 2 + signed8(b1)) & 0xFFFF
                return 2, f"JR {fmt16(target)}", "jump", target, False
            cond = CONDS[y - 4]
            target = (pc + 2 + signed8(b1)) & 0xFFFF
            return 2, f"JR {cond},{fmt16(target)}", "branch", target, True
        if z == 1:
            if q == 0:
                value = b1 | (b2 << 8)
                return 3, f"LD {RP[p]},{fmt16(value)}", "next", None, False
            return 1, f"ADD HL,{RP[p]}", "next", None, False
        if z == 2:
            operand = ("(BC)", "(DE)", "(HL+)", "(HL-)")[p]
            if q == 0:
                return 1, f"LD {operand},A", "next", None, False
            return 1, f"LD A,{operand}", "next", None, False
        if z == 3:
            return 1, f"{'DEC' if q else 'INC'} {RP[p]}", "next", None, False
        if z == 4:
            return 1, f"INC {REGS[y]}", "next", None, False
        if z == 5:
            return 1, f"DEC {REGS[y]}", "next", None, False
        if z == 6:
            return 2, f"LD {REGS[y]},{fmt8(b1)}", "next", None, False
        return 1, ("RLCA", "RRCA", "RLA", "RRA", "DAA", "CPL", "SCF", "CCF")[y], "next", None, False

    # 0x40–0x7F: register-to-register loads (HALT is a special opcode).
    if x == 1:
        if op == 0x76:
            return 1, "HALT", "halt", None, False
        return 1, f"LD {REGS[y]},{REGS[z]}", "next", None, False

    # 0x80–0xBF: ALU operations with an 8-bit register operand.
    if x == 2:
        return 1, f"{ALU[y]}{'' if y in (2, 4, 5, 6, 7) else ','} {REGS[z]}".replace("  ", " "), "next", None, False

    # 0xC0–0xFF: control flow, stack, high-memory, and immediate ALU.
    if z == 0:
        if y < 4:
            return 1, f"RET {CONDS[y]}", "ret_cond", None, True
        if y == 4:
            return 2, f"LDH ({fmt8(b1)}),A", "next", None, False
        if y == 5:
            return 2, f"ADD SP,{signed8(b1):+d}", "next", None, False
        if y == 6:
            return 2, f"LDH A,({fmt8(b1)})", "next", None, False
        return 2, f"LD HL,SP{signed8(b1):+d}", "next", None, False

    if z == 1:
        if q == 0:
            return 1, f"POP {RP2[p]}", "next", None, False
        if p == 0:
            return 1, "RET", "ret", None, False
        if p == 1:
            return 1, "RETI", "ret", None, False
        if p == 2:
            return 1, "JP (HL)", "indirect", None, False
        return 1, "LD SP,HL", "next", None, False

    if z == 2:
        if y < 4:
            target = b1 | (b2 << 8)
            return 3, f"JP {CONDS[y]},{fmt16(target)}", "branch", target, True
        if y == 4:
            return 1, "LD (C),A", "next", None, False
        if y == 5:
            target = b1 | (b2 << 8)
            return 3, f"LD ({fmt16(target)}),A", "next", None, False
        if y == 6:
            return 1, "LD A,(C)", "next", None, False
        target = b1 | (b2 << 8)
        return 3, f"LD A,({fmt16(target)})", "next", None, False

    if z == 3:
        if y == 0:
            target = b1 | (b2 << 8)
            return 3, f"JP {fmt16(target)}", "jump", target, False
        if y == 1:
            return 2, "PREFIX CB", "prefix", None, False
        if y == 6:
            return 1, "DI", "next", None, False
        if y == 7:
            return 1, "EI", "next", None, False
        return 1, f"DB {fmt8(op)} ; illegal opcode", "illegal", None, False

    if z == 4:
        if y < 4:
            target = b1 | (b2 << 8)
            return 3, f"CALL {CONDS[y]},{fmt16(target)}", "call", target, True
        return 1, f"DB {fmt8(op)} ; illegal opcode", "illegal", None, False

    if z == 5:
        if q == 0:
            return 1, f"PUSH {RP2[p]}", "next", None, False
        if p == 0:
            target = b1 | (b2 << 8)
            return 3, f"CALL {fmt16(target)}", "call", target, False
        return 1, f"DB {fmt8(op)} ; illegal opcode", "illegal", None, False

    if z == 6:
        return 2, f"{ALU[y]}{'' if y in (2, 4, 5, 6, 7) else ','} {fmt8(b1)}".replace("  ", " "), "next", None, False

    target = y * 8
    # RST $00/$08 consume their pushed return address as an inline pointer-table
    # base and tail-dispatch; there is no ordinary fall-through at that site.
    flow = "dispatch" if y in (0, 1) else "call"
    return 1, f"RST {fmt16(target)}", flow, target, False


def decode_cb(op: int) -> str:
    x, y, z = op >> 6, (op >> 3) & 7, op & 7
    if x == 0:
        return f"{CB_GROUPS[y]} {REGS[z]}"
    if x == 1:
        return f"BIT {y},{REGS[z]}"
    if x == 2:
        return f"RES {y},{REGS[z]}"
    return f"SET {y},{REGS[z]}"


def read_cpu(rom: bytes, bank: int, addr: int) -> tuple[int | None, int | None]:
    """Read a byte at a CPU ROM address; return (value, physical file offset)."""
    if 0 <= addr < 0x4000:
        offset = addr
    elif 0x4000 <= addr < ROM_WINDOW_END and 1 <= bank < (len(rom) + BANK_SIZE - 1) // BANK_SIZE:
        offset = bank * BANK_SIZE + addr - 0x4000
    else:
        return None, None
    if offset >= len(rom):
        return None, None
    return rom[offset], offset


def decode_at(rom: bytes, bank: int, pc: int) -> tuple[Instruction | None, int | None]:
    op, file_offset = read_cpu(rom, bank, pc)
    if op is None:
        return None, None
    b1, _ = read_cpu(rom, bank, (pc + 1) & 0xFFFF)
    b2, _ = read_cpu(rom, bank, (pc + 2) & 0xFFFF)
    b1 = 0 if b1 is None else b1
    b2 = 0 if b2 is None else b2

    if op == 0xCB:
        cb, _ = read_cpu(rom, bank, (pc + 1) & 0xFFFF)
        if cb is None:
            return Instruction(pc, 1, bytes([op]), "PREFIX CB ; truncated", "illegal"), file_offset
        raw = bytes([op, cb])
        return Instruction(pc, 2, raw, decode_cb(cb)), file_offset

    size, text, flow, target, conditional = decode(op, b1, b2, pc)
    raw_values = []
    for offset in range(size):
        byte, _ = read_cpu(rom, bank, (pc + offset) & 0xFFFF)
        if byte is None:
            return Instruction(pc, offset, bytes(raw_values), text + " ; truncated", "illegal"), file_offset
        raw_values.append(byte)
    return Instruction(pc, size, bytes(raw_values), text, flow, target, conditional), file_offset


def parse_seed(seed: str) -> tuple[int, int]:
    try:
        bank_text, address_text = seed.split(":", 1)
        bank = int(bank_text, 0)
        address = int(address_text, 0)
    except (ValueError, TypeError) as exc:
        raise argparse.ArgumentTypeError("seed must be BANK:ADDRESS, e.g. 0:0x0150") from exc
    return bank, address


def key_for_target(current_bank: int, target: int) -> tuple[int, int] | None:
    if not 0 <= target < ROM_WINDOW_END:
        return None
    if target < 0x4000:
        return 0, target
    if current_bank == 0:
        return None
    return current_bank, target


def disassemble(rom: bytes, seeds: Iterable[tuple[int, int]]) -> tuple[dict[tuple[int, int], Instruction], list[str]]:
    count = (len(rom) + BANK_SIZE - 1) // BANK_SIZE
    queue: deque[tuple[int, int]] = deque()
    for bank, addr in seeds:
        if not 0 <= addr < ROM_WINDOW_END:
            raise ValueError(f"seed address out of ROM range: ${addr:04X}")
        if addr < 0x4000:
            bank = 0
        elif not 1 <= bank < count:
            raise ValueError(f"banked seed ${addr:04X} requires bank 1..{count - 1}")
        queue.append((bank, addr))

    instructions: dict[tuple[int, int], Instruction] = {}
    byte_owners: dict[tuple[int, int], tuple[int, int]] = {}
    warnings: list[str] = []

    while queue:
        bank, pc = queue.popleft()
        while True:
            if pc < 0x4000:
                active_bank = 0
            elif pc < ROM_WINDOW_END:
                active_bank = bank
                if active_bank == 0:
                    warnings.append(f"unresolved bank context at CPU ${pc:04X} (reachable with unknown selected bank)")
                    break
            else:
                break

            key = (active_bank, pc)
            if key in instructions:
                break
            if key in byte_owners:
                owner = byte_owners[key]
                warnings.append(f"overlapping instruction boundary at bank {active_bank:02d}:${pc:04X}; already inside {owner[0]:02d}:${owner[1]:04X}")
                break

            ins, file_offset = decode_at(rom, active_bank, pc)
            if ins is None or ins.size <= 0:
                break
            instructions[key] = ins
            for offset in range(ins.size):
                byte_key = (active_bank, (pc + offset) & 0xFFFF)
                previous = byte_owners.get(byte_key)
                if previous is not None and previous != key:
                    warnings.append(f"instruction overlap bank {active_bank:02d}:${pc:04X} with {previous[0]:02d}:${previous[1]:04X}")
                byte_owners[byte_key] = key

            next_pc = (pc + ins.size) & 0xFFFF
            if ins.target is not None:
                destination = key_for_target(active_bank, ins.target)
                if destination is None:
                    if 0x4000 <= ins.target < 0x8000:
                        warnings.append(f"unresolved direct target bank at bank {active_bank:02d}:${pc:04X} -> ${ins.target:04X}")
                else:
                    queue.append(destination)

            if ins.flow in ("ret", "indirect", "illegal", "stop", "jump", "dispatch"):
                break
            if ins.flow == "halt":
                # HALT resumes at the following opcode after an interrupt.
                pc = next_pc
                continue
            if ins.flow == "call":
                # A callee can change mapper state; banked execution after a call
                # is not presumed safe without a separate bank-state analysis.
                if active_bank != 0:
                    warnings.append(f"bank state after call is not modelled at bank {active_bank:02d}:${pc:04X}")
                pc = next_pc
                continue
            if ins.flow == "branch" or ins.flow == "ret_cond":
                pc = next_pc
                continue
            pc = next_pc

    return instructions, sorted(set(warnings))


def format_listing(rom: bytes, instructions: dict[tuple[int, int], Instruction]) -> str:
    lines = [
        "; Candidate LR35902 control-flow sweep (explicit seeds only; NOT verified disassembly)",
        "; Bank identity is physical ROM bank for CPU window $4000-$7FFF; fixed $0000-$3FFF is bank 00.",
        "; Direct edges are hypotheses: mapper changes, inline tables, indirect dispatch, and call-stack state are not fully modeled.",
        "; RST $00/$08 stop normal fall-through (they consume inline tables), but table entries are not enumerated here.",
        "; Every emitted range must be validated before treating it as executed code. This is not a whole-ROM byte map.",
        "",
    ]
    for (bank, pc), ins in sorted(instructions.items()):
        if pc < 0x4000:
            file_offset = pc
        else:
            file_offset = bank * BANK_SIZE + pc - 0x4000
        raw = " ".join(f"{byte:02X}" for byte in ins.raw)
        lines.append(f"B{bank:02d}:{pc:04X} [ROM+{file_offset:06X}]  {raw:<8}  {ins.text}")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="path to a local Game Boy ROM")
    parser.add_argument("--seed", action="append", type=parse_seed, required=True,
                        help="entry point BANK:ADDRESS; repeat for multiple seeds (e.g. 0:0x0150)")
    parser.add_argument("--output", type=Path, help="write listing to this file instead of stdout")
    args = parser.parse_args()

    rom = args.rom.read_bytes()
    listing, warnings = disassemble(rom, args.seed)
    output = format_listing(rom, listing)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
        print(f"Wrote {len(listing)} candidate instruction starts to {args.output}")
    else:
        print(output, end="")
    for warning in warnings:
        print(f"WARNING: {warning}")
    print(f"Decoded {len(listing)} candidate instruction starts from {len(args.seed)} seed(s); this is not proof of execution.", file=__import__('sys').stderr)


if __name__ == "__main__":
    main()
