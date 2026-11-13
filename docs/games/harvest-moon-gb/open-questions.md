# Open questions / TODOs

Ordered by priority for the randomizer. Items resolved on 2026-11-13 are marked ✅.

1. **High-half fate.** ✅ Partially resolved: bank `0x20` (CGB VRAM bootstrap) is REQUIRED —
   FF-fill kills boot. The rest of the high half (`0x21–0x3f`) boots fine through
   name entry but is unverified deeper into gameplay. Full CGB-mode playthrough FF-kill test
   needed (intro → first dialogue → sleep → shop). If the high half proves unused past
   name entry, it is 448 KB of potential payload space.
2. **Record operand headers** (`78 70` / `bc 54` / `7d 01` / `da 61` per build + `FE … 78 70 00 00`):
   semantics unknown. Appears constant per build/bank — likely a VM jump-table selector or
   portrait/sound slot. Decode by reading the `$3804`/`$3813` control path fully.
3. **Name entry screen** ✅ Resolved 2026-11-13: the **END tile** (row 5, col 11 — green/tan
   box, bottom-right of the grid) confirms the name; A on it advances to the
   "You are a Male? Female?" gender screen. Start and B are no-ops; A on the `[` cell is a
   no-op; A on out-of-range col 12 backspaces one character. Pure-button path:
   `Down ×5`, `Left` (wrap), `A`. Cursor is WRAM `$cd65` (col) / `$cd66` (row);
   `$c000/$c001` are derived pixel coords. Full details: name-entry.md.
4. **Shop/item tables** location (banks `0x0a/0x0c/0x10`?) for economy randomization.
5. **Crop/animal growth tables** for crop/growth randomization.
6. **Save compatibility**: decide whether randomizer guarantees old saves load (needs SRAM
   checksum-aware patching; RTC + `$a000` marker code at `$0ce0–$0d90`).
7. **JP vs US symbol drift**: SRAM/WRAM verified identical; other JP names must be
   re-derived.
8. **Checksums**: policy for header/global checksums after edits (keep valid to avoid
   flashcart/emulator warnings — global checksum is big-endian, header checksum is XOR).
9. **`$9c22` glyph-lookup table** (window `$9c22`, consumed by `$0a93`): format unknown.
10. **Dispatcher `$c0a7` semantics** ✅ REFRAMED: LCDC-STAT hblank glyph pump; index into
    bank-0 table `$3345` (13 × `{addr, bank}`); `0x48` vector → `jp $3311`; incremented in
    bank `0x06` at `0x18da3` (twin `0x98cea`), read/tested in bank-0 at `0x1cff`.
11. **Twin text record relocation**: records in the high half have the same content at
    arbitrary shifted windows (one observed delta −0x63); no triplet slot tables exist there.
    Patch by content match if high-half support is ever required.
12. **Bank-0 slot operand semantics** (`78 70` vs `bc 54` vs `7d 01` vs `da 61`): the two
    bytes differ per bank but are constant per bank — likely a script-VM tag or text-box
    style. Decode via the `$3804` control path or by editing and observing.
13. **Pet name rendering path**: pet name at `$b8dc-$b8df` is NOT in the `$774c` farcall
    table. Find how (or whether) the pet name is ever drawn as text.
14. **Farcall slots `$a4-$ee`** (all point at SRAM: `$b912/$b913`, `$ba4f-$ba52`, …): map
    these fields — they are likely money/year/relationship/quest bytes rendered into text.
15. **Bank 0x1b/0x3b record operand**: `46 42 4a` vs `46 33 52` (varies per record) — purpose unknown.
16. **$a000 marker semantics**: `$0e` after new-game init vs `'Y'`/`'y'` on real saves.
17. **Line-break hyphen**: engine draws font tile `$44` (`-`) at author-marked `$44` points;
    confirm the auto-hyphen mechanism and whether records contain explicit `-` glyphs anywhere.
18. **Opening-tour SRAM deltas** (12 bytes written during the grand tour):
    `$b883`, `$b88b`, `$b8cb` (0a→09), `$b90c-$b90d` (00 00→22 d8), `$b914` (slot `$b1`),
    `$b919-$b91a` (slots `$c8/$c9`), `$b93c` (00→19), `$ba53-$ba55` (slots `$ec/$ed/$f0`).
    Which ones are clock/RNG/checksum/counters, and which are the farcall fields the text VM
    actually renders? Cross-check by walking to the first bed-save and see which values move.
19. **Money storage**: HUD shows 500G at first control; money is NOT at `$b90c-$b90d`
    (22 d8). Locate the 16-bit money field (search SRAM diff for $01F4).
20. **First-control position**: farmer stands in the farmhouse at Spring 1 Mon 6 AM;
    SELECT toggles the clock/money HUD. Does leaving via the door trigger Day 1 events
    or a forced sleep?
