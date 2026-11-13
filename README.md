# Kiwi's Local Multi-Game Randomizer

A **local, offline, Archipelago-style** multi-game randomizer.
No network required — everything runs on your machine.

## Vision

Archipelago connects multiple games over a network so items can be shuffled
between them. This project takes the same *idea* — a shared item pool,
seeded shuffling, logic validation — but runs **entirely locally**:

- One seed generates a whole multi-game "world".
- Each game gets a patched ROM / save file with its randomized items.
- A local tracker shows where every item went.
- No servers, no accounts, no internet.

## Games (roadmap)

| # | Game | Platform | Status |
|---|---|---|---|
| 1 | Harvest Moon GBC | Game Boy Color | 🔬 Reverse engineering |
| 2 | _TBD_ | — | — |
| 3 | _TBD_ | — | — |

## Project Layout

```
.
├── Harvest Moon GB (U) [C][!].gbc   # game ROM (user-supplied)
├── tools/
│   ├── sm83.py          # SM83 (Game Boy CPU) disassembler
│   ├── analyze_rom.py   # ROM header / bank analyzer
│   └── disasm.py        # (planned) full-ROM disassembler CLI
├── disasm/
│   └── bank00.asm       # bank 0 disassembly
└── docs/
    ├── rom-analysis.md  # Harvest Moon GBC ROM findings
    └── memory-map.md    # memory map + calling conventions
```

## Harvest Moon GBC — RE Status

- ✅ ROM analyzed (1 MiB, MBC1+RAM, 64 banks)
- ✅ SM83 disassembler built and verified against boot code
- ✅ Bank 0 disassembled (10,367 instructions)
- ✅ Boot sequence, VBlank, calendar, save-RAM layout mapped
- ✅ Banked-call architecture identified (`CALL $217F`)
- ⏳ Locate new-game init + item/shop data tables
- ⏳ Map save-file format
- ⏳ Build first randomizer (starting items / shop stock)

See [`docs/rom-analysis.md`](docs/rom-analysis.md) for details.

## Quick Start

```bash
# Analyze the ROM header and banks
python3 tools/analyze_rom.py "Harvest Moon GB (U) [C][!].gbc"

# Disassemble a CPU address range (bank 0)
python3 tools/sm83.py "Harvest Moon GB (U) [C][!].gbc" 150 280

# Disassemble a range in a switchable bank (e.g. bank 5, $4000-$4100)
python3 tools/sm83.py "Harvest Moon GB (U) [C][!].gbc" 4000 4100 5
```

## Legal Note

ROMs are **not** distributed with this project. You must supply your own
dump of a game you own. All reverse-engineering is for interoperability
and personal use.
