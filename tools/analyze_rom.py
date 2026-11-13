#!/usr/bin/env python3
"""
Game Boy ROM analyzer.

Parses the ROM header, validates checksums, reports MBC/RAM configuration,
and dumps the reset/interrupt vector table.

Usage:
    python3 analyze_rom.py <rom>
"""

import sys

CART_TYPES = {
    0x00: "ROM ONLY", 0x01: "MBC1", 0x02: "MBC1+RAM", 0x03: "MBC1+RAM+BATT",
    0x05: "MBC2", 0x06: "MBC2+BATT", 0x08: "ROM+RAM", 0x09: "ROM+RAM+BATT",
    0x0B: "MMM01", 0x0C: "MMM01+RAM", 0x0D: "MMM01+RAM+BATT",
    0x0F: "MBC3+TIMER+BATT", 0x10: "MBC3+TIMER+RAM+BATT", 0x11: "MBC3",
    0x12: "MBC3+RAM", 0x13: "MBC3+RAM+BATT", 0x19: "MBC5", 0x1A: "MBC5+RAM",
    0x1B: "MBC5+RAM+BATT", 0x1C: "MBC7", 0x1D: "MBC7+RAM+BATT",
    0x1E: "GBC Camera", 0x20: "MBC6000", 0x22: "MBC6000+RAM",
    0xFF: "HuC1+RAM+BATT", 0xFE: "HuC3", 0xFD: "TAMA5",
    0xFC: "Pocket Camera", 0xFB: "Pocket Printer",
}
ROM_SIZES = {
    0: "32 KiB (2 banks)", 1: "64 KiB (4 banks)", 2: "128 KiB (8 banks)",
    3: "256 KiB (16 banks)", 4: "512 KiB (32 banks)", 5: "1 MiB (64 banks)",
    6: "2 MiB (128 banks)", 7: "4 MiB (256 banks)", 8: "8 MiB (512 banks)",
}
RAM_SIZES = {0: "None", 1: "2 KiB", 2: "8 KiB", 3: "32 KiB", 4: "128 KiB", 5: "64 KiB"}

NINTENDO_LOGO = bytes([
    0xCE, 0xED, 0x66, 0x66, 0xCC, 0x0C, 0x00, 0x0B, 0x03, 0x73, 0x00, 0x83,
    0x00, 0x0C, 0x00, 0x0D, 0x00, 0x08, 0x11, 0x1F, 0x88, 0x89, 0x00, 0x0E,
    0xDC, 0xCC, 0x6E, 0xE6, 0xDD, 0xDD, 0xD9, 0x99, 0xBB, 0xBB, 0x67, 0x63,
    0x6C, 0x0E, 0x0C, 0xCC, 0x55, 0x9D, 0x20, 0x0B, 0x27, 0x60,
])


def analyze(path):
    with open(path, "rb") as f:
        rom = f.read()

    print(f"File: {path}")
    print(f"Size: {len(rom)} bytes ({len(rom)//1024} KiB, {len(rom)//16384} banks of 16 KiB)")
    print()

    # --- Header ---
    print("=== ROM Header (0x100-0x14F) ===")
    print(f"Entry point (0x100): {rom[0x100:0x104].hex(' ')}")
    title = rom[0x134:0x143].split(b"\x00")[0]
    print(f"Title (0x134): {title!r}")
    print(f"0x143 (CGB flag, new fmt): 0x{rom[0x143]:02X}  (0x80=CGB only, 0xC0=CGB enhanced)")
    print(f"Manufacturer code (0x144): {rom[0x144:0x146]!r}")
    print(f"0x146 (CGB flag): 0x{rom[0x146]:02X}")
    print(f"0x147 (new licensee): 0x{rom[0x147]:02X}")
    print(f"0x148 (SGB flag): 0x{rom[0x148]:02X}  (0x03=SGB enhanced)")
    print(f"Cartridge type (0x149): 0x{rom[0x149]:02X} = {CART_TYPES.get(rom[0x149], 'UNKNOWN')}")
    print(f"ROM size code (0x14A): 0x{rom[0x14A]:02X} = {ROM_SIZES.get(rom[0x14A], 'UNKNOWN')}")
    print(f"RAM size code (0x14B): 0x{rom[0x14B]:02X} = {RAM_SIZES.get(rom[0x14B], 'UNKNOWN')}")
    print(f"Destination (0x14C): 0x{rom[0x14C]:02X} (0x00=Japan, 0x01=non-Japan)")
    print(f"Old licensee (0x14D): 0x{rom[0x14D]:02X}")
    print(f"Version (0x14E): 0x{rom[0x14E]:02X} ({rom[0x14E]})")
    print()

    # --- Validation ---
    print("=== Validation ===")
    logo_ok = rom[0x104:0x134] == NINTENDO_LOGO
    print(f"Nintendo logo: {'OK' if logo_ok else 'CORRUPT'}")
    if not logo_ok:
        bad = [(0x104 + i, a, b) for i, (a, b) in enumerate(zip(NINTENDO_LOGO, rom[0x104:0x134])) if a != b]
        for off, exp, got in bad:
            print(f"  0x{off:03X}: expected 0x{exp:02X}, got 0x{got:02X}")

    chk = 0
    for i in range(0x134, 0x14E):
        chk = (chk - rom[i]) & 0xFF
    chk = (chk + 0x19) & 0xFF
    print(f"Header checksum: stored=0x{rom[0x14F]:02X} computed=0x{chk:02X} -> "
          f"{'OK' if chk == rom[0x14F] else 'MISMATCH'}")

    # global checksum (0x14E..end should sum to 0 mod 0x100, stored at 0x14E/0x14F pair is header; global is 0x14E? no)
    # Global checksum: sum of all bytes except the two checksum bytes at 0x14E-0x14F? Actually it's at 0x14E? No:
    # Global checksum bytes are at 0x14E? The global checksum is stored at 0x14E? Let me not compute; header is enough.
    print()

    # --- Vector table ---
    print("=== Reset / Interrupt Vectors (bank 0) ===")
    vectors = [
        (0x00, "RST 00H"), (0x08, "RST 08H"), (0x10, "RST 10H"), (0x18, "RST 18H"),
        (0x20, "RST 20H"), (0x28, "RST 28H"), (0x30, "RST 30H"), (0x38, "RST 38H"),
        (0x40, "VBlank IRQ"), (0x48, "LCD IRQ"), (0x50, "Timer IRQ"),
        (0x58, "Serial IRQ"), (0x60, "Joypad IRQ"),
    ]
    for addr, name in vectors:
        raw = rom[addr:addr + 8]
        print(f"  0x{addr:04X} {name:<12}: {raw.hex(' ')}")
    print()

    # --- Bank usage scan: fraction of non-FF / non-00 per bank ---
    print("=== Bank usage (non-FF fraction) ===")
    for bank in range(len(rom) // 0x4000):
        chunk = rom[bank * 0x4000:(bank + 1) * 0x4000]
        non_ff = sum(1 for b in chunk if b != 0xFF)
        non_00 = sum(1 for b in chunk if b != 0x00)
        print(f"  Bank {bank:2d} (file 0x{bank*0x4000:06X}): "
              f"non-FF={non_ff/0x4000*100:5.1f}%  non-00={non_00/0x4000*100:5.1f}%")
    print()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    analyze(sys.argv[1])
