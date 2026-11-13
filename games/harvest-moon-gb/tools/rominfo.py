#!/usr/bin/env python3
"""
rominfo.py — Harvest Moon GB (USA) ROM identification & structural audit.

Usage:
    python3 rominfo.py "Harvest Moon GB (USA) (SGB Enhanced).gbc" [--markdown] [--full-scan]

Prints: header fields, checksum verification, MBC/RTC facts, per-bank "bank tag"
analysis and the twin-build duplication report.

This tool is part of the Kiwi's Local Multi-Game Randomizer project.
It does NOT contain or distribute any ROM data.
"""
import argparse, hashlib, math, sys
from collections import Counter

NINTENDO_LOGO = bytes([0xCE,0xED,0x66,0x66,0xCC,0x0D,0x00,0x0B,0x03,0x73,0x00,0x83,0x00,0x0C,0x00,0x0D,
                       0x00,0x08,0x11,0x1F,0x88,0x89,0x00,0x0E,0xDC,0xCC,0x6E,0xE6,0xDD,0xDD,0xD9,0x99,
                       0xBB,0xBB,0x67,0x63,0x6E,0x0E,0xEC,0xCC,0xDD,0xDC,0x99,0x9F,0xBB,0xB9,0x33,0x3E])

CARTRIDGE_TYPES = {0x00:"ROM ONLY",0x01:"MBC1",0x02:"MBC1+RAM",0x03:"MBC1+RAM+BATTERY",0x05:"MBC2",
                   0x06:"MBC2+BATTERY",0x0F:"MBC3+TIMER+BATTERY",0x10:"MBC3+TIMER+RAM+BATTERY",
                   0x11:"MBC3",0x12:"MBC3+RAM",0x13:"MBC3+RAM+BATTERY",0x19:"MBC5",0x1A:"MBC5+RAM",
                   0x1B:"MBC5+RAM+BATTERY",0x1C:"MBC5+RUMBLE",0x1D:"MBC5+RUMBLE+RAM",0x1E:"MBC5+RUMBLE+RAM+BATTERY",0xFF:"HuC1+RAM+BATTERY"}
RAM_SIZES = {0x00:"0 KB (no RAM)",0x01:"2 KB (1 bank)",0x02:"8 KB (1 bank)",0x03:"32 KB (4 banks)",0x04:"128 KB (16 banks)",0x05:"64 KB (8 banks)"}

def header_checksum(data):
    s = 0
    for i in range(0x134, 0x14d):
        s = (s - data[i] - 1) & 0xFF
    return s

def global_checksum(data):
    return sum(b for i, b in enumerate(data) if i not in (0x14e, 0x14f)) & 0xFFFF

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("rom")
    ap.add_argument("--markdown", action="store_true")
    ap.add_argument("--full-scan", action="store_true", help="compute per-bank similarity matrix (slow)")
    args = ap.parse_args()

    data = open(args.rom, "rb").read()
    n = len(data)
    bank = 0x4000
    nbanks = n // bank
    out = []
    p = out.append

    p(f"# ROM identification — {args.rom}")
    p("")
    p(f"- size: **{n:,} bytes ({n//1024} KiB)** — {nbanks} × 16 KiB banks")
    p(f"- md5: `{hashlib.md5(data).hexdigest()}`")
    p(f"- sha256: `{hashlib.sha256(data).hexdigest()}`")
    p(f"- Nintendo logo valid: **{data[0x104:0x134] == NINTENDO_LOGO}**")
    hdr = header_checksum(data)
    p(f"- header checksum: computed `0x{hdr:02x}`, stored `0x{data[0x14d]:02x}` — **{'OK' if hdr == data[0x14d] else 'MISMATCH'}**")
    glob = global_checksum(data)
    # Game Boy global checksum is stored BIG-ENDIAN at $014E-$014F ($014E = high byte)
    stored_glob = data[0x14e] << 8 | data[0x14f]
    p(f"- global checksum: computed `0x{glob:04x}`, stored `0x{stored_glob:04x}` (big-endian) — **{'OK' if glob == stored_glob else 'MISMATCH'}**")
    p("")
    p("## Header fields")
    p("")
    p(f"- Title: `{data[0x134:0x144]!r}`")
    p(f"- CGB flag (0x143): `0x{data[0x143]:02x}` (0x80 = CGB-compatible, 0xC0 = CGB-only)")
    p(f"- Old licensee code (0x14b): `0x{data[0x14b]:02x}` (0x33 = use new licensee)")
    p(f"- New licensee code (0x144-0x145): `{data[0x144:0x146].decode('ascii', 'replace')}`")
    p(f"- SGB flag (0x146): `0x{data[0x146]:02x}` (bit supported: SGB enhancement {'yes' if data[0x146] & 0x03 else 'no'})")
    ct = data[0x147]
    p(f"- Cartridge type (0x147): `0x{ct:02x}` = **{CARTRIDGE_TYPES.get(ct, 'unknown')}**")
    p(f"- ROM size (0x148): `0x{data[0x148]:02x}` = {32 << data[0x148]} KiB ({2 << data[0x148]} banks)")
    p(f"- RAM size (0x149): `0x{data[0x149]:02x}` = {RAM_SIZES.get(data[0x149], 'unknown')}")
    p(f"- Destination code (0x14a): `0x{data[0x14a]:02x}` (0x00 = Japan, 0x01 = overseas)")
    p(f"- ROM version (0x14c): `0x{data[0x14c]:02x}`")
    p(f"- Entry point (0x100): `nop; jp $0150`")
    p("")
    p("## Bank tags (first byte of every bank)")
    p("")
    p("Every bank except bank 0 begins with its own absolute bank number. Bank 0 begins")
    p("with `0xc3` (the `jp` opcode of the entry jump). The engine reads `[$4000]` at runtime")
    p("to learn the currently mapped bank (see `docs/games/harvest-moon-gb/rom-identification.md`).")
    p("")
    tags = [data[b * bank] for b in range(nbanks)]
    ok = all(tags[b] == b for b in range(1, nbanks))
    p(f"- banks 1..{nbanks-1} tagged with own bank number: **{ok}**")
    p(f"- bank 0 first byte: `0x{tags[0]:02x}`")
    p("")
    p("## Twin-build duplication (banks i vs i+0x20)")
    p("")
    p("| bank pair | identical bytes |")
    p("|-----------|-----------------|")
    npairs = nbanks // 2
    for i in range(npairs):
        a = data[i * bank:(i + 1) * bank]
        b = data[(i + npairs) * bank:(i + npairs + 1) * bank]
        same = sum(1 for x, y in zip(a, b) if x == y)
        p(f"| {i:02x} ↔ {i + npairs:02x} | {same:6d} / {bank} ({same / bank * 100:5.1f}%) |")
    if args.full_scan:
        p("")
        p("## Full similarity matrix (upper triangle)")
        chunks = [data[b * bank:(b + 1) * bank] for b in range(nbanks)]
        for i in range(nbanks):
            row = []
            for j in range(i + 1, nbanks):
                same = sum(1 for x, y in zip(chunks[i], chunks[j]) if x == y)
                if same / bank > 0.75:
                    row.append(f"{j:02x}@{same / bank * 100:.0f}%")
            if row:
                p(f"- bank {i:02x} ~= " + ", ".join(row))
    print("\n".join(out))

if __name__ == "__main__":
    main()
