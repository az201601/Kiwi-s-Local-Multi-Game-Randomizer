# ROM identification & cart structure

Tool: `games/harvest-moon-gb/tools/rominfo.py`

## Vital signs

| Field | Value |
|---|---|
| Size | 1,048,576 bytes (1024 KiB), 64 × 16 KiB banks |
| MD5 | `498c0a50a5e5cde16127617a97ad6162` |
| SHA-256 | `54fe95af5ded33631db84b6ba4ecc4a86f9f7528c9c56ca678150a3246d49623` |
| Title | `HARVEST-MOON GB` (+ `0x80` appended at 0x143) |
| CGB flag | `0x80` (CGB-compatible, runs in SGB/DMG mode) |
| SGB flag | `0x03` (SGB enhancement) |
| Dest | `0x01` (overseas / US) |
| Cartridge type | `0x10` — **MBC3 + TIMER + RAM + BATTERY** |
| ROM size | `0x05` → 1024 KiB (64 banks) |
| RAM size | `0x02` → 8 KB SRAM, single bank |
| Licensee | old `0x33`, new `E9` |
| Version | `0x00` |
| Header checksum | `0x6f` ✓ |
| Global checksum | `0x834c` ✓ (stored **big-endian** at $014E-$014F) |
| Nintendo logo | valid ✓ |
| Entry | `$0100: nop; jp $0150` |

## The cart really is MBC3 + RTC

Confirmed by code, not just the header byte:

- RAM enable: `ld a,$0a; ld [$0000],a` (and `ld [$1fff],a`) — 4 sites.
- RTC latch: `ld a,$01; ld [$6000],a` (2 sites, e.g. `0x01a0`).
- RTC register select: `ld a,$08..$0c; ld [$4000],a` (8 sites) — selects RTC seconds..latch.
- RTC shadow vars in HRAM/SRAM: `$cbF1-$cbF6` etc.; time/date copies at WRAM `$b880-$b885`
  (`sTimerSeconds/Minutes/CurrentHour/CurrentDay/CurrentSeason/CurrentYear` — identical to the
  Japanese build's addresses) and display copies at `$cbdd-$cbe1`, money display at `$ccca-$cccc`.
- SRAM checksum/RTC handling present around `$0ce0-$0d90` (new-game RTC reset writes 0 to RTC regs).

**Implication for randomizer:** the game uses the RTC chip for its in-game clock/season pacing.
Emulators need RTC (PyBoy supports it, use `rtc_lock_experimental` to freeze it for deterministic
testing/seeding). A randomizer probably wants to keep RTC intact; wiping RTC semantics would break
the day/season loop.

## Bank tags — every bank is prefixed with its bank number

Every bank **except bank 0** begins with its own **absolute** bank number
(bank 0x01 → first byte `0x01`, …, bank 0x3f → `0x3f`). Bank 0 begins with `0xc3`
(the `jp` opcode of the entry jump). Verified programmatically: all 63 banks match.

The engine **reads `[$4000]`** to obtain the current bank number at runtime (e.g. in the
farcall trampoline `$217f` and the RTC helpers). That is *why* the tags exist.

## Farcall convention (everywhere in the codebase)

```
    ; call HL in bank A, restoring the caller's bank afterwards
Call_000_217f:
    push bc
    ld  b, a          ; target bank
    ld  a, [$4000]    ; current bank (the tag byte)
    ld  c, a
    ld  a, b
    ld  [$2100], a    ; select target bank  (any $2100-$21FF address works on MBC3)
    ld  a, c
    pop bc
    push af
    call  Call_000_2194   ; jp hl — the jump itself
    pop af
    ld  [$2100], a    ; restore caller's bank
    ret
```

Notes:
- Bank switching writes go to `$2100`–`$21FF` (usually `ld [$2100],a`) instead of `$2000` —
  any write in `$2000-$3FFF` decodes as the MBC ROM-bank register. The US build uses `$2100`
  almost everywhere (only ONE `ld [$2000],a` exists, in the boot stub at `$015d`).
- Inline farcalls are common too: `ld a,[$4000]; ldh [$a4],a; ld a,<bank>; ld [$2100],a; …;
  ldh a,[$a4]; ld [$2100],a`.
- Restores can also go through a helper at `$2195` (uses HRAM `$ffa4`).
- Writes to `[$4000]` with values `$01`,`$08`-`$0c` are **RTC register select**, NOT ROM banking.
  `[$4000]` is read for the bank tag; writing `[$4000]` selects an RTC register. Both coexist.
- Bank 0's tag byte is `0xc3` (the entry `jp` opcode) — restoring bank 0 through the `[$4000]`
  path writes `$c3` to the MBC bank register. That is "fine" on hardware/emulators only because
  bank 0 never actually farcalls at runtime (game logic runs with bank ≥ 1 mapped; bank 0 =
  boot + interrupts + trampoline + fixed data). Treat bank-0 inline farcall-restore sites found
  statically as **dead code until proven otherwise** (they appear to never execute; the live
  ones all sit in bank ≥ 1 or restore from `[$ffa4]`).
- WRAM banks: `$d000-$dfff` = WRAM bank 1 (classes/entity data) — entry does `ld [$dff7],a`.
  HRAM usage: `$ffa4/$ffa5` = farcall bank save scratch, `$ff80-$fffe` cleared at boot, stack at `$dff7`.

## THE TWIN BUILD — most important structural discovery (updated 2026-11-13)

The 1 MiB ROM contains **two full builds of the same USA game**, side by side:

- **Build L (low half): banks `0x00–0x1f`** — the primary build. Bank-0 record tables
  point here; boot/logo/title/name-entry execute here (banks 0x01/0x07/0x0b/0x10/0x13/0x17/
  0x1d/0x1e observed mapped) plus farcalls to bank `0x0f` (asset decompressor) and
  bank `0x1d`. This is the build PyBoy executes end-to-end.
- **Build H (high half): banks `0x20–0x3f`** — a *relocated re-link* of the same source with
  the same linker map; identical USA text content at shifted windows and no fixed-slot
  record tables. Same English dialogue present (verified byte-level: "GRANDPA'S",
  "(SHIPPING BOX)", "HERE IS 75G…" all exist in bank `0x27` at different offsets).
  Banks `0x1b↔0x3b` are byte-identical; other pairs differ by relinking (pointer/text deltas).
- Exception: **bank `0x20` is NOT a build bank**. It is a CGB-support bank (VRAM-bank
  `$ff4f` switching + `$9800` tile DMA + calls into bank `0x31`/`0x20` windows — classic
  CGB hi-color tile bootstrap) reached by farcall `$6d48` at boot. **Filling bank `0x20`
  with `0xFF` crashes boot** (blank title, wild PC). Banks `0x21–0x3f` passed the same
  FF-kill test (boot + title + name entry render fine), so the rest of Build H is
  unverified beyond the name-entry screen — do not assume it is unused.

Evidence (all reproducible with `rominfo.py`/`bankscan.py`/PyBoy):

1. Banks `0x1b↔0x3b` byte-identical except tag; `0x10↔0x30` 4.3%; `0x1f↔0x3f` 6.8%;
   `0x07↔0x27` 7.2%; `0x0a↔0x2a` 47%; `0x0c↔0x2c` 64.6% — code relocates hard, data less.
2. Identical English strings exist in both halves at different file offsets
   (e.g. "ORIGIN OF FIRE" at `0x1c648` and `0x9c5e5`, delta −0x63).
3. Bank-0 record tables (`0x03421`, `0x03601`) reference only low-half banks — no triplet
   runs exist anywhere in the high half (full stride-scan for banks 27/2a/2c/30/3b/3f).
4. The two `reti` interrupt handlers preceding the tables switch to bank `0x1f`/`0x06`
   via `$2100` — the low build's record lookup path. No high-half bank appears in either
   vector path.
5. FF-kill: filling `0x20` crashes boot; filling any other high bank boots fine — but only
   through name entry (deeper gameplay untested).
6. CGB header flag is `0x80` and PyBoy runs the CGB boot ROM; bank `0x20` performs
   `$ff4f` VRAM bank switches at boot — CGB VRAM bootstrap.

Why two copies? Not yet proven. Candidates: two quality/region variants merged into one
mask ROM; SGB-vs-CGB paired builds; accidental duplication from the build system. What IS
proven: the low build is the one executed in our tests, and bank `0x20` is the only
high-half bank required for boot. The rest of the high half is unverified past the name
entry screen — treat it as live until disproven gameplay-wide.

**Randomizer strategy implications (important):**

1. Patch the low build for any dialogue/table change; also apply identical edits at the
   relocated twin location when the same record exists (patch by content match, not by
   offset — twin windows shifted arbitrarily, e.g. `-0x63`).
2. **Never touch bank `0x20`** (CGB VRAM bootstrap — boot dies).
3. Any free-space hunting for payload must exclude banks `0x20–0x3f` until a full
   CGB-mode playthrough FF-kill test proves them dead.
4. Verification must boot and play the game headless (PyBoy) and soak-test all patched paths.
5. Keep record slot lengths unchanged where possible; the low half has no free space for
   moved pointer tables anyway (all banks are tagged in-place).
