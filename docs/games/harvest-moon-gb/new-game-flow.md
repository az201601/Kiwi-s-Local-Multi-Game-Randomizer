# New-game flow (US build) — verified end-to-end 2026-11-13

Headless (PyBoy) verification from fresh boot through the intro cutscene. All steps
reproducible; see `emulator-workflow.md` for the boot recipe and `name-entry.md` for grid
mechanics. Frame numbers are from the `rtc_lock_experimental(True)` fresh-state timeline.

```
Victor copyright → Natsume logo → landscape truck-gate cutscene (~f420–1440)
→ white fade → TITLE (~f1500)                              [Start, not skippable]
→ Name? grid #1 (FARMER name)                               [Down×5, Left, A = END]
→ "You are a Male? Female?"      (▶ Male default)           [A = confirm]
→ "Which do you like? Dog / Cat" (▶ Dog default)            [A = confirm]
→ Name? grid #2 (PET name)                                 [same END confirm]
→ ★ FULL 8 KB SRAM SAVE TEMPLATE IS WRITTEN HERE ★
→ intro cutscene: green field + winged messenger, multi-page dialogue
   ("…AAAA… Wake Up, AAAA. I have a favor to ask of you.
     My ranch has been neglected because I did not have a successor!…
     …surPass me as Ranch Master!! …Good Luck future Ranch Master! …")
```

## Questionnaire screens

> Harness note: each of the two question screens eats the first `A` issued while still
> transitioning from the previous screen. Allow ~100 f after each confirm before pressing
> `A` again, or press twice — otherwise the flow silently desyncs.


- Both name grids are the same screen/mechanics (4-char limit, END tile = row 5 col 11,
  cursor `$cd65/$cd66`, A on END confirms). The farmer grid is shown first with an empty
  name; after the gender/pet questions the game re-enters the grid for the pet name.
- **Confirmed name length: 4 characters** (both grids).
- Gender screen: `Male` selected by default (`▶` pointer); A confirms; A/B semantics of the
  pointer untested (Up/Down presumably). Sprites shown on the right.
- Pet screen: identical layout; `Dog` default; A confirms. The choice is presumably stored
  in SRAM (`$a008`-ish region — not decoded; pet name storage IS decoded, below).

## SRAM write timing (verified by watching `$a000` + nonzero-byte count)

| Stage | `$a000` | nonzero bytes |
|---|---|---|
| at farmer grid | `$00` | 0 / 8192 |
| farmer typed | `$00` | 0 / 8192 |
| farmer END confirm | `$00` | 0 / 8192 |
| gender A | `$00` | 0 / 8192 |
| pet A | `$00` | 0 / 8192 |
| pet typed | `$00` | 0 / 8192 |
| **pet END confirm** | **`$0e`** | **2761 / 8192** |

The entire new-game SRAM template is written at the pet-name END confirm. It includes a
"Back_Up_Check" signature at `$bff0-$bffc` (raw ASCII `Back_Up_Check!#$%` — the save-verify
marker the JP doc calls the backup checksum area) and the `$a000`-family save-slot marker
set to `$0e` (not `'Y'`/`'y'` — the save-complete value appears only on later saves, TBD).

## Name storage (SRAM)

| Address | Content |
|---|---|
| `$b8dc-$b8df` | **Pet name** (4 bytes) |
| `$b8f4-$b8f7` | **Farmer (player) name** (4 bytes) |

Letter encoding = VM font index (rows map contiguously):

| Grid cells | Codes |
|---|---|
| Row 0 (A–M) | `$00-$0C` (bold A–Z) |
| Row 1 (N–Z) | `$0D-$19` (Z = `$19`) |
| Row 2 (a–m) | `$1A-$26` |
| Row 3 (n–z) | `$27-$33` (z = `$33`) |
| Row 4 (0–9) | `$34-$3D`; `!`=`$40`, `?`=`$41`, `♥`=`$42` |
| Row 5 (symbols) | `[`=`$3E` `]`=`$3F` `…`=`$43` `_`=`$46` `↑`=`$47` `←`=`$48` `↓`=`$49` `→`=`$4A` `—`=`$44` `~`=`$45` ; `SP` (space cell) is **not typable** (A does nothing) |

Empty slot sentinel = `$D8`. Typed-but-uncommitted slots keep the glyph codes; confirming
writes them verbatim via the END path. The name bytes bypass the VM opcode dispatcher and
are drawn as raw font indices, so name glyph identities differ from record-opcode meanings
(`$44` in a record = word-break hint / zero-width; in a name = `-` glyph).

## Name rendering (text VM)

Regression (2026-11-13): short names work end-to-end — farmer `AB`, pet `CD`; SRAM
`$b8f4-$b8f7` = `00 01 d8 d8`, intro page 1 renders `AB   ... Wake UP,` with `$D8`
drawn as spaces. END recipe from cursor col c: `Down ×5`, `Left ×(c+1)`, `A`
(Left from col 0 wraps straight to END).

The intro dialogue contains the literal farcall bytes `a0 a1 a2 a3` (twice) — the
player-name token. Handler at bank `$17`, CPU `$7739`:

```
$17:$7739:  ld a, b          ; farcall operand ($A0-$A3)
             sub  $a0        ; -> slot index 0-3
             sla  a          ; *2
             ld   hl, $774c  ; pointer table (CPU $774c, bank $17)
             add  a, l / adc a, h
             ld   a, (hl+)   ; lo byte of SRAM pointer
             ld   c, a
             ld   a, (hl)    ; hi byte
             ld   b, a       ; BC = &name[slot]
             ret
```

Table at `$17:$774c` (LE pointers, slots `$a0-$ee`, then `$00fd` sentinel):
`a0→$b8f4, a1→$b8f5, a2→$b8f6, a3→$b8f7` (the farmer name — verified by index math).
The remaining slots point at other SRAM fields (`$b912/$b913`, `$ba4f-$ba52`,
`$ba44-$ba48`, `$b915/$b918/$b914/$b928-$b92e/$b933-$b937`, `$cc00-$cc03`, `$cc13/$cc14`,
`$b919-$b92c`, `$cc9d-$cca3`, `$ccad-$ccb1`, `$cbf7-$cbfd`, `$ba53-$ba5f`, `$b917`) — all
SRAM-resident; the exact fields are undecoded (open question). The pet name (`$b8dc-$b8df`)
is NOT in this table; its in-dialogue rendering path is unknown (probably never rendered as
text, or via a different table/opcode — open question).

Name token locations in US text (bank 0a): the intro line at `0x29102` twin `0xa9108`
(record `0x290bc`, twin `0xa90bc`). 30 records reference `<N0>`-`<N3>` in the extracted
corpus (banks 07, 0c, 10, 27, 2c, 30 — both halves). `extract_text.py` now decodes
`$a0`-`$a3` as `<N0>`-`<N3>` placeholders so such records no longer split.

## Post-cutscene: guided farm tour (auto-walk, A advances)

After the spirit disappears (green fade), the game enters a scripted
"grand tour" of the farm with the blue-clad rancher, ending at the farmhouse.
Text advances with A; scenes are entered with a white fade; the tour is
deterministic. Pages (order verified 2026-11-13):

- farm gate (shipping box): `This is the Shipping Box to ship crops. The trader will
  … Pick up Your shipment at 5PM.`
- silos (exterior): `these two towers are Silos used to store fodder. On the right of
  the silos is the Chicken Coop and…`
- chicken coop (interior, cow visible): `…Brush Your cow everyday with… so, your cow
  will Give You a lot of milk. Of course do not for-get to feed them everYday. Make
  sure that you feed th… Nevertheless. Never for-get.` (uses `love and affection`
  phrasing for the cow relationship)
- tool shed (interior, tools on wall): `This is the Shed where Your items are kept.
  I am a little tired…`
- farmhouse (interior): no text — **first frame with free control**. Player starts
  inside the house at 6 AM, Monday, Spring 1st, 500G. **SELECT** toggles the
  season/time/money HUD (`01 Spr-ing Mon 00 6AM 500G`).

Words are rendered with odd mid-word capitalization in-game (`Shipping`, `Ranch`,
`Place`, `Master`, `Cow`/`cow`…) — this is the game's actual type style, matching
the `<N0>` lowercase rendering behavior noted above.

### SRAM bookkeeping written during the tour (12 bytes)

`$b883` ff→00, `$b88b` 00→01, `$b8cb` 0a→09 (decrement), `$b90c` 00→22, `$b90d` 00→**d8**
(the empty-slot sentinel), `$b914` 13→34 (farcall slot `$b1`), `$b919` 5b→58 (slot `$c8`),
`$b91a` 63→59 (slot `$c9`), `$b93c` 00→19, `$ba53` fe→35 (slot `$ec`), `$ba54` 64→5a
(slot `$ed`), `$ba55` 65→57 (slot `$f0`). Bookkeeping fields, not save-header; semantics
undecoded — see open-questions.md. Names (`$b8dc/$b8f4`) are unchanged by the tour.

## Intro cutscene (first ~20 pages)

Green gradient field, winged messenger (the "ranch spirit"). Text advances with A; each
page ~2 lines. Verified pages (A-press each):

1. `<NAME>... Wake Up, <NAME>. I have a` ▼
2. `favor to ask of you. My ranch ▼`
3. `has been ne-glected becaus` ▼
4. `I did not have a successor! So, ▼`
5. `I want YOU to take my Place ▼`
6. `to restore my ranch and ▼`
7. `surPass me as Ranch Master!! ▼`
8. `Unfor-tunately. I can not help ▼`
9. `You because I am a sPirit. ▼`
10. `At the end of winter. I can ▼`
11. `come and check UP on you. ▼`
12. `I must be going now. Good Luck ▼`
13. `future Ranch Master! ▼`
14. `Oh. I for-got to tell You that ▼`
15. `the croPs will die when the ▼`
16. `seasons chante! Be sure to ▼`
17. `check the date on the ▼`
18. `calendar!! It is essential for ▼`
19. `growing crops! ▼`
20. (empty text box; the spirit ascends and disappears — graphics-only)

The messenger then flies up and the scene fades to the shipping-box tour below.
(Word-internal hyphens at line breaks are engine auto-hyphens drawn at
author-marked `$44` points.)

## Name rendering paths (three distinct ones)

1. **Dialogue VM farcall** — records embed `$A0-$A3`; the text VM's farcall handler
   (`$17:$7739`, pointer table `$17:$774c`) resolves them to SRAM name bytes
   (`$b8f4-$b8f7`), drawn inline as raw font indices. This is how `<NAME>` appears
   in dialogue.
2. **Status screen (bank 9 @ CPU `0x5867`, twin in bank `$29` @ CPU `0x580e`)** —
   copies farmer name `$b8f4-$b8f7` → tilemap `$9821` (4 cells), 3 bytes
   `$b8ef-$b8f1` (money + pad) → `$ccca-$cccc` (WRAM bank 3 display copy),
   stages farmer name into WRAM bank 3 `$cdbf` (leading `$05`/`$04` + 4 name
   bytes) via sister builders at `$7c4d3`/`$7d829` (bank 30) and
   `$fc3d6`/`$fd718` (bank 63), and copies pet name `$b8dc-$b8df` → tilemap
   `$98e6`. Conditional icon tiles at `$98a6-$98a9` depend on `$b8db`/`$b8ea`
   (attribute bytes, both `0` at new game). `$22f8` = block copy (`ld a,(hl+);
   ld (de),a`), `$0da9` = hblank wait. **Pet name never goes through the
   dialogue VM.**
3. **Name-grid typed rendering** — the typed-glyph tile id is staged at
   `$9800+0x04a` (shown in the grid), driven by WRAM `$cd65/$cd66` (col/row).

## Harness notes

- The whole opening (boot → intro) is deterministic under `rtc_lock_experimental(True)`;
  tutorial flow length ≈ 1500 f boot + questionnaire + ~500 f intro text latency.
- PyBoy save/load-state works (`pb.save_state(fileobj)` / `pb.load_state(fileobj)`) — use it
  to skip boot during harness iterations; states are per-RTC-lock.
- SRAM init is a one-shot at pet-name END; save files (`.ram`) made after that point carry
  a complete template.
