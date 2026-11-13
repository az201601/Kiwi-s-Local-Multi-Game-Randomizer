# Dialogue text format (USA build) — CONFIRMED table

All values verified against ≥2 independent records in 2026-11-13 (byte-level alignment with
English words). Tool: `games/harvest-moon-gb/tools/textcodec.py` / `extract_text.py`.
ROM-derived bulk output must stay in `work/` (git-ignored).

## TL;DR

Nearly 1 byte per glyph. Sentence case is ALL CAPS — there is no lowercase text.
`glyph = ascii − 0x27 (mod 256)` for A–Z exactly; digits/punctuation do NOT follow ASCII.

## Confirmed character table

Punctuation entries were **corrected on 2026-11-13 against rendered text in the US
opening cutscene** (the name-entry farcall path draws name bytes with the same glyph
font, which gave us a controlled way to see every symbol; the record pages then confirmed
`$40`/`$4C`/`$8E` in context). Earlier docs claimed `. = $40 (final) / $8E (line)` and
`! = $4C` — **both were wrong**.

| Byte | Glyph | Evidence |
|------|-------|----------|
| `$00`–`$19` | Bold A–Z (bold A=`$00`) | "SHIPPING BOX" records; render-verified bold caps |
| `$1A`–`$33` | **A–Z** | every decoded word; render-verified |
| `$34`–`$3D` | **0–9** (`$34`='0', slashed zero) | "6AM" (`3a`), "75G" (`3b 39`), "200G", render-verified |
| `$3E` | `[` | name-grid cell (render-verified) → text renders same glyph; "[SHIPPING BOX]" signs |
| `$3F` | `]` | same |
| `$40` | `!` | "I did not have a successor**!**", "Ranch Master**!!**" (render-verified) |
| `$41` | `?` | "WHAT CAN I DO FOR YOU?", "MONEY?", "WOULD YOU LIKE A DRINK?" (record-terminal, corpus) |
| `$42` | `♥` | name-grid cell (render-verified) |
| `$43` | `…` | name-grid cell (render-verified) |
| `$44` | author-marked word-break point → font tile `-` (hyphen) | "COL{44}DER", "HURRI{44}CANE", "SPRINKL{44}ER"; line-break hyphens ("ne-glected", "Unfor-tunately", "for-got") are drawn by the engine at author-marked points; the font tile at `$44` IS `-` (render-verified in names). Zero-width in records. |
| `$45` | `~` | name-grid cell (render-verified) |
| `$46` | `_` | name-grid cell (render-verified) |
| `$47` | `↑` | name-grid cell (render-verified) |
| `$48` | `←` | name-grid cell (render-verified) |
| `$49` | `↓` | name-grid cell (render-verified) |
| `$4A` | `→` | name-grid cell (render-verified) |
| `$4B` | `'` apostrophe | "WEREN'T", "I'M", "DIDN'T" |
| `$4C` | `.` (period) | "AAAA." (render-verified, twice in "Ranch Master!!" record as `40 40` vs `AAAA` + `4c 4c 4c`) |
| `$53` | `'s` ligature | "GRANDPA'S", "IT'S" |
| `$8E` | `,` (comma) | "WAKE UP," / "SO," (render-verified in opening cutscene) |
| `$EF` | space | all text |
| `$E9` | end of display line | control VM branch |
| `$FE` / `$FF` | record delimiter / record end | VM branches at `$375b`/`$375f` |

Artifacts:
- Rare unknown bytes seen inside records (`$42`,`$46`,`$4A`,`$4E`,`$52`"`-like` lead-ins) are
  **operand preludes** (record operands / portrait/speaker-slot ops), not glyphs. Note bank
  0x1b/0x3b records begin with a 3-byte operand (`46 42 4a` or `46 33 52`), and banks
  0x07/0x0a/0x0c/0x10 with the documented prologue + per-bank op16. Operand semantics still
  undecoded (open-questions).
- `$08` doubles as bold-I (bold A=`$00` → I=`$08`); do NOT treat it as a pause.
- The bold range `$00–$19` overlaps nothing; `$00` is common padding in record prologues
  (`00 00 00 00 00 00` before the operand).

## Record framing & VM

See `record-format.md` for full detail. Short version:

```
<prologue: 00 00 00 00 00 00 <op16> 00 00> <TEXT… E9> [00 padding to slot end]
```

- Records live in fixed-size slots (bank `0x07` stride `0x2B`=43 B; banks `0x0a/0c/10`
  `0x56`=86 B stride). Text is padded with `$EF`/`$00` to fill the slot.
- Token dispatch: `$FF` → end of record, `$FE` → control sub-record, `$EF/$E9/< $A0` →
  formatting/letter path, `≥ $A0` → farcall bank `0x17` window `$7739`.
- **Name-slot farcall**: `$a0`–`$a3` return pointers to the farmer's name bytes
  (`$b8f4-$b8f7` in SRAM). The font glyph drawn for that code = font tile with the same
  index: `$a0`→tile of `$b8f4`'s content, etc. The full farcall pointer table spans
  `$a0`–`$ee` (entry `$ef`–`$ff` = `$00fd` sentinel). Name bytes bypass the opcode
  dispatcher, so a name byte like `$44` draws the font's `-` glyph instead of acting as
  a word-break opcode. See `new-game-flow.md` for the handler disassembly.
- Emphasis: bold A–Z range for sentence starts and speaker names; `'s` ligature and `'`
  used normally; emphatic lines may end with `!` (`$40`).

## Verified record inventory

- Dialogue concentrates in banks `0x07 (146), 0x0a (62), 0x0c (56), 0x10 (42)` per half —
  same banks in the high half with relocated slots and the same English content.
- Menu/UI strings are inline in code banks (e.g. bank `0x07`: sleep confirmation, exchange,
  yes/no prompts, "[SHIPPING BOX]").
- Dumps (regenerable, never committed):
  ```bash
  python3 games/harvest-moon-gb/tools/extract_text.py "<ROM>" > work/dialogue.txt
  python3 games/harvest-moon-gb/tools/textcodec.py "<ROM>" --offset 0x1c636 --length 0x40
  ```

## Randomizer notes

- Rewriting a record requires staying inside its slot (bank 07 ≤ 43 B, banks 0a/0c/10 ≤ 86 B)
  including the terminating `$E9` and preserving the 10-byte prologue.
- Only caps + digits + the punctuation table above exist; there is no lowercase to encode.
- The bold range is optional emphasis; replacing with regular caps is safe.
