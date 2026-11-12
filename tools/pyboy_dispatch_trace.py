#!/usr/bin/env python3
"""Collect executed RST $00/$08 inline-table dispatches with local PyBoy.

The trace is a runtime sample, not a complete static call graph. The ROM is
loaded in memory and no save files are read or written.
"""

from __future__ import annotations

import argparse
import collections
import io
import json
from pathlib import Path

RAM_BYTES = {0x00: 8 * 1024, 0x01: 8 * 1024, 0x02: 8 * 1024,
             0x03: 32 * 1024, 0x04: 128 * 1024, 0x05: 64 * 1024}
BANK_SIZE = 0x4000


def read_rom_cpu(rom: bytes, active_bank: int, address: int) -> int | None:
    address &= 0xFFFF
    if address < 0x4000:
        offset = address
    elif 0x4000 <= address < 0x8000 and 1 <= active_bank < (len(rom) + BANK_SIZE - 1) // BANK_SIZE:
        offset = active_bank * BANK_SIZE + address - 0x4000
    else:
        return None
    return rom[offset] if offset < len(rom) else None


def traced_entry(rom: bytes, active_bank: int, table_base: int,
                 index: int, stride: int) -> tuple[int, int] | None:
    values = [read_rom_cpu(rom, active_bank, table_base + stride * index + i)
              for i in range(stride)]
    if any(value is None for value in values):
        return None
    target_address = values[0] | (values[1] << 8)
    target_bank = values[2] if stride == 3 else (active_bank if target_address >= 0x4000 else 0)
    return target_bank, target_address


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="local ROM path")
    parser.add_argument("--frames", type=int, default=600, help="frames to emulate (default: 600)")
    parser.add_argument("--mode", choices=("auto", "cgb", "dmg"), default="auto")
    parser.add_argument("--json", type=Path, help="optional JSON output path")
    args = parser.parse_args()

    try:
        from pyboy import PyBoy
    except ImportError as exc:
        raise SystemExit("PyBoy is optional; install it with: python -m pip install -r tools/requirements-pyboy.txt") from exc

    rom = args.rom.read_bytes()
    if len(rom) < 0x150:
        raise SystemExit("ROM is too short to contain a Game Boy header")
    mode = {"auto": None, "cgb": True, "dmg": False}[args.mode]
    ram_size = RAM_BYTES.get(rom[0x149], 8 * 1024)
    emulator = PyBoy(
        io.BytesIO(rom), window="null", sound_emulated=False, fast_mode=False,
        log_level="CRITICAL", ram_file=io.BytesIO(b"\xFF" * ram_size), cgb=mode,
    )
    emulator.set_emulation_speed(0)
    events: dict[str, collections.Counter] = {
        "rst00": collections.Counter(),
        "rst08": collections.Counter(),
    }

    def make_callback(kind: str, stride: int):
        def callback(_context) -> None:
            registers = emulator.register_file
            stack_pointer = registers.SP
            table_base = (emulator.memory[stack_pointer]
                          | (emulator.memory[(stack_pointer + 1) & 0xFFFF] << 8))
            index = registers.A
            # All mapped switchable banks in this dump carry their bank number
            # as the first byte, so read $4000 to recover the active ROM bank.
            active_bank = emulator.memory[0x4000]
            entry = traced_entry(rom, active_bank, table_base, index, stride)
            if entry is None:
                return
            target_bank, target_address = entry
            site = (table_base - 1) & 0xFFFF
            site_bank = active_bank if site >= 0x4000 else 0
            table_bank = active_bank if table_base >= 0x4000 else 0
            key = (site_bank, site, table_bank, table_base, index, target_bank, target_address)
            events[kind][key] += 1
        return callback

    emulator.hook_register(0, 0x2160, make_callback("rst00", 2), None)
    emulator.hook_register(0, 0x216C, make_callback("rst08", 3), None)
    try:
        emulator.tick(args.frames, render=False, sound=False)
        result = {
            "pyboy_version": __import__("importlib.metadata", fromlist=["version"]).version("pyboy"),
            "rom_file": args.rom.name,
            "mode": args.mode,
            "frames": emulator.frame_count,
            "title": emulator.cartridge_title,
            "dispatches": {},
        }
        for kind, counts in events.items():
            rows = []
            for key, hits in sorted(counts.items()):
                site_bank, site, table_bank, base, index, target_bank, target = key
                rows.append({
                    "hits": hits,
                    "site_bank": site_bank,
                    "rst_site": f"0x{site:04X}",
                    "table_bank": table_bank,
                    "inline_table_base": f"0x{base:04X}",
                    "index": f"0x{index:02X}",
                    "target_bank": target_bank,
                    "target_address": f"0x{target:04X}",
                })
            result["dispatches"][kind] = rows
        output = json.dumps(result, indent=2) + "\n"
        if args.json:
            args.json.parent.mkdir(parents=True, exist_ok=True)
            args.json.write_text(output, encoding="utf-8")
        print(output, end="")
    finally:
        emulator.stop(save=False)


if __name__ == "__main__":
    main()
