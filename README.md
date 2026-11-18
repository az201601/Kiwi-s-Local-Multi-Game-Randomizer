# Kiwi's Local Multi-Game Randomizer

A Java 8 compatible desktop GUI randomizer — starting with **Harvest Moon GBC** (`Harvest Moon GB (USA) (SGB Enhanced).gbc`), built through hands-on ROM disassembly and live in-game verification.

## Quick Start (Run the GUI)

Requires **Java 8 or newer** (JRE or JDK 8+).

Double-click **`KiwisMultiGameRandomizer.jar`**, or run:

```bash
java -jar KiwisMultiGameRandomizer.jar
```

1. Select your clean `Harvest Moon GB (USA) (SGB Enhanced).gbc` ROM (auto-detected if placed in the same folder as the JAR).
2. Review or customize **Grandpa's Spirit** welcome text with the live pixel-accurate GBC dialogue box preview (rendered using the actual 8×8 font tiles from your ROM).
3. Click **Generate Randomized ROM** to save a patched `.gbc` ROM with recalculated header (`0x014D`) and global (`0x014E–0x014F`) checksums. Your original ROM is never overwritten.

## Current Progress

- [x] **Step 1 — Grandpa's Spirit Intro Speech** (verified in ROM code & live CGB/DMG gameplay)
  - Full Harvest Moon GBC font codec (`A–Z`, `a–z`, `0–9`, punctuation, `'s` ligature, `♥`, `…`, and `{PLAYER}` 4-char dynamic name placeholder)
  - Automatic 16×2 dialogue box word-wrapping + manual box-by-box mode
  - Patches Dialogue ID `0xC9` cleanly in-place (`1–19` boxes at `Bank 0x0A:$510A`) without touching adjacent NPC/Harvest Sprite dialogues, and automatically repoints via the Master Dialogue Table (`0x0367C` → `Bank 0x09:$75A1`) if you write a longer speech (`20–61` boxes)
  - Java 8 Swing GUI with live in-game tile preview, presets, `.txt` import/export, and ROM validation
- [ ] **Next Steps** — Crops, shops & prices, tools, animals, starting items/gold, seeds, and multi-game item pools

## Building From Source

Requires JDK 8+ (`build.sh` / `build.bat` automatically target Java 8 bytecode):

```bash
cd randomizer
./build.sh
```

On Windows:

```bat
cd randomizer
build.bat
```

This produces `KiwisMultiGameRandomizer.jar` in the repository root.

## Repository Layout

```text
.
├── KiwisMultiGameRandomizer.jar              Runnable Java 8 GUI randomizer
├── Harvest Moon GB (USA) (SGB Enhanced).gbc  Clean base ROM (read-only)
├── docs/
│   └── hmgb-rom-research.md                  Verified ROM disassembly & in-game notes
└── randomizer/
    ├── build.sh / build.bat                  Java 8 build scripts
    └── src/main/java/com/kiwi/randomizer/
        ├── Main.java                         GUI launcher + headless CLI test runner
        ├── core/                             ROM validation & GB/GBC checksums
        ├── games/hmgb/                       Harvest Moon GBC codec, font & patcher
        └── ui/                               Swing desktop GUI & live GBC box preview
```
