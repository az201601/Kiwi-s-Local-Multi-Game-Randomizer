# RAM map (US build) — verified against the Japanese disassembly

Sources: direct ROM/dump analysis + cross-check against H0smax/HMGB-Disassembly
(JP build) symbol addresses. Most JP WRAM symbols land 1:1 in our ROM at the same
addresses, which strongly indicates the US build kept the same linker layout.

## SRAM (`$a000-$baff`, 8 KB, battery-backed)

| Address | Symbol (JP ref) | Contents | Conf. |
|---|---|---|---|
| `$a000` | sSaveMarker | save-complete marker ('Y'/'y' after write) | high |
| `$a010`+ | per-slot expansion data | 3 slots × 0x40 bytes (cursor, area flags, expansion) | high (3 copies at 0xa010/0xa060/0xa0b0/0xa100 …) |
| `$a100-$a10f` | sHeroName | hero name (10 chars + padding) | high |
| `$a111-$a118` | sWifeName | wife name | high |
| `$a120-$a128` | sPetName? / dog name | pet name area | mid |
| `$a130` | sWeatherToday | current weather byte | mid |
| `$a135` | sCurrentSeason | season (0 spring..3 winter) | mid |
| `$a140` | sCurrentDay | day of month | mid |
| `$a145-$a146` | sCurrentYear | year (16-bit) | mid |
| `$a149` | sMoneyFlag | money presence flag | mid |
| `$a150-$a154` | sMoney (3 bytes BCD) | current money | mid |
| `$a161-$a162` | sSpouse/heart counters? | relationship | low |
| `$a175-$a17a` | — | raw SRAM dump used by checksum code | — |
| `$a1e7` | — | related to "about $a1e9" money display | low |
| `$b000-$b6ff` | per-slot records? | base offsets 0x1bd0/0x1bfe/0x1c2c … | low |

(Full raw dump inspection in `work/sram_*` when generated; JP reference is authoritative
for offsets and matches the "0x1bd0"-style wording used in the wiki's save notes.)

## WRAM (`$c000-$dfff`, +WRAM bank 1 at `$d000-$dfff`)

Verified symbols used by the text VM / RTC:

| Address | Symbol / role | Notes |
|---|---|---|
| `$c000` | name-grid cursor Y (`$29 + $10*row`) | derived; game recomputes on input |
| `$c001` | name-grid cursor X (`$23 + $8*col`) | derived; same |
| `$cd65` | name-grid cursor column (0–12) | authoritative for input; END = 11 |
| `$cd66` | name-grid cursor row (0–5) | |
| `$cb53/$cb54` | script cursor (lo/hi) | offset into script stream |
| `$cb55` | record/sub-record index | incremented per token |
| `$cb56` | flags (bit checks `$cb56 & $10` gates glyph draw path) | |
| `$cb62`/`$cb63` | glyph-format flags (a |= $80 path) | |
| `$cb64` | init `$ff` | |
| `$cb6e/$cb6f` | post-record callback (lo/hi) passed to `$217f` | |
| `$cb78` | gate (`$3842`) | |
| `$cb8x` | more flags | |
| `$cbdd-$cbe1` | clock (hour,min), day?, season, year (display copy) | |
| `$ccca-$cccc` | money (BCD-ish display copy) | |
| `$b880-$b885` | RTC shadow: sec,min,hour,day,season?,year | direct mirrors of JP sTimer* |
| `$b886-$b8xx` | RTC sDayOfWeek? | |
| `$b8dc-$b8df` | **pet name** (VM font codes, 4 bytes) | new-game; inline render unverified |
| `$b8f4-$b8f7` | **farmer (player) name** (VM font codes, 4 bytes) | new-game; farcall slots `$a0-$a3` point here |
| `$b912/$b913` | unknown 2-byte field (referenced by farcall `$a4-$a5`) | init `6d 6e` |
| `$bff0-$bffc` | `Back_Up_Check!#$%` ASCII signature (save-verify) | written by new-game SRAM template init |
| `$a000` | save marker; `$0e` after new-game init (not `'Y'`) | `'Y'`/`'y'` presumably only after bed-save |
| `$cb17-$cb1f` | checks (`ret nz` chain) — message-busy flags | |
| `$c0a7` | dispatcher index (banked scene/script jump; table at `$3345`) | semantics unknown |
| `$cccb-$cccd` | extra display bytes (newer additions) | |
| `$d000-$dfff` | WRAM bank 1 — classes/entity graphics staging | entry sets bank 1, `$dff7` = stack |
| `$d800+` | asset-decompression output area (via bank-$0f/$0f-like farcall chain) | |
| `$e000-$fdff` | echo RAM (mirrors $c000-$ddff) | |

## Notes / gotchas

- HRAM is cleared at boot except reserved pages; HRAM `$ffa4/$ffa5` = farcall bank-save scratch;
  HRAM `$ffb0-$ffc0` hold RTC latch scratch during RTC updates (seconds/min/hour/day copied there).
- The US build keeps the **same layout as the JP build** almost everywhere (all SRAM addresses
  checked match). Divergence appears in relocated/added content (banks with `$b000`-area data
  at different absolute offsets).
- **New-game SRAM template**: the entire 8 KB save is initialized in one go at the
  pet-name END confirm (2761 nonzero bytes). Before that, SRAM is all `$00`. See new-game-flow.md.
