# Prior art & tooling landscape (2026-11)

## Games/ROMs involved

| Item | ID | Notes |
|---|---|---|
| This project | `Harvest Moon GB (USA) (SGB Enhanced).gbc` | the file in repo root |
| HMGB original | md5 `6633ea57f86c4a117f0b027da68bda92` | Japanese, 512 KiB, original HMGB |
| HMGB GBC | `737b0fe3b7f5f86c7541c1f52f1b7546` | "Harvest Moon GBC", *different* game (developed later), do NOT confuse |
| HMGB DX (collection) | inside Hidden Catch GB / Harvest Moon GB collection | emulations of JP original |
| Sega Genesis "Worldwide Pocket"? | n/a | not related |

## Repositories

- **H0smax/HMGB-Disassembly** — the only full disassembly of this game.
  Targets the Japanese 512 KiB ROM. RGBDS source; builds a byte-identical JP ROM.
  Symbol names (`sCurrentSeason`, `$b880`+, `$a100`+, …) are used throughout our notes.
  License not stated (treat as reference, do not vendor code).
  <https://github.com/H0smax/HMGB-Disassembly>
- **Sanqui/Hm3** — Harvest Moon 3 (SNES) editor; not applicable code-wise but its wiki
  documents the general save-format/structure (`sCurrentSeason` etc. names come from here lineage).
- ** pret / disassemblies** — not this game.
- **GB Studio / gb-symbols** — tooling ecosystems for GBC.
- **PyBoy** — maintained Game Boy emulator in Python; the one we use headless for RE + testing.
- **BGB** (Windows) — best-in-class debugger (memory viewer, tilemap, breakpoints) — Win32 only.
- **mgb** (and `mgbdis`) — cycle-accurate GB/GBC emulator + disassembler (Go). Code-perfect.
- **Emulicious** — Java emulator/debugger with labels/breakpoints/symbols import; cross-platform.

## Legal state of the JP reference

H0smax's repo contains a **ROM-decompilation workflow** (builds the ROM from RGBDS source +
asserts md5). We treat the disassembly **as documentation only**: we read it to learn layout;
our ROM differs (twin build, US text, +1 MiB size) so its source would not assemble to our
binary anyway.

## Naming

"Harvest Moon GB" vs "Harvest Moon GB GBC": the cart in our repo is literally the US release
of *Harvest Moon GB* — cart title `HARVEST-MOON GB`, CGB-compatible mode (`0x80`), SGB-enhanced.
It is sometimes mislabeled "GBC" because it runs on GBC hardware. Not the same as
*Harvest Moon GBC* (Kirby rarity / Natsume collection).
