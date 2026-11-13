# Harvest Moon GB (GBC) - reverse-engineering notes

Running notebook for the first game. Everything here is derived from public
documentation and from analysis the tools perform; no ROM data is stored.

## 1. Cartridge facts to confirm first

`make info` prints all of these from the header. Things worth recording once we
have a dump:

| Field | Why it matters |
|---|---|
| title | which regional build we are analysing (US/EU/JP) |
| MBC type | decides how banks are switched, which decides how we map `call $4xxx` targets |
| CGB flag | `$80` = CGB-enhanced DMG-compatible, `$C0` = CGB-only |
| ROM size | 4/8/16/32 banks changes how much of the bank table we can annotate |
| save type | SRAM vs none vs battery: the randomizer needs to write state somewhere |

## 2. Memory model we assume until proven otherwise

Harvest Moon GB uses the standard layout: WRAM0 `$C000-$CFFF` for the
time/day/economy variables, WRAM banks for the farm map, SRAM for the save
slot. Section 3 gets filled in as the analysis names things.

## 3. What the analysis currently knows

Run:

```bash
make disasm
python -c "import json;d=json.load(open('out/hmgbc/codemap.auto.json'));\
print({k:v['coverage'] for k,v in d['banks'].items()})"
```

Coverage is *not* a quality score - it is the fraction of bytes the walker
could prove are instructions. Real cartridges typically start at 15-35% because
graphics, maps and text sit in ROM too. The number to watch is
`unresolved_refs`: banked calls we cannot attribute to a bank yet. Each one we
resolve usually unlocks a whole function.

## 4. The work queue

1. **Bank map** - which banks hold code, graphics, map data, text. Start from
   `link.script` + coverage per bank.
2. **Entry points** - label the main loop, the day-advance routine, the
   shipping/economy update, the tool-use (hoe/watering can) handlers.
3. **Save format** - find the SRAM layout (money, day, season, inventory).
   This is what a randomizer patches on a fresh seed.
4. **Locations** - things that can only happen once: first harvest of each
   crop, each tool upgrade, each house upgrade, each festival, each animal.
5. **Items** - seeds, tools, money bundles, animals, upgrades.
6. **Logic** - Archipelago-style rules: "gold watering can requires 30
   watering-can uses and 1000G", "greenhouse requires 4 house upgrades".

## 5. Conventions in the emitted source

* `sub_XXXX` - an address the walker proved is code (a jump/call target).
* `data_XXXX` - bytes emitted verbatim, addressable by reference.
* Bank 0 addresses keep their bare 16-bit form (`sub_0150`); other banks are
  prefixed with the bank (`sub_05_4a1c`) so symbols stay unique.
* `jr`/`jp`/`call` are emitted as label references when the target is in the
  same bank, numeric otherwise (a `$4xxx` target in bank 0 is ambiguous until
  we know which bank is mapped).

## 6. Open questions

* Does the ROM use MBC5 banking only, or does it also write SRAM bank
  registers for a >8 KiB save?
* Is the day-tick interrupt-driven (VBlank) or polled in the main loop?
  That decides where a "shuffle received items into the world" hook can go.
* Are the English text strings compressed? (If yes, the text tools come before
  the item logic.)
