#!/usr/bin/env python3
"""Run the full pipeline on a synthesised cartridge - no ROM required.

This is the smoke test you can run before you have a cartridge dump in
``romfs/``: it manufactures a legal 4-bank ROM, disassembles it, rebuilds it
and prints the result of the byte comparison.
"""

from __future__ import annotations

import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tests import synth  # noqa: E402
from tools.gb import analyze, emit, rom as rommod, rgbds  # noqa: E402


def main() -> int:
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        data = synth.make_rom(nbanks=4)
        src = root / "synth.gbc"
        src.write_bytes(data)
        print(f"[1/4] synthesised cartridge: {len(data)} bytes")

        r = rommod.load(str(src))
        print(f"[2/4] header: {r.header.title!r} {r.header.cart_type_name}")
        print("      " + r.describe().replace("\n", "\n      "))

        results = analyze.analyse(r)
        out = root / "proj"
        emit.emit_all(r, results, out)
        for bank, res in sorted(results.items()):
            print(f"[3/4] bank {bank:02d}: {res.coverage():6.1%} code, "
                  f"{len(res.code):5d} insns, {len(res.labels):4d} labels")

        includes = {str(p.relative_to(out)): p.read_bytes()
                    for p in out.rglob("*.asm") if p.name != "main.asm"}
        obj = rgbds.assemble((out / "main.asm").read_text(), files=includes)
        built = rgbds.link([obj],
                           link_script=(out / "link.script").read_text())
        built = built.ljust(len(data), b"\x00")
        ok = built[:len(data)] == data
        print(f"[4/4] rebuild byte-identical: {'OK' if ok else 'MISMATCH'}")
        return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
