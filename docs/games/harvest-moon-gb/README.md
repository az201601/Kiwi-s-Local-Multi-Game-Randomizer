# Harvest Moon GB (USA) — reverse engineering knowledge base

Target: `Harvest Moon GB (USA) (SGB Enhanced).gbc` (in repo root — **DO NOT COMMIT/DISTRIBUTE**, see legal note below).

- Game: **Harvest Moon GB** (牧場物語GB), developed by Victor Interactive Software, published by Natsume, 1998.
- The file in the repo is the US/overseas release, GBC-compatible, SGB-enhanced, MBC3+Timer+RAM+BATTERY, 1 MiB, 64 × 16 KiB banks.
- This is **not** the later "Harvest Moon GBC" (a different game), nor the Japanese 512 KiB original.
- Common reference: H0smax/HMGB-Disassembly (targets the **Japanese** ROM, md5 `6633ea57f86c4a117f0b027da68bda92`) — used as layout reference only; our ROM is a different build.

## Legal

The ROM is copyrighted by Natsume/Victor Interactive Software. We use it locally for
reverse engineering in the same way tools like pret work. This repository must not
redistribute the ROM or substantial verbatim ROM/derived content.
`.gitignore` excludes `*.gb` / `*.gbc` / generated disassembly. The full raw
disassembly is kept under `work/` (ignored) and must not be committed.
Small quoted strings for documentation are fair-use RE annotations.

## Status snapshot (2026-11-13, cont.)

- Dialogue text fully decoded (caps + digits + punctuation, punctuation table render-verified
  2026-11-13: `!`=`$40` `.`=`$4C` `,`=`$8E` `?`=`$41`); records are fixed-size slots
  addressable from two bank-0 tables; high half = relocated second build (bank `0x20` is the
  only boot-critical high bank); LCDC-STAT dispatcher = hblank glyph pump; tools verified
  against live decoding, incl. `$a0-$a3` player-name farcall tokens.
- New-game flow mapped end-to-end (farmer name → gender → pet preference → pet name →
  **full SRAM template init** → intro cutscene). Name storage: farmer `$b8f4-$b8f7`,
  pet `$b8dc-$b8df`. Docs are the deliverable; ROM is never committed.

See the other docs in this folder:

| Doc | Contents |
|---|---|
| `rom-identification.md` | Header, checksums, cart structure, **the twin-build duplication**, farcall convention, bank 0x20 = CGB bootstrap (REQUIRED) |
| `text-encoding.md` | US dialogue text format, confirmed character table (digits `$34–$3D`, `! = $4C`, `? = $41`, `' = $4B`, `'s = $53`, zero-width pause `$44`), decoder usage |
| `record-format.md` | **Fixed-slot records** (bank 07 stride `0x2B`, banks 0a/0c/10 stride `0x56`), bank-0 pointer tables at `0x03421`/`0x03601`, text-VM dispatch, LCDC-STAT glyph pump dispatcher `$3311` |
| `ram-map.md` | WRAM/SRAM addresses verified against JP reference |
| `name-entry.md` | Name-entry screen semantics: cursor WRAM `$cd65/$cd66`, END tile (row 5 col 11) = confirm, 4-char limit, pure-button confirm path, deterministic boot-to-grid recipe, glyph-code table |
| `new-game-flow.md` | Opening sequence (farmer name → gender → pet preference → pet name → SRAM template init → intro cutscene), name storage addresses, player-name farcall disassembly |
| `emulator-workflow.md` | PyBoy headless pipeline: boot, input, screenshots, bank reads, RTC lock |
| `prior-art.md` | H0smax JP disassembly, Sanqui hm3, tooling landscape, licenses |
| `open-questions.md` | Known unknowns and next investigations (updated 2026-11-13) |

## Quick commands

```bash
python3 games/harvest-moon-gb/tools/rominfo.py "Harvest Moon GB (USA) (SGB Enhanced).gbc"
python3 games/harvest-moon-gb/tools/extract_text.py "Harvest Moon GB (USA) (SGB Enhanced).gbc" > work/dialogue.txt
python3 games/harvest-moon-gb/tools/render_tiles.py "Harvest Moon GB (USA) (SGB Enhanced).gbc" \
    --offset 0x44000 --length 0x4000 --out work/bank11_tiles.png
```
