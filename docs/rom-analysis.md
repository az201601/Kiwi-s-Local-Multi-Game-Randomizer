# Harvest Moon GBC — ROM Analysis

## ROM Identity

| Field | Value |
|---|---|
| File | `Harvest Moon GB (U) [C][!].gbc` |
| Size | 1 MiB (64 banks × 16 KiB) |
| Title | `HARVEST-MOON GB` |
| CGB flag (0x143) | `0x80` (CGB only) |
| Cartridge type (0x149) | `0x02` = MBC1 + RAM |
| Entry point | `NOP; JP $0150` |

## Header Corruption ⚠️

The dump's header is **partially corrupted** (likely a bad dump or patched ROM):

- **Nintendo logo**: 9 bytes differ from the standard logo
  (`0x109`, `0x128`, `0x12A`, `0x12C`–`0x131`)
- **Header checksum**: stored `0x4C`, computed `0x32` → mismatch
- **Suspicious fields**: `0x146=0x03`, `0x147=0x10`, `0x148=0x05`,
  `0x14A=0x01` (claims 64 KiB, file is 1 MiB), `0x14B=0x33` (invalid),
  `0x14E=0x83` (version 131)

**Impact**: The game code itself is intact and valid. Real hardware would
reject the bad logo at boot, but emulators ignore it. For the randomizer
we will **repair the header** (restore logo + recompute checksum) when
producing patched ROMs.

## Memory Mapper

- **MBC1** (per header `0x02`), 1 MiB ROM / up to 32 KiB battery RAM.
- ROM bank switching via `$2000`–`$3FFF` (write to `$2100` observed).
- RAM enable via `$0000`, RAM bank via `$6000`.
- WRAM bank switching via `SVBK` (`$FF70` / `LDH ($8D)`), bank 2 used at boot.

## Bank 0 Layout (0x0000–0x3FFF)

| Address | Contents |
|---|---|
| `0x0000` | RST `00H` → `JP $2160` (jump-table dispatch) |
| `0x0008` | RST `08H` → `JP $216C` (far-call dispatch) |
| `0x0010`–`0x0037` | RST `10H`–`38H` → `RET` (unused) |
| `0x0038` | RST `38H` → stack-clear helper (`ADD HL,SP; LD HL,SP+0; LD A,0; LD (H+),A; RET`) |
| `0x0040` | VBlank IRQ → `CALL $04C7; RETI` |
| `0x0048` | LCD IRQ → `JP $3311` |
| `0x0050` | Timer IRQ → `JP $33F9` |
| `0x0058` | Serial IRQ → `JP $3408` |
| `0x0060` | Joypad IRQ → `RETI` |
| `0x0061` | Small utility routine (called from boot) |
| `0x0100` | ROM header |
| `0x0150` | **Boot / startup code** |
| `0x0217` | Secondary init entry (reached from main loop) |
| `0x026D` | **Main loop**: polls `$C0A6`, `JP Z,$026D`, else `JP $0217` |
| `0x0278` | SGB detection branch |
| `0x04C7` | VBlank handler |
| `0x0673` | Calendar / date system (day/month/year counters) |
| `0x0763` | Per-year data table (3 bytes/entry) |
| `0x2160` | RST 00 dispatcher (jump table) |
| `0x216C` | RST 08 dispatcher (far call, 3-byte table entries) |
| `0x217F` | **Banked call**: `HL`=addr, `A`=bank → call, restore bank |
| `0x2194` | `JP (HL)` trampoline |
| `0x2242` | Copy 10-byte HRAM routine from `$2250` to `$FF80` |
| `0x2250` | HRAM routine source (volume init + delay loop) |
| `0x225A` | Wait-for-LCD-off (`BIT 7,(HL)` on `$FF40`, LY==`$91`) |
| `0x2273` | LCDC shadow restore: `A=($C0A2)` → `LDH ($40),A` |
| `0x2279` | IE shadow restore: `A=($C0A0)` → `LDH ($FF),A` |
| `0x2282` | **memset**: `HL`=dest, `BC`=len, fill `0` |
| `0x228A` | Clear VRAM `$9BFF`←`$0400` bytes |
| `0x2299` | Clear VRAM `$9FFF` variant |
| `0x2430` | **Serial link-cable protocol** (SC/SB at `$FF00`/`$FF01`) |
| `0x24B1` | Tilemap init (`$9800` fill, BGP=`$E4`) |
| `0x24EA` | Tilemap init variant (banked via `$31A0`) |
| `0x2527` | Sound engine init (NR50/51/52, `$D300` state) |
| `0x2569` | Song state store (`BC` → `$D3A0`/`$D3A1`, clear `$D3A2`) |
| `0x3311` | LCD IRQ handler |
| `0x33F9` | Timer IRQ handler |
| `0x3408` | Serial IRQ handler |

## Boot Sequence (0x0150)

1. `DI`, save `H` (console type) to `$DFF7`
2. LCD off (`LCDC=0` via `$4000` write — mapper shadow)
3. MBC1 ROM bank = 1 (`$2000`)
4. `SP=$DFEF`, clear HRAM `$FF80` (0x7F bytes) and `$DD00` (0xFF bytes)
5. Palettes BGP/OBGP = 0
6. WRAM bank 2 (`SVBK=2`), scroll = 0
7. Cartridge RAM enable (`$0000=0x0A`), RAM bank 1 (`$6000`)
8. `CALL $2430` — serial link check; on failure show error (`HL=$780B, A=7`)
9. Full reset: `IE=0`, clear HRAM, VRAM `$8000` (0x1FFF), WRAM `$C000` (0x1CFF)
10. SGB check: `A=($DFF7)`, `CP $11` → set `$C0BB`/`$C0BC` flags
11. Banked calls to init routines, then `EI`
12. Main loop at `$026D`

## Key WRAM Variables

| Address | Meaning |
|---|---|
| `$C0A0` | IE (interrupt enable) shadow |
| `$C0A2` | LCDC shadow |
| `$C0A6` | Main-loop trigger flag (polled at `$026D`) |
| `$C0A7` | Joypad / input flag |
| `$C0A8`–`$C0A9` | 16-bit frame counter |
| `$C0BA` | Counter (incremented in VBlank) |
| `$C0BB`–`$C0BC` | SGB detection flags |
| `$C500` | VBlank scratch |
| `$CB5C`–`$CB5E` | VBlank state |
| `$D300`–`$D3FF` | Sound engine state (song player) |
| `$DFF7` | Saved console type (`H` from boot ROM) |
| `$DD00` | Cleared at boot |

## Cartridge RAM (Save Data) — `$A000`–`$BFFF`

| Address | Meaning |
|---|---|
| `$B880` | Day counter (max `$1E`=30) |
| `$B881` | Month counter (max `$0F`/`$0E`) |
| `$B882` | Year counter (max `$18`=24) |
| `$B883`–`$B885` | Calendar sub-counters |
| `$B906` | Flag |
| `$B912`–`$B91A` | Season/weather data (from `$0763` table) |
| `$BA40`–`$BA55` | Game flags |

## Notes for Randomizer

- The game uses a **banked-call architecture** (`$217F`); most game logic
  lives in banks 1–63. Bank 0 is the kernel/boot + VBlank + calendar.
- Save data lives in cartridge RAM (`$B880+`); a save-editor approach can
  randomize starting state without code patches.
- The **serial link protocol** (`$2430`) is the 2-player Game Link code —
  relevant if we ever want online/local co-op randomization.
- Header must be repaired before the ROM will boot on hardware.
