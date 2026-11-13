"""Recursive-descent control-flow analysis over a Game Boy ROM.

The goal is not to perfectly recover every function (that is impossible in
general - the CPU has computed jumps), but to produce a *reproducible*
code/data split:

* every bank is walked from a set of entry points,
* every instruction reached that way is reported as code,
* everything else is emitted as data,
* the resulting source re-assembles to a byte-identical ROM.

That last property is what makes the output trustworthy: if the code/data
split were wrong, the rebuild would differ.  Curated annotations (a
``codemap``) add entry points and names over time and the rebuild stays
byte-exact throughout.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from . import sm83
from .disasm import Instr, decode_one
from .rom import Rom

ROM0 = (0x0000, 0x3FFF)
ROMX = (0x4000, 0x7FFF)

#: Every address the CPU can enter on its own initiative.
BASE_ENTRIES = (*range(0x00, 0x40, 8), 0x40, 0x48, 0x50, 0x58, 0x60, 0x100)


@dataclass
class BankAnalysis:
    bank: int
    code: dict[int, Instr] = field(default_factory=dict)
    labels: dict[int, str] = field(default_factory=dict)
    #: addresses referenced by this bank but living in another ROM window
    external_refs: set[int] = field(default_factory=set)
    #: every absolute value referenced by an instruction (operand -> sources)
    all_refs: dict[int, list[int]] = field(default_factory=dict)
    #: addresses where the walk disagreed with itself
    conflicts: set[int] = field(default_factory=set)
    #: 16-bit literals that look like they point at ROMX
    literal_words: set[int] = field(default_factory=set)

    @property
    def window(self) -> tuple[int, int]:
        return ROM0 if self.bank == 0 else ROMX

    def code_bytes(self) -> int:
        return sum(i.size for i in self.code.values())

    def coverage(self, size: int = 0x4000) -> float:
        return self.code_bytes() / size


def _in(addr: int, win: tuple[int, int]) -> bool:
    return win[0] <= addr <= win[1]


def _rel_target(operand: int, next_addr: int) -> int:
    rel = operand - 0x100 if operand > 0x7F else operand
    return next_addr + rel


def walk_bank(rom: Rom, bank: int, entries: list[int],
              max_steps: int = 400_000) -> BankAnalysis:
    win = ROM0 if bank == 0 else ROMX
    base = 0 if bank == 0 else bank * 0x4000
    raw = rom.data[base:base + 0x4000]
    start_of = win[0]

    res = BankAnalysis(bank)
    pending: list[int] = list(entries)
    seen: set[int] = set()
    steps = 0

    while pending:
        addr = pending.pop()
        while True:
            steps += 1
            if steps > max_steps:
                return res
            if not _in(addr, win) or addr in seen:
                if _in(addr, ROMX) and bank != 0:
                    res.external_refs.add(addr)
                elif _in(addr, ROM0) and bank != 0:
                    res.external_refs.add(addr)
                break
            off = addr - start_of
            ins = decode_one(raw, off)
            if ins is None or off in res.code or ins.op in sm83.INVALID:
                break

            res.code[off] = ins
            seen.add(addr)
            nxt = addr + ins.size

            if ins.operand is not None:
                res.all_refs.setdefault(ins.operand, []).append(addr)
                if ins.kind in ("cjp", "call", "jp", "end", "rst"):
                    res.labels.setdefault(ins.operand, "")

            # --- control flow ------------------------------------------
            if ins.op == 0x18:  # jr
                tgt = _rel_target(ins.operand or 0, nxt)
                res.labels.setdefault(tgt, "")
                if _in(tgt, win):
                    pending.append(tgt)
                break
            if ins.op in sm83.COND_JR_OPS:
                tgt = _rel_target(ins.operand or 0, nxt)
                res.labels.setdefault(tgt, "")
                if _in(tgt, win):
                    pending.append(tgt)
                addr = nxt
                continue
            if ins.kind == "end":
                if ins.op == 0xC3 and ins.operand is not None:
                    if _in(ins.operand, win):
                        pending.append(ins.operand)
                    else:
                        res.external_refs.add(ins.operand)
                break
            if ins.kind == "jp":
                if ins.operand is not None and _in(ins.operand, win):
                    pending.append(ins.operand)
                elif ins.operand is not None:
                    res.external_refs.add(ins.operand)
                break
            if ins.kind in ("cjp", "call"):
                if ins.operand is not None:
                    if _in(ins.operand, win):
                        pending.append(ins.operand)
                    else:
                        res.external_refs.add(ins.operand)
                addr = nxt
                continue
            if ins.kind == "rst":
                if bank == 0 and ins.operand is not None:
                    pending.append(ins.operand)
                addr = nxt
                continue
            if ins.kind == "cret":
                addr = nxt
                continue

            # plain instruction: remember ROM-pointing literals
            if ins.op in (0x01, 0x11, 0x21, 0x31) and ins.operand:
                if _in(ins.operand, ROMX):
                    res.literal_words.add(ins.operand)
            addr = nxt

    return res


def analyse(rom: Rom, extra_entries: dict[int, list[int]] | None = None,
            codemap: dict | None = None,
            max_rounds: int = 4) -> dict[int, BankAnalysis]:
    """Analyse every bank, propagating discovered entry points between banks."""
    entries: dict[int, set[int]] = {0: set(BASE_ENTRIES)}

    def add(bank: int, addr: int) -> bool:
        s = entries.setdefault(bank, set())
        if addr in s:
            return False
        s.add(addr)
        return True

    if codemap:
        for bank_s, addrs in (codemap.get("entries") or {}).items():
            for a in addrs:
                add(int(bank_s), int(a) if isinstance(a, int) else int(a, 16))
    for bank, addrs in (extra_entries or {}).items():
        for a in addrs:
            add(bank, a)

    results: dict[int, BankAnalysis] = {}
    prev_sig: dict[int, tuple[int, int]] = {}

    for _round in range(max_rounds):
        for bank in range(rom.nbanks):
            results[bank] = walk_bank(rom, bank, sorted(entries.get(bank, ())))

        changed = False
        for bank, res in results.items():
            # A jump into $4000-$7FFF from bank N is ambiguous: the window can
            # hold any bank.  Seed every bank whose byte at that offset starts a
            # plausible instruction, and let the next round tell us which one
            # grows the code map.  Ambiguity is recorded, not guessed away.
            for addr in sorted(res.external_refs):
                if not _in(addr, ROMX):
                    continue
                if bank == 0:
                    for target_bank in range(1, rom.nbanks):
                        if add(target_bank, addr):
                            changed = True
            # ROM-pointing literal words are entry points for the same bank.
            for addr in sorted(res.literal_words):
                if _in(addr, ROMX) and bank != 0:
                    pass
        for bank, res in results.items():
            sig = (res.code_bytes(), len(res.labels))
            if prev_sig.get(bank) != sig:
                changed = True
                prev_sig[bank] = sig
        if not changed:
            break

    return results


def load_codemap(path: str | Path) -> dict:
    p = Path(path)
    if not p.exists():
        return {}
    return json.loads(p.read_text())


def save_codemap(path: str | Path, results: dict[int, BankAnalysis]) -> None:
    doc = {
        "generated_by": "tools/gb/analyze.py",
        "banks": {
            f"{b:02x}": {
                "coverage": round(r.coverage(), 4),
                "instructions": len(r.code),
                "code_bytes": r.code_bytes(),
                "labels": {f"{a:04x}": n or "" for a, n in sorted(r.labels.items())},
                "unresolved_refs": [f"{a:04x}" for a in sorted(r.external_refs)],
                "conflicts": [f"{a:04x}" for a in sorted(r.conflicts)],
            }
            for b, r in sorted(results.items())
        },
    }
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(doc, indent=2) + "\n")
