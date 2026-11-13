# Kiwi's Local Multi-Game Randomizer

A **local, offline** multi-game randomizer in the spirit of Archipelago - same
idea (a multiworld where items in one game can unlock progress in another),
same item/location logic, but everything runs on your machine with no server
and no network.

Design rules we hold ourselves to:

* **offline by default** - no accounts, no telemetry, no sockets;
* **no copyrighted data in this repo** - ROMs live in `romfs/` (git-ignored)
  and are read at runtime;
* **reverse engineering before randomization** - we only patch a game we can
  rebuild byte-for-byte from source, so every change is reviewable.

## Roadmap

| # | Game | Platform | Status |
|---|------|----------|--------|
| 1 | Harvest Moon GB | Game Boy Color | **RE groundwork** (this milestone) |
| 2 | TBD | - | - |

For game 1 the goal is a working *logic model* (what unlocks what, and in what
order) plus the ability to patch ROM data - not a full source port.

## What exists today

```
tools/gb/          Game Boy analysis toolkit (pure Python, no emulator)
  sm83.py          256-opcode table (SM83 / LR35902), hand-written + test-checked
  disasm.py        decoder, flow classifier
  rom.py           cartridge header, MBC identification, bank mapping
  analyze.py       recursive-descent code/data split, cross-bank entry discovery
  emit.py          RGBDS source emitter (byte-exact by construction)
  rgbds.py         rgbasm/rgblink/rgbfix driver, native or WASM
  build.py         CLI: info | disasm | verify
tools/demo.py      end-to-end pipeline on a synthesised cartridge
tests/             opcode-table validation, unit tests, round-trip tests
romfs/             your cartridges go here (git-ignored)
```

### The one property everything else rests on

The disassembler does not try to be clever: it does a recursive-descent walk
from the CPU's real entry points (RST/interrupt vectors, `$0100`, plus every
target it discovers), marks what it proved is code, and emits **everything
else as data**. Then `verify` re-assembles the emitted source and compares to
the original image.

```
OK: rebuilt 65536 bytes identical to original
```

If that line prints, the code/data split is consistent: no data byte was
misread as an instruction in a way that changed the bytes, and no instruction
was dropped. That is the safety net that makes patching a retail cartridge
defensible - and it keeps working as annotations are added, which is how the
analysis gets better over time without ever getting *less* trustworthy.

## Quick start

```bash
make venv        # .venv + pyboy + pytest
make demo        # full pipeline on a generated ROM (works with no cartridge)
make test        # 27 tests: opcode table vs. an independent reference,
                 # decoding edge cases, byte-exact round trip
```

With your own dump in place:

```bash
cp /path/to/your/dump.gbc romfs/harvest_moon_gbc/hm.gbc
make info        # title, MBC, CGB flag, checksum sanity
make disasm      # out/hmgbc/{main.asm,banks/bankNN.asm,link.script}
make verify      # rebuild and compare
```

## Why RGBDS

The emitted source is real RGBDS assembly, so the same tree can be assembled
by the official toolchain. In this sandbox the native binaries are not
downloadable, so `tools/gb/rgbds.py` drives the WASM builds of
`rgbasm`/`rgblink`/`rgbfix` (via `node`) and falls back to native binaries when
they exist. Nothing about the output depends on which backend ran.

## Legal

This repository contains **no** copyrighted ROM data and never will. Bring your
own dump of a cartridge you own. The tooling is analysis-only: it reads an
image you supply, writes assembly, and rebuilds it.
