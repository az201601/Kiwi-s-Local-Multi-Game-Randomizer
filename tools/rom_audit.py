#!/usr/bin/env python3
"""Dependency-free, offline Game Boy ROM header and bank audit."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

BANK_SIZE = 0x4000
NINTENDO_LOGO = bytes.fromhex(
    "CE ED 66 66 CC 0D 00 0B 03 73 00 83 00 0C 00 0D "
    "00 08 11 1F 88 89 00 0E DC CC 6E E6 DD DD D9 99 "
    "BB BB 67 63 6E 0E EC CC DD DC 99 9F BB B9 33 3E"
)
ROM_SIZES = {
    0x00: 32 * 1024, 0x01: 64 * 1024, 0x02: 128 * 1024,
    0x03: 256 * 1024, 0x04: 512 * 1024, 0x05: 1024 * 1024,
    0x06: 2 * 1024 * 1024, 0x07: 4 * 1024 * 1024,
    0x08: 8 * 1024 * 1024, 0x52: 1152 * 1024,
    0x53: 1280 * 1024, 0x54: 1536 * 1024,
}
RAM_SIZES = {0x00: 0, 0x01: 2 * 1024, 0x02: 8 * 1024,
             0x03: 32 * 1024, 0x04: 128 * 1024, 0x05: 64 * 1024}
CARTRIDGE_TYPES = {
    0x00: "ROM ONLY", 0x01: "MBC1", 0x02: "MBC1+RAM", 0x03: "MBC1+RAM+BATTERY",
    0x05: "MBC2", 0x06: "MBC2+BATTERY", 0x08: "ROM+RAM", 0x09: "ROM+RAM+BATTERY",
    0x0B: "MMM01", 0x0C: "MMM01+RAM", 0x0D: "MMM01+RAM+BATTERY",
    0x0F: "MBC3+TIMER+BATTERY", 0x10: "MBC3+TIMER+RAM+BATTERY",
    0x11: "MBC3", 0x12: "MBC3+RAM", 0x13: "MBC3+RAM+BATTERY",
    0x19: "MBC5", 0x1A: "MBC5+RAM", 0x1B: "MBC5+RAM+BATTERY",
    0x1C: "MBC5+RUMBLE", 0x1D: "MBC5+RUMBLE+RAM",
    0x1E: "MBC5+RUMBLE+RAM+BATTERY", 0x20: "MBC6",
    0x22: "MBC7+SENSOR+RUMBLE+RAM+BATTERY", 0xFC: "POCKET CAMERA",
    0xFD: "BANDAI TAMA5", 0xFE: "HuC3", 0xFF: "HuC1+RAM+BATTERY",
}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def calculate_header_checksum(rom: bytes) -> int:
    value = 0
    for byte in rom[0x134:0x14D]:
        value = (value - byte - 1) & 0xFF
    return value


def calculate_global_checksum(rom: bytes) -> int:
    return sum(rom[:0x14E] + rom[0x150:]) & 0xFFFF


def profile(path: Path) -> dict:
    rom = path.read_bytes()
    if len(rom) < 0x150:
        raise ValueError(f"ROM is too short for a cartridge header: {len(rom)} bytes")

    title = rom[0x134:0x143].split(b"\0", 1)[0].decode("ascii", "replace").rstrip()
    rom_size_code = rom[0x148]
    ram_size_code = rom[0x149]
    declared_size = ROM_SIZES.get(rom_size_code)
    header_checksum = calculate_header_checksum(rom)
    stored_global = int.from_bytes(rom[0x14E:0x150], "big")
    calculated_global = calculate_global_checksum(rom)
    banks = []
    for index, start in enumerate(range(0, len(rom), BANK_SIZE)):
        bank = rom[start:start + BANK_SIZE]
        banks.append({
            "bank": index,
            "file_offset_start": f"0x{start:06X}",
            "file_offset_end_exclusive": f"0x{start + len(bank):06X}",
            "cpu_window": "0x0000-0x3FFF" if index == 0 else "0x4000-0x7FFF (banked)",
            "bytes": len(bank),
            "first_byte": f"0x{bank[0]:02X}" if bank else None,
            "first_16_bytes": bank[:16].hex(" ").upper(),
            "first_byte_matches_physical_bank_index": index > 0 and bool(bank) and bank[0] == index,
            "sha256": sha256(bank),
            "all_00": bool(bank) and all(x == 0x00 for x in bank),
            "all_ff": bool(bank) and all(x == 0xFF for x in bank),
            "count_00": bank.count(0x00),
            "count_ff": bank.count(0xFF),
        })

    return {
        "input_file": path.name,
        "file_size_bytes": len(rom),
        "sha256": sha256(rom),
        "header": {
            "title": title,
            "cgb_flag": f"0x{rom[0x143]:02X}",
            "cgb_mode": {0x00: "monochrome-compatible", 0x80: "CGB-enhanced/dual-mode", 0xC0: "CGB-only"}.get(rom[0x143], "other/unknown"),
            "sgb_flag": f"0x{rom[0x146]:02X}",
            "cartridge_type_code": f"0x{rom[0x147]:02X}",
            "cartridge_type": CARTRIDGE_TYPES.get(rom[0x147], "unknown cartridge type"),
            "rom_size_code": f"0x{rom_size_code:02X}",
            "declared_rom_size_bytes": declared_size,
            "actual_matches_declared_size": declared_size == len(rom),
            "ram_size_code": f"0x{ram_size_code:02X}",
            "declared_ram_size_bytes": RAM_SIZES.get(ram_size_code),
            "destination_code": f"0x{rom[0x14A]:02X}",
            "old_licensee_code": f"0x{rom[0x14B]:02X}",
            "version": rom[0x14C],
            "entry_bytes_0x0100": rom[0x100:0x104].hex(" ").upper(),
            "nintendo_logo_matches": rom[0x104:0x134] == NINTENDO_LOGO,
            "header_checksum_stored": f"0x{rom[0x14D]:02X}",
            "header_checksum_calculated": f"0x{header_checksum:02X}",
            "header_checksum_valid": rom[0x14D] == header_checksum,
            "global_checksum_stored": f"0x{stored_global:04X}",
            "global_checksum_calculated": f"0x{calculated_global:04X}",
            "global_checksum_valid": stored_global == calculated_global,
        },
        "bank_size_bytes": BANK_SIZE,
        "actual_bank_count": (len(rom) + BANK_SIZE - 1) // BANK_SIZE,
        "banks": banks,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="path to a local Game Boy ROM")
    parser.add_argument("--json", type=Path, help="also write the full audit as JSON")
    args = parser.parse_args()

    result = profile(args.rom)
    h = result["header"]
    print(f"File: {result['input_file']}")
    print(f"Size: {result['file_size_bytes']:,} bytes; SHA-256: {result['sha256']}")
    print(f"Title: {h['title']}")
    print(f"CGB: {h['cgb_mode']} (flag {h['cgb_flag']})")
    print(f"Cartridge: {h['cartridge_type']} (type {h['cartridge_type_code']})")
    print(f"ROM: {result['actual_bank_count']} banks of up to {result['bank_size_bytes']:,} bytes; declared size matches: {h['actual_matches_declared_size']}")
    print(f"RAM: {h['declared_ram_size_bytes']} bytes (header code {h['ram_size_code']})")
    print(f"Entry: {h['entry_bytes_0x0100']}; Nintendo logo valid: {h['nintendo_logo_matches']}")
    print(f"Checksums: header {h['header_checksum_stored']} / {h['header_checksum_calculated']} (valid={h['header_checksum_valid']}); "
          f"global {h['global_checksum_stored']} / {h['global_checksum_calculated']} (valid={h['global_checksum_valid']})")
    print("Bank hashes:")
    for bank in result["banks"]:
        print(f"  {bank['bank']:02d} {bank['sha256']}  0x{bank['file_offset_start'][2:]}-0x{int(bank['file_offset_end_exclusive'], 16)-1:06X}  "
              f"00={bank['count_00']:5d} FF={bank['count_ff']:5d}")
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(f"JSON profile written to {args.json}")


if __name__ == "__main__":
    main()
