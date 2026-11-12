#!/usr/bin/env python3
"""Trace executions of the bank-00 $31A0 compressed-stream routine in PyBoy.

This records runtime arguments, ROM-bank transitions, decoded destination
ranges, and output hashes. The ROM is loaded from memory, a fresh in-memory
RAM image is supplied, and stop(save=False) prevents save/RTC writes.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import io
import json
from pathlib import Path

RAM_BYTES = {0x00: 8 * 1024, 0x01: 8 * 1024, 0x02: 8 * 1024,
             0x03: 32 * 1024, 0x04: 128 * 1024, 0x05: 64 * 1024}


def read_word(memory, address: int) -> int:
    return memory[address & 0xFFFF] | (memory[(address + 1) & 0xFFFF] << 8)


def read_output(memory, start: int, length: int) -> bytes:
    return bytes(memory[(start + offset) & 0xFFFF] for offset in range(length))


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
        raise SystemExit(
            "PyBoy is optional; install it with: python -m pip install -r tools/requirements-pyboy.txt"
        ) from exc

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
    has_cgb_vram_banks = mode is not False and (mode is True or bool(rom[0x143] & 0x80))
    active: list[dict] = []
    events: list[dict] = []

    def selected_vram_bank() -> int | None:
        # $FF4F is not a usable VRAM-bank selector on DMG hardware; PyBoy may
        # return an unmapped/open-bus value there, so never report it as VBK.
        return emulator.memory[0xFF4F] & 1 if has_cgb_vram_banks else None

    def on_entry(_context) -> None:
        registers = emulator.register_file
        stack_pointer = registers.SP
        return_address = read_word(emulator.memory, stack_pointer)
        caller_bank = emulator.memory[0x4000]
        active.append({
            "frame": emulator.frame_count,
            "source_address": registers.HL,
            "source_bank": registers.C,
            "destination_argument": (registers.D << 8) | registers.E,
            "caller_bank": caller_bank,
            "return_address": return_address,
            "call_site": (return_address - 3) & 0xFFFF,
            "vram_bank_at_entry": selected_vram_bank(),
        })

    def on_stream_read(_context) -> None:
        if not active:
            return
        item = active[-1]
        registers = emulator.register_file
        source_address = registers.HL
        item["stream_bank_after_switch"] = emulator.memory[0x4000]
        item["header_address"] = source_address
        item["declared_output_size"] = read_word(emulator.memory, source_address)

    def on_return(_context) -> None:
        if not active:
            return
        item = active.pop()
        registers = emulator.register_file
        destination_start = read_word(emulator.memory, 0xFFB8)
        destination_end = read_word(emulator.memory, 0xFFBC)
        destination_length = (destination_end - destination_start) & 0xFFFF
        if destination_length > 0:
            output = read_output(emulator.memory, destination_start, destination_length)
            item["output_sha256"] = hashlib.sha256(output).hexdigest()
            item["output_first_32_bytes"] = output[:32].hex(" ")
        else:
            item["output_sha256"] = None
            item["output_first_32_bytes"] = ""
        item.update({
            "destination_start": destination_start,
            "destination_end_exclusive": destination_end,
            "output_length": destination_length,
            "source_address_after": registers.HL,
            "compressed_bytes_consumed": (registers.HL - item["source_address"]) & 0xFFFF,
            "restored_rom_bank": emulator.memory[0x4000],
            "vram_bank_at_return": selected_vram_bank(),
        })
        events.append(item)

    # The function has a shared setup block at $31AE and a single RET at $323C.
    emulator.hook_register(0, 0x31A0, on_entry, None)
    emulator.hook_register(0, 0x31AE, on_stream_read, None)
    emulator.hook_register(0, 0x323C, on_return, None)
    try:
        emulator.tick(args.frames, render=False, sound=False)
        result = {
            "pyboy_version": importlib.metadata.version("pyboy"),
            "rom_file": args.rom.name,
            "mode": args.mode,
            "frames": emulator.frame_count,
            "title": emulator.cartridge_title,
            "routine": "bank 00:$31A0 (runtime-observed compressed-stream routine)",
            "completed_calls": len(events),
            "incomplete_calls": active,
            "calls": events,
        }
        output = json.dumps(result, indent=2) + "\n"
        if args.json:
            args.json.parent.mkdir(parents=True, exist_ok=True)
            args.json.write_text(output, encoding="utf-8")
        print(output, end="")
    finally:
        emulator.stop(save=False)


if __name__ == "__main__":
    main()
