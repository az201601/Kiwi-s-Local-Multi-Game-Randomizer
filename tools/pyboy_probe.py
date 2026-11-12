#!/usr/bin/env python3
"""Optional local PyBoy boot probe for the supplied Harvest Moon GBC ROM.

The ROM is loaded from an in-memory stream; cartridge RAM is initialized in
memory and never saved. Hooks are debugger-only and do not alter the ROM file.
"""

from __future__ import annotations

import argparse
import io
import json
import struct
import zlib
from pathlib import Path

RAM_BYTES = {0x00: 8 * 1024, 0x01: 8 * 1024, 0x02: 8 * 1024,
             0x03: 32 * 1024, 0x04: 128 * 1024, 0x05: 64 * 1024}
WATCH_POINTS = ((0x0150, "reset_entry"), (0x01E6, "mode_signature_read"),
                (0x0202, "after_mode_branch"), (0x225A, "lcd_disable_entry"),
                (0x226D, "lcd_disable_after_wait"), (0x2272, "lcd_disable_return"))


def write_png_rgba(path: Path, pixels) -> None:
    """Write a PyBoy uint8 HxWx4 framebuffer without requiring Pillow."""
    height, width, channels = pixels.shape
    if channels != 4:
        raise ValueError(f"expected RGBA framebuffer, got shape {pixels.shape}")
    raw = b"".join(b"\x00" + pixels[y].tobytes() for y in range(height))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="local ROM path")
    parser.add_argument("--frames", type=int, default=120, help="frames to emulate (default: 120)")
    parser.add_argument("--mode", choices=("auto", "cgb", "dmg"), default="auto",
                        help="auto-detect or force CGB/DMG emulation")
    parser.add_argument("--capture", type=Path, help="optional final-frame PNG capture")
    parser.add_argument("--json", type=Path, help="optional JSON output path")
    args = parser.parse_args()

    try:
        from pyboy import PyBoy
    except ImportError as exc:
        raise SystemExit("PyBoy is optional; install it with: python -m pip install -r tools/requirements-pyboy.txt") from exc

    rom_data = args.rom.read_bytes()
    if len(rom_data) < 0x150:
        raise SystemExit("ROM is too short to contain a Game Boy header")
    ram_size = RAM_BYTES.get(rom_data[0x149], 8 * 1024)
    mode = {"auto": None, "cgb": True, "dmg": False}[args.mode]

    emulator = PyBoy(
        io.BytesIO(rom_data),
        window="null",
        sound_emulated=False,
        fast_mode=False,
        log_level="CRITICAL",
        ram_file=io.BytesIO(b"\xFF" * ram_size),
        cgb=mode,
    )
    emulator.set_emulation_speed(0)
    observations = []

    def capture(label: str):
        def callback(_context) -> None:
            registers = emulator.register_file
            observations.append({
                "point": label,
                "frame": emulator.frame_count,
                "pc": f"0x{registers.PC:04X}",
                "a": f"0x{registers.A:02X}",
                "bc": f"0x{registers.B:02X}{registers.C:02X}",
                "hl": f"0x{registers.HL:04X}",
                "dff7": f"0x{emulator.memory[0xDFF7]:02X}",
                "c0bb": f"0x{emulator.memory[0xC0BB]:02X}",
                "c0bc": f"0x{emulator.memory[0xC0BC]:02X}",
                "ly_ff44": f"0x{emulator.memory[0xFF44]:02X}",
                "lcdc_ff40": f"0x{emulator.memory[0xFF40]:02X}",
                "ie_ffff": f"0x{emulator.memory[0xFFFF]:02X}",
            })
        return callback

    for address, label in WATCH_POINTS:
        emulator.hook_register(0, address, capture(label), None)

    try:
        emulator.tick(args.frames, render=True, sound=False)
        result = {
            "pyboy_version": __import__("importlib.metadata", fromlist=["version"]).version("pyboy"),
            "rom_file": args.rom.name,
            "mode": args.mode,
            "frames": emulator.frame_count,
            "title": emulator.cartridge_title,
            "observations": observations,
        }
        if args.capture:
            write_png_rgba(args.capture, emulator.screen.ndarray.copy())
            result["capture"] = str(args.capture)
        text = json.dumps(result, indent=2) + "\n"
        if args.json:
            args.json.parent.mkdir(parents=True, exist_ok=True)
            args.json.write_text(text, encoding="utf-8")
        print(text, end="")
    finally:
        emulator.stop(save=False)


if __name__ == "__main__":
    main()
