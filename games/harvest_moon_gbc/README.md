# Harvest Moon GB (Game Boy Color)

First game in the multiworld. This folder holds everything that is
*Harvest-Moon-specific*; the generic Game Boy tooling lives in `tools/gb/`.

```
games/harvest_moon_gbc/
  README.md      <- you are here
  codemap.json   <- curated entry points + names (checked in, human-edited)
  world.json     <- items / locations / rules (filled in as RE progresses)
```

## Workflow

1. Put your own dump at `romfs/harvest_moon_gbc/hm.gbc`.
2. `make info` - confirm title/MBC/CGB flag/checksum.
3. `make disasm` - emit `out/hmgbc/`.
4. `make verify` - must print `OK: rebuilt ... identical`.
5. Read `out/hmgbc/codemap.auto.json`, then copy anything you have identified
   into `codemap.json` here. Next `make disasm` uses it:

   ```bash
   .venv/bin/python -m tools.gb.build disasm romfs/harvest_moon_gbc/hm.gbc \
       --out out/hmgbc --codemap games/harvest_moon_gbc/codemap.json
   ```

`codemap.json` format:

```json
{
  "entries": { "0": ["0x150", "0x1234"], "5": ["0x4a1c"] },
  "names":   { "0": { "0150": "main_loop" },
               "5": { "4a1c": "day_advance" } },
  "comments": { "5": { "4a1c": "advances day counter at $C0F3" } }
}
```

Adding an entry can only ever *grow* the set of instructions the walker
believes in, and `make verify` tells you immediately if that belief was wrong.
That makes codemap curation safe to do incrementally.

## What we are looking for first

| Target | Why |
|---|---|
| main loop | the place a per-frame hook would go |
| day/season advance | drives nearly every location check |
| money + inventory | what a randomizer writes on a fresh seed |
| SRAM save layout | where patched state must land |
| tool-use handlers | "hoe used N times" style checks |
