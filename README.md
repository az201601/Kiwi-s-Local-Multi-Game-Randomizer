# Kiwi's Local Multi-Game Randomizer

A local multiworld randomizer, Archipelago-shaped, starting with **Harvest Moon GB (USA)**.

```
dist/hm-randomizer.jar
```

## Build

```
bash tools/build.sh
```

There is no JDK in this environment (only a JRE), so Java sources are compiled with the
TeaVM-built `javac` running as WebAssembly (`npm i @browserrun/java`). The result is a
normal JAR; run it with any Java runtime.

## Run

```
java -jar dist/hm-randomizer.jar info
java -jar dist/hm-randomizer.jar generate --seed 12345 \
     --rom "Harvest Moon GB (USA) (SGB Enhanced).gbc" \
     --out output/patched.gbc
java -jar dist/hm-randomizer.jar verify --rom output/patched.gbc
```

`generate` writes the patched ROM plus `<out>.spoiler.txt`.

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/nz/kiwi/hmrandom/RomData.java` | every address/constant from the reverse engineering |
| `src/main/java/nz/kiwi/hmrandom/TextCodec.java` | game text charmap encode/decode |
| `src/main/java/nz/kiwi/hmrandom/Rom.java` | ROM image wrapper, text read/write, header checksum |
| `src/main/java/nz/kiwi/hmrandom/HarvestMoonGb.java` | the world: locations, item pool, patch builders |
| `src/main/java/nz/kiwi/hmrandom/Multiworld.java` | seed → item placement across worlds, spoiler log |
| `src/main/java/nz/kiwi/hmrandom/Patcher.java` | applies patches, fixes header checksum |
| `src/main/java/nz/kiwi/hmrandom/World.java` | seam a future world plugs into |
| `tools/compile-java.mjs` | WASM-javac driver |
| `tools/build.sh` | build script |

## Step 1 result: Grandpa's dresser

The farmhouse dresser is the first randomized check. Reverse engineering found it is **not**
an item: the original game gives **1000G** once, flagged by `sFoundMoneyInDresser`
(`$B8FF`). Text: *"What! There is money in the drawer. Was this grandpa's rainy day money?
It's mine now!! Received 1000G!!"*

Two reward shapes are implemented:

* **Money reward** — overwrite the `1000` immediate at `$99D6` and the "Received NNNG!!"
  line at `$1C70F`. No code injection.
* **Item reward** — a 19-byte `give_item` routine is injected into free space at ROM
  `$BF7C` (bank 2, `$7F7C`, 132 bytes of `$FF`), and the 10-byte reward stub at `$99D3`
  is redirected to it: `ld a,<item>` / `call $7F7C`. The routine scans the three
  `$B8F8..$B8FA` inventory slots for `$FF` (empty) and writes the item id.

The dresser handler lives at `$99C8` in bank 2 and is reached from the house dispatch
(`bank_002`, `wDominantFacingTileID == DRESSER`).

## Verified

* Patched ROM boots in PyBoy identically to the base ROM (same title screen, same frame
  count, no crash) for both money and item seeds.
* `verify` reads back the patched bytes and decodes the patched text line.
* 12 seeds produce the expected spread of money/item rewards.

## Known limitation

Driving the game to the farmhouse in PyBoy to watch the dresser fire is not finished. The
title screen's "press start" does not advance under scripted input in this PyBoy build, so
in-game verification of the dresser trigger is still outstanding. The patch bytes themselves
are confirmed by reading the ROM back.
