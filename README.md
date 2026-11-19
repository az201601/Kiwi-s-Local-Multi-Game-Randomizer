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
- [x] **Research — Grandpa's Rainy Day Money (`HMGB-CHK-001` / `HMGB-ITM-001`)** (verified in ROM code & live CGB/DMG gameplay)
  - Traced dedicated SRAM flag (`$B8FF`, `.sav[0x18FF]`), CGB/DMG drawer handlers (`02:$59C8` / `22:$5935`), all 3 farmhouse tier attribute maps (`Bank 0x11:$7251`, `$70FD`, `$6FA9`), 24-bit Gold routines (`$B8EF..$B8F1`, `00:$0CBB`, `00:$1056`), and cataloged in [`docs/hmgb-items-and-checks.md`](docs/hmgb-items-and-checks.md) for future randomization
- [x] **Research — Flower Shop / Seed Shop (`HMGB-SHP-001..009`, `HMGB-CHK-002`, `HMGB-ITM-002..011`)** (verified in ROM code & live CGB/DMG gameplay)
  - Mapped the dual-VM Town Shop engine (`09`/`29` + `1B`/`3B`), 81-byte 9-seed master table (`0x2495D` CGB / `0xA4940` DMG), 3-gate stock filter (`< 99` bags, season bitmask `$B884`, and 256-byte player-name 1st-char LUT `$B8F4` including the hidden symbol-name all-seed unlock), dynamic left-packed 5-slot scrolling counter + CGB bag palettes (`20:$57F5`), `u16` prices & hover strings, Nina's first-crop-purchase free Water Can (`1B/3B:$717A`), and overnight farm crop-season checks (`02`/`22`)
- [x] **Research — Tool Shop (`HMGB-SHP-010..015`, `HMGB-ITM-012..017`)** (verified in ROM code & live CGB/DMG gameplay)
  - Disassembled & live-tested all 6 one-time Tool Shop items (`09:$4FBE..$508D` CGB / `29:$4F95..$5064` DMG + `1B`/`3B:$4888..$4E0B`): Sprinkler (`128` tilled tiles + `7`-day `$BA3B..$BA3D` timer & `+256d` low-byte quirk + optional Water Can trade-in + unused silver Sprinkler tile `$60`), Brush, Saddlebag (`$B8EA == 2 → 3`), Milker, and Cheese/Butter Makers (`$B942 >= 200` shipped Milk)
- [x] **Research — Animal Shop (`HMGB-SHP-016..022`, `HMGB-CHK-003`, `HMGB-ITM-018..025`)** (verified in ROM code & live CGB/DMG gameplay)
  - Mapped all 9 Animal Shop options (`09:$5331..$5434` CGB / `29:$5300..$5403` DMG + `1B`/`3B:$559C..$7D80`): Cow (`5,000G` across 3 synced `u16` sites incl. `1D`/`3D` naming screen, `>= 54` grass gate, `3.9%` instant-adult RNG jackpot, and first-Cow free Cow Bell `HMGB-CHK-003`), Chicken (`500G`, `>= 36` grass gate), 12-pack Cow/Chicken Feed, Cow Medicine, M.Potion, `P.Medicine` (`$B945 >= 8` cow-sickness gate), and Cow/Chicken sell tiers
- [x] **Research — Carpenter & Home Expansion (`HMGB-SHP-023..024`, `HMGB-CHK-004`, `HMGB-ITM-026..028`)** (verified in ROM code & live CGB/DMG gameplay)
  - Mapped Carpenter counter (`09:$4DF3..$4EFF` CGB / `29:$4DD2..$4EDA` DMG + `1B`/`3B:$4E0C..$50DB`): `Material` (`50` for `500G`, 16-bit LE `$B938:$B939` clamped at `999`) and `Home Expansion` (`$B8FE` 6-state farm pipeline: next-day quote `Dlg 0x2E` at `01:$7DFE`/`21:$7CD1` & `07:$730C`/`27:$72A9` for Tier 1 Deluxe `100` Mat + `2,500G` and Tier 2 Ultra-Deluxe `300` Mat + `5,000G`, 3-day build `02:$7014`/`22:$6F74` incl. Tier 2 `+0` Happiness stray-`RET` quirk, and Deluxe completion **Super Hammer** `HMGB-CHK-004` at `01:$7E65`/`21:$7D38` plus critical Farmhouse-init auto-grant failsafe at `02:$509F`/`22:$505B`)
- [x] **Research — Restaurant & Take-Out Pantry (`HMGB-SHP-025..029`, `HMGB-ITM-029..033`)** (verified in ROM code & live CGB/DMG gameplay)
  - Mapped hybrid 5-slot Restaurant counter (`09:$51B1..$51FD` CGB / `29:$5184..$51D0` DMG + `1B`/`3B:$68AC..$6E07` + CGB palettes `20:$5823..$582C`): dine-in `Cake` (`300G`, `+25%` Max Stamina) and `Lunch Box` (`500G`, `+50%` Max Stamina — uncovered & live-reproduced a vanilla 8-bit overflow bug in `1B/3B:$45D6` that drains Stamina at `8–10` Power Berries, e.g. `200 → 44`!), plus take-out 10-packs `Croissant` (`$B8C5`, `300G`, `+30` Stamina), `Rice Ball` (`$B8C6`, `200G`, `+20`), and `Meat Dumpling` (`$B8C7`, `100G`, `+10`) auto-consumed at 06:00 / 12:00 / 18:00 meals (`02:$5FC1`/`22:$5F2E` → `08/28:$41EE`)
- [x] **Research — Juice Bar & Church — All 7 Town Locations Complete (`HMGB-SHP-030..034`, `HMGB-ITM-034..038`)** (verified in ROM code & live CGB/DMG gameplay)
  - **Juice Bar (`Map 0x15`, night-only `18:00–23:59`):** Mapped `09:$4CC2..$4DF2` CGB / `29:$4CA5..$4DD1` DMG + `1B`/`3B:$50DC..$559B` + CGB drink palettes `20:$5807..$5810`: dine-in `Orange Juice` (`300G`, `+50%` Max Stamina) and date-gated `Apple Juice` (`500G`, `+100%` Max Stamina, unlocked at `days >= 134` = Year 2 Spring 15 — suffers severe `1B/3B:$45D6` 8-bit overflow across `3–10` Power Berries, e.g. `126 → 0` at `Max=130` & `56 → 0` at `Max=200`!), plus take-out 10-packs `Wild Grape Juice` (`$B8CA`, `300G`, `+30`), `Green Tea` (`$B8CB`, `200G`, `+20`), and `$B942 >= 1` Milk-shipped-gated `Milk` (`$B8CC`, `100G`, `+10`) restored by Home-Bank `00:$1AFE`
  - **Church (`Map 0x1A`, `08:00–17:59`, unique `$B89A` 3-day guest closure):** Mapped `09:$55A1..$566C` CGB / `29:$5563..$5613` DMG + `1B`/`3B:$620C..$68AB`: free once-per-day `Pray` miracles (`$BA3F`: `1/16` sick-cow cure `$4707` or `1/32` Goddess Cow Blessing `$472A` granting `cow[+$14] = 7` sickness-shield charges consumed only on unfed nights at `03:$7180`/`23:$713A`), free repeatable `Fortune Telling` (`1B/3B:$4560` LUT: `3/16` Great `+10/+10`, `10/16` Normal, `3/16` Bad `-10/-10`), and post-game `$B8D1` + `SELECT` 40-track `Organ Sound Test` (`09:$6882,$6A24` / `29:$6829,$69CB`, incl. inverted `Left = +1` / `Right = -1` D-pad quirk and 5 of the 8 total `1B` vs `3B` byte differences)
- [x] **Research — Starting Inventory, Character Creation, New Game Init & 31-Slot Tool System (`HMGB-ITM-039..050`)** (verified in ROM code & live CGB/DMG gameplay)
  - Disassembled & live-verified Title/File Select dual save signature (`1D:$440C` / `3D:$43F2`: `"BackUp_Check!#$%"` at `$BFF0..$BFFF` + `$A002..$A011` farm border), `Map 0x12` setup wizard (4-char Player/Pet names with `$D8` space pad & blank rejection, Gender `$B8EC`, Pet `$B8DB = cursor XOR 1` with full-ROM audit proving zero Dog/Cat inventory or logic impact), and `Map 0x24` New Game initializer (`1D:$5192..$530D` CGB / `3D:$5160..$52C8` DMG, uniform `-0x45` shift, `0`-byte 8 KB CGB/DMG SRAM diff): `500G` Gold, `100/100` Stamina, `0` Berries, `4` base tools, exact `7`-byte Girl starter kit (`Water Can` + `1` each Turnip/Potato/Grass Seeds & `HMGB-CHK-002` interaction), two-pass Pantry init (`[10,10,10,_,_,10,10,0]` with unconditional Pass-2 clobber at `1D:$51EE`/`3D:$51A9` & Day 1 `06:00` breakfast `50 → 49`), `11:$5001..$6880` farm matrix (`24` boulders, `21` stumps), all `31` Tool Table & `29` Tool Shed slots (`00:$1B20`, `05:$461D`/`25:$45FE`), two new vanilla Power Berry bugs (uncapped `+0`-Stamina Goddess `0C:$7D5F`/`2C:$7D09` & Underground `0E:$4558`/`2E:$4535` grants + 7-byte CGB Starter House planter LUT `02:$5251`), and a live-tested `1D:$5205`/`3D:$51C0` custom Starting Inventory injector across CGB & DMG
- [ ] **Next Steps** — Harvest Sprites & Underground (`Map 0x0E`), Power Berry check locations (`$B904`), Super/special tool upgrade checks & events, crops/animals/shipping, starting inventory & shop randomizer implementation, and multi-game item pools

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
│   ├── hmgb-items-and-checks.md              Verified items & checks registry for randomization
│   └── hmgb-rom-research.md                  Verified ROM disassembly & in-game notes
└── randomizer/
    ├── build.sh / build.bat                  Java 8 build scripts
    └── src/main/java/com/kiwi/randomizer/
        ├── Main.java                         GUI launcher + headless CLI test runner
        ├── core/                             ROM validation & GB/GBC checksums
        ├── games/hmgb/                       Harvest Moon GBC codec, font & patcher
        └── ui/                               Swing desktop GUI & live GBC box preview
```
