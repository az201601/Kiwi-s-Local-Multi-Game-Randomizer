# Open questions and hypotheses

Keep this list current. Resolve questions by ROM references and reproducible observation; do not turn a guessed label into a fact merely because it sounds plausible.

## Confirmed behavior, meaning unknown

- What are the gameplay meanings of WRAM bytes `$C0BB` and `$C0BC`? Startup sets them differently in PyBoy CGB and DMG modes (`$01/$00` versus `$00/$2A`). Find all reads and writes, then observe what changes in-game.
- What is the field at `$DFF7` used for beyond holding the post-boot A signature? The startup reads it once for the mode branch; search all code and RAM access paths.
- What are the fields in `$D000–$DFFF` and `$C000–$CFFF`? The first reset clear starts at `$DD00`; another begins at `$C000` with length `$1CFF`. Map every later access and check CGB WRAM-bank selection.

## Startup/control flow

- Identify every caller and purpose of `$2242` (HRAM OAM-DMA routine copier) and `$2282` (generic zero-fill helper). PyBoy observed later calls with other destinations; enumerate all call sites and arguments.
- Trace the rest of reset initialization from `$017A` onward. Several calls remain uninterpreted, including `$2527`, `$2430`, `$217F`, and `$0278`.
- Identify the routine at `$225A`'s callers and the other routines around `$2242–$229?`; the safe LCD-disable behavior itself is confirmed.
- Determine which initial-screen sequence the sign scene, title screen, and name-entry screen represent, and map the precise controls/conditions for continuing, canceling, and confirming.
- The candidate sweep reports a bank-ambiguous direct target at bank 00 `$04DE -> $6E8B`. Determine the ROM-bank value active at that transfer and whether it is an actual call/jump or an inline-data artifact.

## Compression and graphics

- Find all executable callers of `$31A0`, including indirect/far-dispatched calls that do not appear as direct `CALL $31A0`; trace more gameplay paths to determine whether other compression formats exist.
- Identify the exact VRAM tile-map references/attributes for the decoded opening assets; the grayscale tile atlas only identifies visual patterns and does not apply map data or CGB palettes.
- Investigate the decompressor's page-aligned output assumption and zero-distance back-reference behavior with every reachable caller and controlled destination-memory states.

## Whole-ROM coverage

- Classify all 64 banks into byte ranges: code, pointer tables, structured tables, text, compressed/uncompressed graphics, maps, sound, padding, or unresolved.
- Find every mapper write and determine how ROM bank state is passed across calls and indirect dispatch.
- Decode all interrupt handlers at `$04C7`, `$3311`, `$33F9`, and `$3408`, plus every reached or table-dispatched routine.
- Map all hardware register use, including LCD/STAT/LY, timers, DMA, joypad, serial, sound, CGB palette/VRAM/WRAM banks, MBC3 RAM, and RTC.
- Establish save format and persistent state by tracing save/load routines and testing controlled in-memory save/load cycles.
