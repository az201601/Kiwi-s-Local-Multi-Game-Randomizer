# Harvest Moon GBC — Verified ROM Research Notes

Target ROM: **`Harvest Moon GB (USA) (SGB Enhanced).gbc`**
- **Size:** `1,048,576` bytes (`1 MB`, `64` banks × `16 KB`)
- **Header Title (`0x0134..0x0142`):** `HARVEST-MOON GB`
- **CGB Flag (`0x0143`):** `0x80` (dual CGB + DMG/SGB)
- **SGB Flag (`0x0146`):** `0x03`
- **Cartridge Type (`0x0147`):** `0x10` (MBC3 + Timer + RAM + Battery)
- **Vanilla Header Checksum (`0x014D`):** `0x6F`
- **Vanilla Global Checksum (`0x014E..0x014F`):** `0x834C`
- **Vanilla SHA-1:** `d407b9c20c5381f46ec460858539a5b6f559e04f`
- **Vanilla MD5:** `498c0a50a5e5cde16127617a97ad6162`
- **Bank Self-ID Convention:** Byte `$4000` of each switchable bank stores its own bank number (`rom[bank * 0x4000] == bank`). The engine reads `LD A,($4000)` to save/restore the active bank across far calls.

Every entry below has been verified both by SM83 disassembly and live in-game testing (CGB and DMG modes).

---

## 1. Dialogue Engine (`Bank 0x00:$36F4..$3A60`)

### Master Dialogue Table
- **Location:** `Bank 0x00:$3421..$36F3` (file `0x03421..0x036F3`)
- **Count:** `241` entries (`ID 0x00..0xF0`), `3` bytes each: `[addr_lo, addr_hi, bank]`
- **Dialogue Banks Referenced:** `0x07` (160 entries), `0x0A` (38 entries), `0x10` (25 entries), `0x0C` (18 entries)
- **Start Dialogue Helper:** `CALL $3F52` with Dialogue ID in `A` (writes `$CB52`)
- **Active Dialogue State (WRAM):**
  - `$CB52` — Active dialogue ID (`$FF` = inactive/closed)
  - `$CB53:$CB54` — 16-bit byte offset from the entry's start pointer
  - `$CB55` — Character slot within current box (`$00..$0F` = Line 1, `$10..$1F` = Line 2, `$20` = awaiting terminator)
  - `$CB69` — Choice flag (`$00` = normal box, non-zero = 2-option choice box)
  - `$CB6A`, `$CB6B` — Left/right choice target box indices (multiplied by `$2B` = `43`)
  - `$CB6C`, `$CB6D` — Left/right choice cursor offsets from Window tilemap `$9C22`
  - `$CB6E:$CB6F` — Per-box callback address executed in the dialogue's bank via `CALL $217F`

### 43-Byte (`0x2B`) Dialogue Box Record Layout
When `$CB55 == 0`, `$3913` reads a 10-byte header and advances `$CB53:$CB54` by `$0A`:

| Offset | Length | Purpose |
|---|---|---|
| `+0..+5` | 6 bytes | Choice header (`is_choice`, `target0`, `target1`, `cursor0`, `cursor1`, `pad`). All `0x00` for standard speech. |
| `+6..+7` | 2 bytes | Little-endian callback address in the dialogue's bank (points to `0xC9` `RET` for standard speech). |
| `+8..+9` | 2 bytes | Reserved (`0x00, 0x00`). |
| `+10..+25` | 16 bytes | **Line 1** glyph codes (rendered to VRAM `$80..$8F`, Window `$9C22..$9C31`). |
| `+26..+41` | 16 bytes | **Line 2** glyph codes (rendered to VRAM `$90..$9F`, Window `$9C62..$9C71`). On continuation boxes, byte `+41` is `0xE9` (`▼`). |
| `+42` | 1 byte | **Terminator:** `0xFE` = wait for `A`, clear box, advance `+1` byte into the next consecutive 43-byte box; `0xFF` = close dialogue (`$CB52 = $FF`) and invoke callback. |

---

## 2. Font & Text Encoding (`Bank 0x11:$4001`, file `0x44001`)

Verified via `$3792` (`LD HL,$4001; LD A,$11; CALL $3F6C`) and full 256-tile dump:
- Each glyph is a standard 16-byte 2bpp `8×8` tile (`color 0` = paper, `color 1` = ink).
- Codes `< 0xA0` render directly from the font table; `0xEF` is space (skipped); `0xE9` is `▼`.
- Codes `>= 0xA0` (other than `0xE9`, `0xEF`, `0xFE`, `0xFF`) invoke `Bank 0x17:$7739` to dereference a dynamic SRAM/WRAM byte.

| Byte(s) | Character / Meaning |
|---|---|
| `0x00..0x19` | `A`–`Z` (uppercase) |
| `0x1A..0x33` | `a`–`z` (lowercase) |
| `0x34..0x3D` | `0`–`9` |
| `0x3E`, `0x3F` | `[`, `]` |
| `0x40`, `0x41` | `!`, `?` |
| `0x42` | `♥` (heart) |
| `0x43` | `…` (single-tile 4-dot ellipsis) |
| `0x44`, `0x45`, `0x46` | `-`, `~`, `_` |
| `0x47..0x4A` | `↑`, `←`, `↓`, `→` |
| `0x4B`, `0x4C` | `'`, `.` |
| `0x50`, `0x51`, `0x52` | `(`, `)`, `×` |
| `0x53` | `'s` (1-tile possessive/contraction ligature, used in vanilla `grandpa's`, `It's`) |
| `0x54..0x86` | Compressed weekday & season tiles (`(Sun)`..`(Sat)`, `Spring`, `Summer`, `Fall`, `Winter`, `Timer`) |
| `0x87..0x8D`, `0x90..0x9F` | Blank / unused glyph slots (available for custom icons later) |
| `0x8E`, `0x8F` | `,`, `'v` |
| `0xA0 A1 A2 A3` | `{PLAYER}` — Player's 4-char name from SRAM `$B8F4..$B8F7` (space-padded with `0xEF`) |
| `0xE9` | `▼` (continuation indicator at Line 2 column 15) |
| `0xEA` | `►` (selection cursor) |
| `0xEF` | Space |
| `0xFE` / `0xFF` | Continue to next box / End dialogue |

> Note: Colon (`:`), semicolon (`;`), double quote (`"`), and slash (`/`) do not exist in the vanilla font.

### Dynamic Token RAM Addresses (`Bank 0x17:$774C`, file `0x5F74C`)
Useful landmarks already uncovered for upcoming randomizer features:
- `$B8F4..$B8F7` (`$A0..$A3`): Player name (4 tiles)
- `$B912..$B913`, `$BA4F..$BA52` (`$A4..$A9`): Current season tiles
- `$B928..$B92C` (`$B2..$B6`): Current Gold (`5` digits)
- `$B92D..$B92F` (`$BC..$BE`): Current Wood/Material (`3` digits)
- `$B930..$B932` (`$BF..$C1`): Current Fodder (`3` digits)
- `$B933..$B937` (`$B7..$BB`): Shipment payout (`5` digits)
- `$CC00..$CC03` (`$C4..$C7`): Active animal/pet name buffer (4 tiles)
- `$D9..$DF, $F7..$FA`: Dynamic item name buffer (11 tiles, e.g. `"[Item] to you."`)

---

## 3. Grandpa's Spirit Opening Speech (`Dialogue ID 0xC9`)

- **Master Table Pointer:** `0x0367C..0x0367E` = `0A 51 0A` → `Bank 0x0A:$510A` (file `0x2910A`)
- **Cutscene Call Sites (both load `ID 0xC9`):**
  - CGB mode: `Bank 0x10:$5568` (file `0x41568`) — `LD A,$C9; CALL $3F52`
  - DMG/SGB mode: `Bank 0x30:$54D3` (file `0xC14D3`) — `LD A,$C9; CALL $3F52`
- **Vanilla Length:** Exactly **19 boxes** (`817` bytes, `0x2910A..0x2943A`), callback `$54BC` (file `0x294BC` = `0xC9` `RET`).
- **Adjacent Entries (must never be overwritten):**
  - Preceding: `ID 0xD3` at `0x290B4..0x29109` (2 boxes: `"Well, it seems I have stayed / to long. Good night."`)
  - Following: `ID 0xEE` at `0x2943B..0x29465` (1 box: `"Thank you."`) and `ID 0xEF` at `0x29466..0x294BB` (2 boxes: `"Thank you very much!!! / We will also try to help you."` — Harvest Sprites).
- **Note on `Bank 0x2A:$510A` (`0xA910A`):** Leftover unreferenced script data from when Bank `0x0A` was cloned into Bank `0x2A` for DMG graphics (`$5C00..$7FFF`). Mirrored during in-place patches for tidiness, while `0x0367C` governs both CGB and DMG modes.
- **Free-Space Auto-Repoint (`20..61` boxes):** `Bank 0x09:$75A0..$7FFF` (file `0x275A0..0x27FFF`, `2,656` bytes of unused `0xFF`). Places `0xC9` (`RET`) at `$75A0`, writes up to `61` boxes starting at `$75A1`, and updates `0x0367C` to `A1 75 09`. Verified live in CGB emulation through box completion, Grandpa's ascension, and farm transition.
