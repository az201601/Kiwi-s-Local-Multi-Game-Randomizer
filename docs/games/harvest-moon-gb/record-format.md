# Dialogue record format & dispatch tables (USA build)

Status: confirmed by direct ROM reads + emulation tracing on 2026-11-13.

## TL;DR

Dialogue strings are stored in **fixed-size slots** inside four "text banks" per build:
bank `0x07` (slot stride `0x2B` = 43 bytes) and banks `0x0a / 0x0c / 0x10` (stride `0x56` = 86
bytes). A bank-0 **pointer table** (`0x03421…`, `0x03600…`) maps a record index to a
`{window16, bank8}` triplet. The text VM at bank-0 `$3730` loads a token stream, and glyph
rendering happens through either the letter path (bytes `< $A0`, most letters) or a
farcall into **bank `0x17` window `$7739`** (bytes `>= $A0`, punctuation/ligatures).

The stride is constant across the whole table, so a patcher can locate every record
deterministically and rewrite text in place (subject to slot length).

## Record tables in bank 0

Two 3-byte-triplet tables, inlined between interrupt routines:

| Table | File offset | Stride | Covers |
|---|---|---|---|
| Table 1 | `0x03421` – `0x0353d` | 3 bytes/entry, slot stride `0x2B` | bank `0x07` records (+ repeats of `($75d1, 07)` as padding tail) |
| Table 2 | `0x03601` – `0x036f1` | 3 bytes/entry | bank `0x0a` (stride `0x56`), bank `0x0c`, bank `0x10` records |

- Each entry: `{ addr_lo, addr_hi, bank }`, e.g. `01 45 07 → bank 07, window $4501`.
- The tables sit immediately after two `reti` interrupt handlers; `0x58 (SERIAL IRQ)` vector
  jumps to `0x3408` — a routine that switches to bank `0x1f` and calls `$67a7` (window).
- No other record tables were found for banks `0x1b / 0x1f / 0x27 / 0x2a / 0x2c / 0x30 / 0x3b / 0x3f`
  in a full 512 KB scan of the high half (stride-scan found nothing) and a 1 MB scan for
  `{addr, bank}` triplets pointing near text-record starts found only the two tables plus
  coincidental matches.

## Record slot layout (observed)

Every sampled slot begins with an identical 10-byte prologue followed by the text:

```
00 00 00 00 00 00 78 70 00 00 <TEXT... E9> [00 padding]
|<--- 10-byte prologue --->|
```

- The prologue's bytes `78 70` / `bc 54` (build 2: `15 70`, `78 70` variants) look like a
  script-VM operand block (opcode-ish `78`/`bc` + `70`/`54`).
- Text is padded with `$EF` or `$00` to fill the slot.
- Slot capacity: bank 07 ≈ 43 bytes; banks 0a/0c/10 ≈ 86 bytes (two-line records).

Example slot headers:
```
bank 07 $4501:  00 00 00 00 00 00 78 70 00 00 b1 ec a4 a5 a6 a7 …
bank 07 $452c:  00 00 00 00 00 00 78 70 00 00 ef 0f 1e 2d ef ef …
bank 0a $4323:  00 00 00 00 00 00 bc 54 00 00 ef 3e 12 21 22 29 …
```

## Text VM (bank 0, `$3730`–`$383c`)

Dispatch on token byte read from the script stream:

| Token | Action |
|---|---|
| `$FF` | end of record → sets `$cb52 = $FF`, clears `$cb53/$cb54/$cb55`, restores bank, `ret` |
| `$FE` | control path (`$3804`): script sub-record / wait |
| `$EF` (space), `$E9` (line end) | formatting path, no glyph |
| `< $A0` | letter/digit path (`$3785`): `$cb63 = a | $80`, `$cb64 = $FF`, table-driven glyph lookup via window `$9c22` + call `$0a93` |
| `>= $A0` | farcall **bank `0x17` window `$7739`** (`call $217f`) to draw the glyph |
| all | cursor `$cb53/$cb54 += 1`, record index `$cb55 += 1`, bank restored from HRAM `$ffa4` |

`$217f` = universal farcall trampoline (save bank from `[$4000]` → HRAM `[$ffa4]`, select
target bank in A via `[$2100]`, `jp hl`, restore bank on return).

The token `$44` appears mid-word as an **author-marked word-break point** ("COL_{44}DER"
= "COLDER", "HURRI_{44}CANE"): it is invisible in records, and the text engine draws the
hyphen glyph (font tile `$44` = `-`) there when a line breaks at that point. `$08` is a
bold-capital I, and sentence-emphasis uses the `$00–$19` "bold A–Z" range (bold A=`$00 …`
bold Z=`$19`, rendered as bold caps; used at sentence starts and for speaker names).

## LCDC-STAT text dispatcher (`$3311`, interrupt vector `0x48`)

`0x48` (LCDC STAT interrupt) → `jp $3311`:

```
$3311: reti-less banked dispatcher:
$3315:   ld a, [$c0a7]          ; current text/scene job index (WRAM)
         ld e, a; add a,a; add a,e   ; ×3
         ld d, $00; ld hl, $3345     ; job dispatch table (bank 0)
         add hl, de; ld a, [hl+]; ld h, [hl]; ld l, a   ; {addr, bank}
         (switch bank via $2100, tail-jump via $3344 = `e9` jp (hl), restore bank, spin VBlank)
$3345:   13 × 3-byte {addr, bank} entries:
           (0x0033, b00) ×3  — no-op default
           (0x7855, b1F) ×8  — bank 0x1F jobs
           (0x4730, b06), (0x435c, b1D), (0x4675, b1D) — scene jobs
```

- `$c0a7` is incremented in bank `0x06` (`0x18da3`) and mirrored in bank `0x26` (`0x98cea`),
  read+tested in bank `0x00` (`0x1cff` checks against `$26/$27/$29` and bails),
  and queues "glyph job" dispatch per hblank.
- This is the **hblank glyph pump** for dialogue boxes: each STAT interrupt draws the
  next glyph/segment, one per scanline — the standard GB text-box technique.
- `0x40 (VBLANK) → call $04c7; reti`, `0x50 (TIMER) → jp $33f9`, `0x60 (JOYPAD) → reti`.

## Implications for the randomizer

1. **Text is slot-bounded.** Dialogue can be rewritten freely inside a slot
   (`00`-padding at the end); no pointer-table fixups are needed as long as the new text
   fits the slot. Longest required slot per string ≤ 43/86 bytes including `E9`.
2. **Records are enumerable** via the two bank-0 tables (~146 records in bank 07, ~160 in
   banks 0a/0c/10) — plus any records reached only via `FE`-delimited control paths
   (sub-records). Count may drift; a ROM-scan extractor with the slot stride is more robust.
3. **Twin rule:** the high half contains the *same* USA text relocated (no slot tables found
   there). Any record edit that is visible in the low build is also visible in the high
   build **only if the high build is ever executed** — unverified; safest to patch both
   (done automatically if we patch by content match). Bank `0x20` (CGB VRAM bootstrap)
   must NOT be touched.
4. **Glyph codes:** A–Z = `$1A–$33`, bold A–Z = `$00–$19`, 0–9 = `$34–$3D`,
   space = `$EF`, `[` = `$3E`, `]` = `$3F`, `!` = `$40`, `?` = `$41`, `'` = `$4B`,
   `.` = `$4C`, `,` = `$8E`, `'s` = `$53`, end-of-line = `$E9`. No lowercase in dialogue.
   See text-encoding.md for the render-verified table (2026-11-13 correction).

## Open items

- Exact semantics of the slot prologue bytes (`78 70` vs `bc 54` vs `15 70`).
- Sub-record `FE` operand headers (`FE … 78 70 00 00`).
- Window `$9c22` glyph-lookup table format (consumed by `$0a93`).
- Which mode runs the high half (CGB VRAM bootstrap is confirmed for title/boot only).
