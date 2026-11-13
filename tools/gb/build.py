#!/usr/bin/env python3
"""Build pipeline for the reverse-engineering workspace.

Usage::

    # inspect a cartridge without touching anything
    python -m tools.gb.build info  romfs/harvest_moon_gbc/hm.gbc

    # analyse + emit an RGBDS project under out/<rom>/
    python -m tools.gb.build disasm romfs/harvest_moon_gbc/hm.gbc --out out/hmgbc

    # prove the emitted source rebuilds the cartridge byte-for-byte
    python -m tools.gb.build verify romfs/harvest_moon_gbc/hm.gbc --out out/hmgbc
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
    __package__ = "tools.gb"

from tools.gb import analyze, emit, rgbds, rom as rommod  # noqa: E402


def cmd_info(args) -> int:
    r = rommod.load(args.rom)
    print(r.describe())
    return 0


def cmd_disasm(args) -> int:
    r = rommod.load(args.rom)
    codemap = analyze.load_codemap(args.codemap) if args.codemap else {}
    results = analyze.analyse(r, codemap=codemap)

    outdir = Path(args.out)
    emit.emit_all(r, results, outdir, codemap)
    analyze.save_codemap(outdir / "codemap.auto.json", results)

    total_code = sum(x.code_bytes() for x in results.values())
    total = r.nbanks * 0x4000
    print(f"{r.header.title!r}: {r.nbanks} banks, "
          f"{len(results)} analysed")
    for bank, res in sorted(results.items()):
        print(f"  bank {bank:02d}  {res.coverage():6.1%} code  "
              f"{len(res.code):5d} insns  "
              f"{len(res.labels):4d} labels  "
              f"{len(res.conflicts):3d} conflicts")
    print(f"  total  {total_code}/{total} bytes proved to be code "
          f"({total_code / total:.1%})")
    print(f"written: {outdir}")
    return 0


def cmd_verify(args) -> int:
    r = rommod.load(args.rom)
    src = Path(args.out) / "main.asm"
    if not src.exists():
        print(f"no disassembly at {src}; run `disasm` first", file=sys.stderr)
        return 2
    outdir = Path(args.out)
    includes = {str(p.relative_to(outdir)): p.read_bytes()
                for p in outdir.rglob("*.asm") if p.name != "main.asm"}
    assert includes, "no bank sources found"
    obj = rgbds.assemble(src.read_text(), files=includes)
    link_script = (Path(args.out) / "link.script").read_text()
    built = rgbds.link([obj], link_script=link_script)
    if len(built) < len(r.data):
        built = built.ljust(len(r.data), b"\x00")
    if built == r.data:
        print(f"OK: rebuilt {len(built)} bytes identical to original")
        return 0
    diffs = [i for i in range(min(len(built), len(r.data)))
             if built[i] != r.data[i]]
    print(f"MISMATCH: {len(diffs)} bytes differ, first at "
          f"${diffs[0]:06x} (orig ${r.data[diffs[0]]:02x} vs "
          f"built ${built[diffs[0]]:02x})", file=sys.stderr)
    for i in diffs[:16]:
        print(f"  ${i:06x}: {r.data[i]:02x} != {built[i]:02x}", file=sys.stderr)
    return 1


def cmd_xref(args) -> int:
    """Who references this address?  The bread-and-butter RE query."""
    r = rommod.load(args.rom)
    codemap = analyze.load_codemap(args.codemap) if args.codemap else {}
    results = analyze.analyse(r, codemap=codemap)
    target = int(args.addr, 16) if not args.addr.startswith("0x") else int(args.addr, 16)
    hits = 0
    for bank, res in sorted(results.items()):
        for addr, ins in sorted(res.code.items()):
            cpu = (0 if bank == 0 else 0x4000) + addr
            if ins.operand == target:
                print(f"bank {bank:02d}  ${cpu:04x}  {ins.text}")
                hits += 1
                if res.labels.get(ins.operand):
                    print(f"                -> {res.labels[ins.operand]}")
    print(f"{hits} reference(s) to ${target:04x}")
    return 0 if hits else 1


def cmd_strings(args) -> int:
    """Find ASCII runs in the image (useful for menu / text tables)."""
    r = rommod.load(args.rom)
    data = r.data
    min_len = args.min
    run = bytearray()
    start = 0
    found = 0
    for i, b in enumerate(data + b"\x00"):
        if 32 <= b < 127:
            if not run:
                start = i
            run.append(b)
        else:
            if len(run) >= min_len:
                bank, addr = r.offset_to_addr(start)
                print(f"bank {bank:02d}  ${addr:04x}  {run.decode('ascii')!r}")
                found += 1
            run = bytearray()
    print(f"{found} string(s) of >= {min_len} chars")
    return 0


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(prog="tools.gb.build", description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("info", help="dump the cartridge header")
    p.add_argument("rom")
    p.set_defaults(func=cmd_info)

    p = sub.add_parser("disasm", help="analyse and emit an RGBDS project")
    p.add_argument("rom")
    p.add_argument("--out", required=True)
    p.add_argument("--codemap", default=None,
                   help="curated codemap.json with extra entry points / names")
    p.set_defaults(func=cmd_disasm)

    p = sub.add_parser("xref", help="find code referencing an address")
    p.add_argument("rom")
    p.add_argument("addr", help="address to look for, e.g. c0f3 or 0xc0f3")
    p.add_argument("--codemap", default=None)
    p.set_defaults(func=cmd_xref)

    p = sub.add_parser("strings", help="list ASCII runs in the image")
    p.add_argument("rom")
    p.add_argument("--min", type=int, default=6)
    p.set_defaults(func=cmd_strings)

    p = sub.add_parser("verify", help="rebuild emitted source, compare bytes")
    p.add_argument("rom")
    p.add_argument("--out", required=True)
    p.set_defaults(func=cmd_verify)

    args = ap.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
