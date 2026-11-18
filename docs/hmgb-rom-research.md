# Harvest Moon GBC — Verified ROM Research Notes

Target ROM: **`Harvest Moon GB (USA) (SGB Enhanced).gbc`**
- **Size:** `1,048,576` bytes (`1 MB`, `64` banks × `16 KB`)
- **Header Title (`0x0134..0x0142`):** `HARVEST-MOON GB`
- **CGB Flag (`0x0143`):** `0x80` (dual CGB + DMG/SGB)
- **SGB Flag (`0x0146`):** `0x03`
- **Cartridge Type (`0x0147`):** `0x10` (MBC3 + Timer + RAM + Battery)
- **SRAM Size (`0x0149`):** `0x02` (`8 KB`, single battery-backed SRAM Bank `0` at `$A000..$BFFF`; `.sav` file offset = `addr - 0xA000`)
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

---

## 4. Grandpa's Rainy Day Money & Farmhouse Interaction Engine

### Flag & Persistence
- **SRAM Address:** `$B8FF` (`.sav` offset `0x18FF` inside the single 8 KB battery SRAM bank `$A000..$BFFF`).
  - `0x00` = uncollected (initialized on New Game by `LD HL,$A000; LD BC,$1FFF; CALL $2282` at CGB `1D:$5223` / `0x75223` and DMG `3D:$51DE` / `0xF51DE`).
  - Non-zero (`0x01` written on collection) = already collected.
- **Exclusivity:** Full-ROM cross-reference confirms `$B8FF` (`FF B8`) is referenced **only** at `02:$59C8`/`02:$59D0` (CGB) and `22:$5935`/`22:$593D` (DMG). Neighboring SRAM bytes are distinct (`$B8F8/$B8F9` tool slots, `$B8FB` rucksack capacity, `$B8FC..$B8FD` pending gold, `$B8FE` farmhouse state, `$B900` event flag, `$B904` houseplant flower state, `$B911` house upgrade level).
- **Persistence:** Written directly to live battery-backed SRAM Bank `0` on the frame `A` is pressed and preserved across cold power-cycles and `Continue` loads.

### Farmhouse Attribute Maps (`00:$11C2..$1204` → WRAM `$D800..$D953`)
On Farmhouse entry (CGB `02:$5211`, DMG `22:$5191`), the engine sets row stride `$B90C = $22` (`34` bytes = `17` metatiles wide), map base `$B90D:$B90E = $D800`, and calls `00:$11C2`, which copies `$0154` (`340`) bytes (`17 × 10` metatiles, 2 bytes `[attr_id, flags]` per tile; flag bit `0` = solid, bit `1` = `A`-interactable) from `Bank 0x11` according to House Upgrade Level `$B911`:

| `$B911` | Tier | ROM Table (File Offset) | Drawer (`09:03`) Coordinates & Offsets |
|---|---|---|---|
| `0x00` | Starter House | `11:$7251` (`0x47251`) | `(3, 3)` → `11:$72BD` (`0x472BD`), WRAM `$D86C` |
| `0x01` | First Extension | `11:$70FD` (`0x470FD`) | `(3, 3)` → `11:$7169` (`0x47169`, `$D86C`); `(12, 3)` → `11:$717B` (`0x4717B`, `$D87E`); `(13, 3)` → `11:$717D` (`0x4717D`, `$D880`) |
| `>= 0x02` | Super Deluxe House | `11:$6FA9` (`0x46FA9`) | `(1, 3)` → `11:$7011` (`0x47011`, `$D868`); `(2, 3)` → `11:$7013` (`0x47013`, `$D86A`); `(13, 3)` → `11:$7029` (`0x47029`, `$D880`) |

Dynamic WRAM table patches in `Bank 0x02` (`02:$64BE..$6513`) touch only `$D8B4..$D8B7` and `$D8D6..$D8D9` (table croissant `$99` tiles) and never alter any `$09` drawer entry. Movement/facing probes in `Bank 0x05` (`$519F`, `$5279`, `$53DA`, `$54C5`) populate WRAM `$CB33` (`attr_id`) and `$CB34` (`flags`) for the tile directly in front of the player.

### Farmhouse `A`-Button Dispatcher (`02:$5913..$596C` CGB / `22:$5880..$58D9` DMG)
Gates interaction on: `$CB4F == 0` (no transition), `$CB8B == 0` (not carrying an item overhead; equipped tools in `$B8F8` are allowed), `$C90E != 0` (player has control), `($CB34 & $02) != 0` (facing interactable tile), `$CB56 == 0` (no dialogue active), and `($FF8B & $01) != 0` (new `A` press). Then dispatches on `$CB33`:
- `$02` → Seed/Item Exchange terminal (`ID 0x07`)
- `$03` → Wall Calendar (`ID 0x1D`)
- `$04` → TV (`02:$59EE` / `22:$595B`)
- `$05` → Diary book subscreen (`CALL $0F0F`)
- `$06..$08` → Kitchen sink/counters/stove (`ID 0x08`)
- **`$09` → Grandpa's Rainy Day Money drawer (`02:$59C8` / `22:$5935`)**
- `$0A` → Bed sleep prompt (`ID 0x05`)
- `$0B` → Houseplant (`ID 0x45` / `0x46`, gated by `$B904`)
- `$0C` → Ranch Master Mark (`ID 0x52`, Super Deluxe House `(14, 4)`, gated by `$B8D1`)
- `$99` → Table item / Croissant (`02:$5A2D` / `22:$599A`)

### Drawer Handler Disassembly (38 Bytes, Identical CGB & DMG)
- **CGB:** `Bank 0x02:$59C8..$59ED` (file `0x099C8..0x099ED`), reached from `02:$5958` (`FE 09 CA C8 59`)
- **DMG:** `Bank 0x22:$5935..$595A` (file `0x89935..0x8995A`), reached from `22:$58C5` (`FE 09 CA 35 59`)

```text
02:59C8 / 22:5935: FA FF B8   LD A,($B8FF)   ; Read Rainy Day Money flag
02:59CB / 22:5938: B7         OR A
02:59CC / 22:5939: 20 15      JR NZ,.taken   ; Non-zero -> already collected
02:59CE / 22:593B: 3E 01      LD A,$01
02:59D0 / 22:593D: EA FF B8   LD ($B8FF),A   ; Mark collected
02:59D3 / 22:5940: 21 EF B8   LD HL,$B8EF    ; 24-bit LE Gold ($B8EF..$B8F1)
02:59D6 / 22:5943: 01 E8 03   LD BC,$03E8    ; +1000G (file 0x099D7 CGB / 0x89944 DMG)
02:59D9 / 22:5946: CD BB 0C   CALL $0CBB     ; Add signed 16-bit BC to 24-bit (HL)
02:59DC / 22:5949: CD 56 10   CALL $1056     ; Clamp 0..99,999G & format $B928..$B92C
02:59DF / 22:594C: 3E 09      LD A,$09       ; Dialogue 0x09 ("What! There is money...")
02:59E1 / 22:594E: 18 02      JR .show
.taken:
02:59E3 / 22:5950: 3E 0A      LD A,$0A       ; Dialogue 0x0A ("Let me see... / Everything is alright!")
.show:
02:59E5 / 22:5952: CD 52 3F   CALL $3F52     ; Open dialogue A
02:59E8 / 22:5955: 3E 00      LD A,$00
02:59EA / 22:5957: CD 58 16   CALL $1658     ; Play confirm SFX $00 (RST $08 -> 06:$55E4)
02:59ED / 22:595A: C9         RET
```

### Dialogues (`Bank 0x07`)
- **`ID 0x09`** — Pointer `0x0343C` → `07:$4684` (file `0x1C684..0x1C72F`, `4` boxes = `172` bytes, callback `$7078` = `RET`):
  1. `"What! There is  "` / `"money in the   ▼"`
  2. `"drawer. Was this"` / `"grandpa's rainy▼"`
  3. `"day money? It's "` / `"mine now!!     ▼"`
  4. `"Received 1000G!!"` / `"                "` (Box 4 Line 1 at file `0x1C70F..0x1C71E`)
- **`ID 0x0A`** — Pointer `0x0343F` → `07:$4730` (file `0x1C730..0x1C785`, `2` boxes = `86` bytes, callback `$7078` = `RET`):
  1. `"Let me see…     "` / `"               ▼"`
  2. `"Everything is   "` / `"alright!        "`

### Gold Storage, Clamp & `00:$0CBB` Carry Quirk
- **SRAM `$B8EF..$B8F1` (`.sav[0x18EF..0x18F1]`):** 24-bit unsigned little-endian Gold. Initialized on New Game to `$0001F4` (`500G`) at CGB `1D:$52C3` (`0x752C3`) and DMG `3D:$527E` (`0xF527E`).
- **`00:$0CBB..$0CCD`:** Adds `C` to `[HL+]`, executes `INC BC` at `00:$0CBE`, tests `BIT 7,B` to branch positive vs. negative, then `ADC A,(HL)` into `[HL+]` and carries/borrows into the 3rd byte `[HL]`.
  - **Vanilla Bug (Verified in Code & Live Emulation):** When `C == $FF` (intended for negative two's-complement offsets like `$FFFF = -1`), `INC BC` also carries `+1` into `B` for positive operands, silently adding **`+256G` extra** (e.g. `500G + $00FF` yields `1,011G` instead of `755G`; `$7FFF` flips `BIT 7,B` and becomes negative). Vanilla `$03E8` (`1,000G`) has `C = $E8` and adds cleanly (`500G` → `1,500G` = `$0005DC`). Avoid `C == $FF` (and `>= $7F00`) when patching `$0CBB` operands.
- **`00:$1056..$10C0`:** Clamps underflow (`$B8F1 == $FF`) to `$000000` (`0G`) and overflow (`>= $0186A0` = `100,000`) to `$01869F` (`99,999G`; verified live: `99,500G + 1,000G` → `99,999G`), then formats 5 right-aligned tiles via `CALL $3268` into SRAM `$B928..$B92C` (`AF AF 39 34 34` → `AF 35 39 34 34` for `500G` → `1500G`).

### Live Verification Summary (CGB & DMG)
All behaviors confirmed in live emulation: Day 1 Starter House collection at `(3,3)` (`$B8FF: 0x00 → 0x01`, Gold `500G → 1500G`, all 4 boxes of `ID 0x09` + HUD update), repeat check (`ID 0x0A`, Gold unchanged), live `$B8FF = 0x00` reset (`1500G → 2500G`, confirming `$B8FF` is the sole gate), `99,999G` cap clamp, equipped-tool pass (`$B8F8 = 1`), overhead-carry block (`$CB8B = 1`), facing-away rejection, House Level 1 (`(3,3),(12,3),(13,3)`) and Level 2 (`(1,3),(2,3),(13,3)`) pre- and post-collection parity, battery `.sav` cold-boot `Continue` persistence, DMG mode (`Bank 0x22:$5935`) parity, and `$0CBB` `BC=$00FF` quirk.

