# Harvest Moon GBC — Memory Map

## CPU Address Space

```
0x0000–0x3FFF  ROM bank 0 (fixed)          — boot, kernel, VBlank, calendar
0x4000–0x7FFF  ROM bank N (switchable)     — game logic, data (MBC1)
0x8000–0x9FFF  VRAM (tile data + tilemap)
0xA000–0xBFFF  Cartridge RAM (battery save) — save data
0xC000–0xDFFF  WRAM (8 banks × 4 KiB, SVBK selects bank)
0xE000–0xFDFF  Echo RAM (mirror of C000–DDFF)
0xFE00–0xFE9F  OAM (sprites)
0xFF00–0xFF7F  I/O registers
0xFF80–0xFFFE  HRAM (fast routines, e.g. $FF80 delay/volume)
```

## MBC1 Registers

| Address | Function |
|---|---|
| `$0000`–`$1FFF` | RAM enable (`0x0A` = enable) |
| `$2000`–`$3FFF` | ROM bank number (lower 5 bits) — game writes `$2100` |
| `$4000`–`$5FFF` | RAM bank / ROM upper bits |
| `$6000`–`$7FFF` | ROM/RAM mode |

## I/O Registers Used

| Address | Register | Notes |
|---|---|---|
| `$FF00` | SC | Serial control (link cable) |
| `$FF01` | SB | Serial data (link cable) |
| `$FF40` | LCDC | LCD control (shadow at `$C0A2`) |
| `$FF42` | SCY | Scroll Y |
| `$FF43` | SCX | Scroll X |
| `$FF44` | LY | LCD Y coordinate |
| `$FF47` | BGP | Background palette |
| `$FF48` | OBGP0 | Object palette 0 |
| `$FF49` | OBGP1 | Object palette 1 |
| `$FF70` | SVBK | WRAM bank select (`LDH ($8D)`) |
| `$FF97` | — | Sound-related (cleared in VBlank) |
| `$FF99` | — | Sound-related |
| `$FF9A` | — | Sound-related |
| `$FFA4` | — | VRAM copy source |
| `$FFA5` | — | VRAM copy dest |
| `$FFFF` | IE | Interrupt enable (shadow at `$C0A0`) |

## Calling Conventions (discovered)

- **`CALL $217F`**: banked call. Input `HL` = target address, `A` = ROM bank.
  Saves current bank, switches, calls, restores. *This is how the game calls
  into banks 1–63.*
- **`RST 00H` (`$2160`)**: jump-table dispatch. Input `A` = index, `HL` = table
  base (taken from stack). Jumps to `table[A*2]`.
- **`RST 08H` (`$216C`)**: far call. Input `A` = index into a table of 3-byte
  entries `{addr_lo, addr_hi, bank}`. Calls the target in its bank.
- **`CALL $2282`**: memset. Input `HL` = dest, `BC` = length, fills with 0.
- **`CALL $225A`**: wait for LCD off (safe VRAM access).
- **`CALL $2273`**: restore LCDC from shadow `$C0A2`.
