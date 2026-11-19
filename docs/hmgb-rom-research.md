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

---

## 5. Flower Shop (Seed Shop) & Shared Town Shop Engine

### Town Map Entry, Hours & Map Dispatch (`Map ID 0x13` → `0x14`)
- **Calendar SRAM:** `$B881` minute, `$B882` hour (`0..23`), `$B883` day-of-season (`0..29`; `day % 7 == 6` is Sunday on standard cycle), `$B884` season (`0` = Spring, `1` = Summer, `2` = Fall, `3` = Winter), `$B885` year.
- **Town Map (`Map ID 0x13`):** CGB `09:$4001` (loop `09:$40C6`), DMG `29:$4001`. Contains 8 16-byte building nodes at `09:$40DC..$415B` (file `0x240DC..0x2415B` CGB) / `29:$40CF..$414E` (file `0xA40CF..0xA414E` DMG), formatted as `[fn_set16, bldg_id, fn_nav16, right16, left16, up16, down16, open_hr, close_hr, dest_map]`:

| Node | `$CD2A` | In-Game Sign Name | Hours (`$B882`) | `dest_map` | CGB Init / Loop | DMG Init / Loop |
|---|---|---|---|---|---|---|
| `0` | `0` | `Exit` (to Ranch) | `00:00..23:59` (`$00..$18`) | `$82` (exit) | — | — |
| `1` | `1` | **`Flower Shop`** (Seed Shop, Nina) | `08:00..17:59` (`$08..$12`) | `$14` | `09:$4709` / `09:$471E` | `29:$46FC` / `29:$470E` |
| `2` | `2` | **`Juice Bar`** (Eve) | `18:00..23:59` (`$12..$18`) | `$15` | `09:$4CC2` / `09:$4CD8` | `29:$4CA5` / `29:$4CB7` |
| `3` | `3` | **`Carpenter`** (Lumber & Paint) | `08:00..17:59` (`$08..$12`) | `$16` | `09:$4DF3` / `09:$4E09` | `29:$4DD2` / `29:$4DE4` |
| `4` | `4` | **`Church`** (Maria; `$B89A` lock) | `08:00..17:59` (`$08..$12`) | `$1A` | `09:$55A1` / `09:$5641` | `29:$5563` / `29:$55E8` |
| `5` | `5` | **`Tool Shop`** (Ann) | `08:00..17:59` (`$08..$12`) | `$17` | `09:$4F00` / `09:$4F16` | `29:$4EDB` / `29:$4EED` |
| `6` | `6` | **`Restaurant`** (Bakery, Ellen) | `08:00..17:59` (`$08..$12`) | `$18` | `09:$50F6` / `09:$510C` | `29:$50CD` / `29:$50DF` |
| `7` | `7` | **`Animal Shop`** (Livestock Dealer) | `08:00..17:59` (`$08..$12`) | `$19` | `09:$51FE` / `09:$525D` | `29:$51D1` / `29:$522C` |

- **Flower Shop Node Offsets:** CGB `09:$40EC..$40FB` (`0x240EC`; `open=$08` at `0x240F9`, `close=$12` at `0x240FA`, `map=$14` at `0x240FB`); DMG `29:$40DF..$40EE` (`0xA40DF`; `open=$08` at `0xA40EC`, `close=$12` at `0xA40ED`, `map=$14` at `0xA40EE`).
- **Open Days & Temporary Event Closures (`09:$4200..$42A7`):** Open **all 7 days of the week** (including Sunday) across all 4 seasons whenever `0x08 <= [$B882] < 0x12`. Only temporarily closed while one of three town-wide search mini-events is active: `$B890` (lost bird), `$B891` (post-hurricane missing weathervane), or `$B899` (Nina missing in forest). `$B89A` only blocks node `4` (`Church`), never the Flower Shop.
- **Bank `0x00` Map Dispatch Tables** (3 bytes `[addr_lo, addr_hi, bank]` per `Map ID`):
  - CGB init table base `00:$0297` → entry `0x14` at `00:$02D3` = `09:$4709` (descriptor `09:$4713` → tiles `1C:$57AE` → `$9500`, `1C:$630F` → `$8A00`, BG map `1C:$65CD`, outer VM entry `09:$48A1` via shared shop loader `09:$5435`).
  - DMG init table base `00:$0315` → entry `0x14` at `00:$0351` = `29:$46FC` (descriptor `29:$4703` → tiles `3C:$5936`, `3C:$6573`, BG map `3C:$6831`, outer VM entry `29:$4884` via shared shop loader `29:$5404`).
  - CGB loop table base `00:$0576` → entry `0x14` at `00:$05B2` = `09:$471E`; DMG loop entry at `00:$0630` = `29:$470E`.
- **`SELECT` Gold Overlay (`09:$47E9`):** Pressing `SELECT` inside the shop toggles bit `5` (`$20`, Window enable) of shadow LCDC `$C0A2` to show/hide the player's current Gold HUD.

### Dual-VM Shop Architecture
- **Dual-VM Shop Architecture:** Flower Shop (`0x14`), Juice Bar (`0x15`), Carpenter (`0x16`), Tool Shop (`0x17`), Restaurant (`0x18`), and Animal Shop (`0x19`) share the dual-VM framework (`09`/`29` outer controller + `1B`/`3B` inner script VM):
1. **Outer Shop Controller VM** — CGB `Bank 0x09:$48A1..$4CB5` / DMG `Bank 0x29:$4884..$4C98` (exact **`-0x1D`** address shift across the entire Flower Shop module; PC in WRAM `$CD28:$CD29`). Builds the active counter slot list, draws the 5 counter bags and cursor/scroll OAM sprites, polls D-pad / `A` / `B`, dispatches hover and purchase scripts to the inner VM, rebuilds slots after each purchase, and returns to Town Map (`$CB50 = $13, $CB4F = $1D`) on exit.
   - Outer flow (CGB addresses): `$4C9C($4695)` idle inner VM → `$491D` build active slots → 5× `$4B27` draw counter tiles → `$4C9C($6E08)` greeting (`"Welcome, come in {PLAYER}."`) + `$4CAB` wait → `$4BB5` branch (`[$CD33] != 0` → `$48F9` selection loop; `[$CD33] == 0` → `$4911` goodbye). Selection loop `$48D6`: redraw 5 counter slots → `$48F9: $4B6B` trigger active slot's `hover_ptr` → `$4BC3` poll input (`L/R` scroll → `$48D6`, cursor move → `$48F9`, `B` → `$4911`, `A` → trigger `buy_ptr`, `$4CAB` wait completion, `$491D` rebuild slots, `$4B8A` clamp cursor/scroll, `$4BB5` loop or exit if `$CD33 == 0`). Exit `$4911`: `$4C9C($714C)` (`"Bye-Bye."`) + `$4CAB` wait → `$4CB5` transition to `$13`.
2. **Inner Dialogue & Transaction VM** — CGB `Bank 0x1B:$4001` / DMG `Bank 0x3B:$4001` (`$1B` and `$3B` share identical virtual addresses; only 8 low-level engine bytes differ across the entire 16 KB bank). Renders shop dialogue boxes, runs the interactive quantity selector, validates/subtracts Gold, increments seed bag SRAM counts, updates Tool Shed presence flags, and handles Nina's first-purchase free Water Can.

### Master 9-Record Seed Shop Table (`09:$495D` CGB / `29:$4940` DMG)
- **CGB Offset:** `Bank 0x09:$495D..$49AD` (file **`0x02495D..0x0249AD`**, `81` bytes = `9` records × `9` bytes)
- **DMG Offset:** `Bank 0x29:$4940..$4990` (file **`0x0A4940..0x0A4990`**, `81` bytes, byte-for-byte identical to CGB)
- **9-Byte Record Structure:**

| Byte(s) | Field | Purpose |
|---|---|---|
| `+0..+1` | `qty_addr` (`u16` LE) | SRAM address holding owned bag count (`$B8D2..$B8DA`); record is hidden when `[qty_addr] >= 99` (`$63`). |
| `+2` | `season_mask` (`u8`) | Allowed-season bitmask tested against `1 << [$B884]`: bit `0` (`$01`) = Spring, bit `1` (`$02`) = Summer, bit `2` (`$04`) = Fall, bit `3` (`$08`) = Winter (`$07` = Spr+Sum+Fal; `$0F` = all 4 seasons). |
| `+3` | `name_mask` (`u8`) | Allowed player-name first-character group bitmask tested against `NameLUT[[$B8F4]]` (`$1F` = all player names; see LUT below). |
| `+4..+5` | `hover_ptr` (`u16` LE) | Inner VM (`1B`/`3B`) script run when cursor highlights this bag (`$7719 + 10*i`, prints 32-byte label at `$7773 + 32*i`). |
| `+6..+7` | `buy_ptr` (`u16` LE) | Inner VM (`1B`/`3B`) script run when player presses `A` on this bag. |
| `+8` | `vram_tile` (`u8`) | Base BG tile index for the `2×2` seed-bag graphic (`tile..tile+3` loaded from `1C:$57AE` / `3C:$5936` into VRAM `$9500`); also selects the CGB highlight palette via `(vram_tile - $50) / 4`. |

- **Complete Vanilla 9-Seed Definition & Cross-Reference Table:**

| `#` | Seed | CGB Row (`File`) | DMG Row (`File`) | Qty SRAM / Shed SRAM / Tool ID | `seas` | `name` | Tile / CGB Pal (`20:$57F5`) | Hover Script / 32B Label (`1B` & `3B`) | Buy Script / `u16` Price Addr (`1B` / `3B` File) | Vanilla Price | Menu Name (`Bank 00`, 14B) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `0` | **Turnip Seeds** | `09:$495D` (`0x2495D`) | `29:$4940` (`0xA4940`) | `$B8D2` / `$B8B5` / `$04` | `$01` (Spr) | `$1F` (All) | `$54` / `20:$56B6` | `$7719` / `$7773` (`0x6F773`,`0xEF773`) | `$6E26` / `$6E64` (`0x6EE64` / `0xEEE64`) | `120G` | `00:$1C3B` |
| `1` | **Potato Seeds** | `09:$4966` (`0x24966`) | `29:$4949` (`0xA4949`) | `$B8D3` / `$B8B6` / `$05` | `$01` (Spr) | `$1F` (All) | `$58` / `20:$56DE` | `$7723` / `$7793` (`0x6F793`,`0xEF793`) | `$6E7B` / `$6EB9` (`0x6EEB9` / `0xEEEB9`) | `150G` | `00:$1C49` |
| `2` | **Tomato Seeds** | `09:$496F` (`0x2496F`) | `29:$4952` (`0xA4952`) | `$B8D4` / `$B8B7` / `$06` | `$02` (Sum) | `$1F` (All) | `$5C` / `20:$56B6` | `$772D` / `$77B3` (`0x6F7B3`,`0xEF7B3`) | `$6ED0` / `$6F0E` (`0x6EF0E` / `0xEEF0E`) | `200G` | `00:$1C57` |
| `3` | **Corn Seeds** | `09:$4978` (`0x24978`) | `29:$495B` (`0xA495B`) | `$B8D5` / `$B8B8` / `$07` | `$02` (Sum) | `$1F` (All) | `$60` / `20:$56E6` | `$7737` / `$77D3` (`0x6F7D3`,`0xEF7D3`) | `$6F25` / `$6F63` (`0x6EF63` / `0xEEF63`) | `300G` | `00:$1C65` |
| `4` | **Eggplant Seeds** | `09:$4981` (`0x24981`) | `29:$4964` (`0xA4964`) | `$B8D6` / `$B8B9` / `$4E` | `$04` (Fal) | `$0B` (`01\|02\|08`) | `$64` / `20:$569E` | `$7741` / `$77F3` (`0x6F7F3`,`0xEF7F3`) | `$6F7A` / `$6FB8` (`0x6EFB8` / `0xEEFB8`) | `150G` | `00:$1C73` |
| `5` | **Peanut Seeds** | `09:$498A` (`0x2498A`) | `29:$496D` (`0xA496D`) | `$B8D7` / `$B8BA` / `$4F` | `$04` (Fal) | `$15` (`01\|04\|10`) | `$68` / `20:$56DE` | `$774B` / `$7813` (`0x6F813`,`0xEF813`) | `$6FCF` / `$700D` (`0x6F00D` / `0xEF00D`) | `200G` | `00:$1C81` |
| `6` | **Carrot Seeds** | `09:$4993` (`0x24993`) | `29:$4976` (`0xA4976`) | `$B8D8` / `$B8BB` / `$50` | `$08` (Win) | `$13` (`01\|02\|10`) | `$6C` / `20:$56B6` | `$7755` / `$7833` (`0x6F833`,`0xEF833`) | `$7024` / `$7062` (`0x6F062` / `0xEF062`) | `300G` | `00:$1C8F` |
| `7` | **Broccoli Seeds** | `09:$499C` (`0x2499C`) | `29:$497F` (`0xA497F`) | `$B8D9` / `$B8BC` / `$51` | `$08` (Win) | `$0D` (`01\|04\|08`) | `$70` / `20:$56D6` | `$775F` / `$7853` (`0x6F853`,`0xEF853`) | `$7079` / `$70C3` (`0x6F0C3` / `0xEF0C3`) | `500G` | `00:$1C9D` |
| `8` | **Grass Seeds** | `09:$49A5` (`0x249A5`) | `29:$4988` (`0xA4988`) | `$B8DA` / `$B8B4` / `$03` | `$07` (Spr–Fal) | `$1F` (All) | `$50` / `20:$56D6` | `$7769` / `$7873` (`0x6F873`,`0xEF873`) | `$70DA` / `$7118` (`0x6F118` / `0xEF118`) | `500G` | `00:$1C2D` |

### 3-Gate Eligibility Validator (`09:$49AE` CGB / `29:$4991` DMG) & Player-Name LUT
When building the counter inventory (`09:$491D` / `29:$4900`), the engine scans records `0..8` strictly in table order. A seed is stocked if and only if **all three gates** pass:
1. **Bag-Cap Gate:** `[[qty_addr]] < 99` (`$63`). Hitting `99` bags hides that seed from the shop until at least 1 bag is sown.
2. **Season Gate:** `(SeasonLUT[[$B884]] & season_mask) != 0`, where `SeasonLUT` is the 4-byte table `01 02 04 08` at `09:$49F4..$49F7` (`0x249F4` CGB) / `29:$49D7..$49DA` (`0xA49D7` DMG).
3. **Player-Name First-Character Gate:** `(NameLUT[[$B8F4]] & name_mask) != 0`, where `NameLUT` is a 256-byte table at `09:$4A09..$4B08` (`0x24A09..0x24B08` CGB) / `29:$49EC..$4AEB` (`0xA49EC..0xA4AEB` DMG, identical), indexed by the **first character** of the player's name (`$B8F4`, entered via name grid `1D:$6C4B` CGB / `3D:$6AFB` DMG):

| `$B8F4` Byte Range | First Character of Player Name | `NameLUT` Bit | Spring (`$B884=0`) | Summer (`$B884=1`) | Fall (`$B884=2`) | Winter (`$B884=3`) |
|---|---|---|---|---|---|---|
| `0x00..0x19` (`26` bytes) | Uppercase **`A`–`Z`** | **`$02`** | `[0]` Turnip, `[1]` Potato, `[2]` Grass | `[0]` Tomato, `[1]` Corn, `[2]` Grass | `[0]` **Eggplant**, `[1]` Grass | `[0]` **Carrot** |
| `0x1A..0x33` (`26` bytes) | Lowercase **`a`–`z`** | **`$04`** | `[0]` Turnip, `[1]` Potato, `[2]` Grass | `[0]` Tomato, `[1]` Corn, `[2]` Grass | `[0]` **Peanut**, `[1]` Grass | `[0]` **Broccoli** |
| `0x34..0x3D` (`10` bytes) | Digit **`0`–`9`** | **`$08`** | `[0]` Turnip, `[1]` Potato, `[2]` Grass | `[0]` Tomato, `[1]` Corn, `[2]` Grass | `[0]` **Eggplant**, `[1]` Grass | `[0]` **Broccoli** |
| `0x3E..0xFF` (`194` bytes) | Symbol **`[`, `]`, `!`, `?`, `♥`** or leading **space/blank** (`$EF`/`$AF`) | **`$01`** | `[0]` Turnip, `[1]` Potato, `[2]` Grass | `[0]` Tomato, `[1]` Corn, `[2]` Grass | `[0]` **Eggplant**, `[1]` **Peanut**, `[2]` Grass (**both!**) | `[0]` **Carrot**, `[1]` **Broccoli** (**both!**) |
| *(never emitted)* | *(unused 5th group in masks)* | **`$10`** | Turnip, Potato, Grass | Tomato, Corn, Grass | Peanut, Grass | Carrot |

- Intended in vanilla to push Game Link trading via the Farmhouse Seed/Item Exchange terminal (`$02`), while naming your farmer with a leading symbol/space (`0x3E..0xFF` → bit `$01`) legitimately unlocks all 8 crop seeds on a single cartridge! Setting `name_mask = 0x1F` on records `4..7` cleanly removes the name restriction for any player name.

### Dynamic Left-Pack Slot Assignment, 5-Slot Scrolling Window & CGB Palettes
- **No Fixed Per-Slot Item IDs:** `09:$491D` / `29:$4900` first clears all counter display entries (`09:$4B09` → empty tile `$7C`), zeroes active slot count `$CD33`, and appends every passing record sequentially into WRAM **`$CD34 + 5*k`** (`[hover_lo, hover_hi, buy_lo, buy_hi, vram_tile]`, `k = [$CD33]++`, capacity up to all `9` records = `45` bytes `$CD34..$CD60`).
  - Slot order is therefore determined purely by **record order in `09:$495D` / `29:$4940`** filtered by the 3 gates. If Slot `0` (e.g., Turnip) hits `99` bags, Potato immediately shifts into Slot `0` and Grass shifts into Slot `1` — both on entry and mid-visit after a purchase.
  - If `$CD33 == 0` (e.g., Winter with `99` bags of your allowed crop), all 5 counter spots stay empty (`$7C`) and Nina immediately says `"Bye-Bye."` after her welcome message and exits back to the Town Map (`$13`) with no softlock.
- **5-Slot Counter & Built-In Horizontal Scrolling:**
  - WRAM cursor state: `$CD30` = selected active-list index (`0..$CD33-1`), `$CD31` = first visible index (scroll offset `0..max(0, $CD33-5)`), `$CD32` = visible counter column (`0..4`, always `$CD30 - $CD31`).
  - Up to **5** bags render simultaneously on the red counter as `2×2` BG metatiles (`tile, tile+1` / `tile+2, tile+3`) at VRAM `$9943/$9963`, `$9946/$9966`, `$9949/$9969`, `$994C/$996C`, and `$994F/$996F` (column stride `3`, drawn by `09:$4B27`).
  - Cursor OAM sprite (`09:$4816`, coords at `09:$4844`): `Y = $48 (72)`, `X = $1C (28), $34 (52), $4C (76), $64 (100), $7C (124)`.
  - Scroll arrow OAM sprites (`09:$484E`): left arrow `◀` at `($0C, $50)` whenever `$CD31 > 0`; right arrow `▶` at `($8C, $50)` whenever `$CD33 - $CD31 > 5`. Verified live with all 9 seeds enabled in Spring (`$CD33 = 9`, scrolling smoothly from `scroll=0` `[Turnip..Eggplant]` to `scroll=4` `[Eggplant..Grass]`).
- **CGB Seed-Bag Highlight Palette (`Bank 0x20:$556C`, `$5720`, `$57F5`):**
  - Unselected counter slots always use neutral brown **BG Palette `2`**.
  - `20:$5720` sets the `2×2` CGB VRAM Bank `1` attributes of visible column `[$CD32]` (via table `20:$5716` = `$9943..$994F`) to **BG Palette `7`**, computes `idx = (vram_tile - $50) / 4` (`0..8`) from `$CD34 + 5*[$CD30] + 4`, and for `Map ID 0x14` (`$C0A7 == $14`) loads the matching CGB palette pointer from the 9-entry table at **`20:$57F5..$5806`** (file `0x0817F5..0x081806`) into BG Palette `7` (`0`=Grass `$56D6` dark green, `1`=Turnip `$56B6` white/red, `2`=Potato `$56DE` tan, `3`=Tomato `$56B6` red, `4`=Corn `$56E6` yellow, `5`=Eggplant `$569E` blue, `6`=Peanut `$56DE` tan, `7`=Carrot `$56B6` orange-red, `8`=Broccoli `$56D6` bright green). Because palette lookup follows `vram_tile` and `$CD32`, reordering seeds across slots preserves every bag's exact CGB colors automatically.

### Inner Purchase Scripts, Pricing & Free Water Can Bonus (`Banks 0x1B` & `0x3B`)
- **Pricing (Only 2 Locations per Seed — Never Hardcoded in Buy Dialogue!):**
  1. **Hover Label (32 bytes at `1B/3B:$7773 + 32*i`):** Line 1 (`+0..+15`) is the seed name; Line 2 (`+16..+31`, i.e., `$7783 + 32*i`) reads `"1 bag is XXXG.  "`.
  2. **Unit Price Immediate (`u16` LE in opcode `$41F7 qty_addr16 price16`):** Sets WRAM max purchasable `$CD92 = 99 - [qty_addr]` and unit price `$CD94:$CD95 = price16` (offsets in table above).
- **Quantity Selector & Deduction:**
  - Opcode `$4218 ok_ptr16 cancel_ptr16` adjusts quantity `$CD93` within `0..$CD92` (`LEFT` increments, `RIGHT` decrements, `B` or `qty == 0` jumps to `$71B9` cancel `"Is that so? / Come again."`) and dynamically updates total cost `$CD96:$CD97 = $CD93 * ($CD94:$CD95)`.
  - Opcode `$43A7 no_money_ptr16 qty_addr16` compares/subtracts `$CD96:$CD97` from 24-bit Gold `$B8EF..$B8F1` using clean unsigned helpers `$46A4` / `$46B1` (**not** `$0CBB`, so prices ending in `$FF` have zero carry bug; insufficient funds jumps to `$7137` `"You don't have enough money."`), then adds `$CD93` to `[qty_addr]`.
  - Opcode `$4405 shed_addr16 tool_id` sets Tool Shed presence flag `[shed_addr] = 1` **unless** `tool_id` is currently equipped in `$B8F8..$B8FA` (preventing Tool Shed + Rucksack duplication; Tool Shed interface is in `06`/`26:$45B7..$46EC`).
- **First Crop-Seed Purchase Bonus — Free Water Can (`1B/3B:$7158..$71B8`, file `0x6F158` / `0xEF158`):**
  - Crop seeds `[0..7]` end with `JMP $7158`, which prints `"Thank you very much. / Please make sure you water them everyday."` (`$7251..$72B0`), then executes at **`$717A..$718C`** (`0x6F17A` CGB / `0xEF17A` DMG):
    - `$43D7 $B8AE $0A $7141` — if Water Can is in Tool Shed (`[$B8AE] != 0`) **or** equipped (`$0A` in `$B8F8..$B8FA`), skip bonus (`JMP $7141`).
    - `$43D7 $B8AF $12 $7141` — if Sprinkler is in Tool Shed (`[$B8AF] != 0`) **or** equipped (`$12` in `$B8F8..$B8FA`), skip bonus (`JMP $7141`).
    - `$43F1 $B8AE` — otherwise set `[$B8AE] = 1` (grants **Water Can** into Tool Shed), display `"Since this is / your first ▼"` → `"purchase, I will / give you this ▼"` → `"Water Can."` (`$7651..$76B0`), and end at `$713F`.
  - **Grass Seeds (`[8]`) Exemption:** Falls straight into `$712B` (`"Thank you very much."` → `JMP $713F`), bypassing `$7158` completely (buying Grass never awards the Water Can).
  - **Boy vs. Girl New-Game Difference (`1D:$51C8..$51DE` / `0x751C8` CGB; `3D:$5183..$5199` / `0xF5183` DMG):** Male farmers (`$B8EC == 0`) start with no seeds and `$B8AE = 0` (triggering Nina's bonus on first crop seed purchase); Female farmers (`$B8EC == 1`) start with `1` bag each of Turnip (`$B8B5`/`$B8D2`), Potato (`$B8B6`/`$B8D3`), and Grass (`$B8B4`/`$B8DA`) plus the Water Can (`$B8AE = 1` at `1D:$51DC` / `3D:$5197`), so vanilla Girl files skip `$718A` because `$B8AE` is already `1` (see §12).

### Farm Sowing & Overnight Crop-Season Coupling (Randomizer Design Note)
Stocking a seed in a different season in `09:$495D` / `29:$4940` makes it immediately purchasable and **sowable** (farm sowing `01:$63BA..$6525` → `08:$50CF..$5135` never checks `$B884`), **but** two farm subsystems remain coupled to `$B884`:
1. **Overnight Crop Season Checks (`Bank 0x02` CGB / `Bank 0x22` DMG):** Each night, `02:$689A` scans all `$0C40` farm map entries at SRAM `$A000` (2 bytes `[tile_id, attr]`; `attr & $40` = crop tile, `attr & $08` = watered) and checks `LD A,($B884); CP <season>` — any mismatch calls `02:$6A49` to wither the crop back to tilled soil (`$11, $00`):
   - **Turnip** (`$9x`, Spring `CP $00`): growth `02:$6B3F` / `22:$6A9F`, secondary `02:$4FC1` / `22:$4FB3`
   - **Potato** (`$Ax`, Spring `CP $00`): growth `02:$6B82` / `22:$6AE2`, secondary `02:$4FCA` / `22:$4FBC`
   - **Tomato** (`$5x..$6x`, Summer `CP $01`): growth `02:$69DE` / `22:$693E`, secondary `02:$4FB0` / `22:$4FA2`
   - **Corn** (`$7x..$8x`, Summer `CP $01`): growth `02:$6AD4` / `22:$6A34`, secondary `02:$4FB8` / `22:$4FAA`
   - **Eggplant** (`$Bx`, Fall `CP $02`): growth `02:$6BC5` / `22:$6B25`, secondary `02:$4FD3` / `22:$4FC5`
   - **Peanut** (`$Cx`, Fall `CP $02`): growth `02:$6C08` / `22:$6B68`, secondary `02:$4FDC` / `22:$4FCE`
   - **Carrot** (`$Dx`, Winter `CP $03`): growth `02:$6C4B` / `22:$6BAB`, secondary `02:$4FE5` / `22:$4FD7`
   - **Broccoli** (`$Ex..$Fx`, Winter `CP $03`): growth `02:$6C8E` / `22:$6BEE`, secondary `02:$4FEE` / `22:$4FE0`
   - **Grass** (`$4x`, dormant in Winter `CP $03`): `02:$69BB` / `22:$691B`, secondary `02:$4F9B` / `22:$4F8D`
2. **Seasonal Farm BG Tilesets (`01:$4060..$40C5` → `Bank 0x0B`):** Farm VRAM swaps 16 tiles in `$8800..$8FFF` (`$CA..$FD`) and 33 tiles in `$9000..$97FF` per season (`$C0BD` from `$B884`: Spring `0B:$4758/$4001`, Summer `0B:$5457/$4C1A`, Fall `0B:$6150/$59FA`, Winter `0B:$6DC8/$6612`). Selling seeds early ("pre-buy out-of-season seeds") needs zero farm patches; actually growing a crop in a new season requires patching its `02`/`22` season byte **and** verifying/copying its mature crop tiles in `Bank 0x0B`.

### Live Verification Summary (CGB, DMG & Scratch ROM Patches)
Verified live in PyBoy: Town Map entry hours (`07:00` blocked, `08:00` open, `17:00` open, `18:00` blocked, Sunday `10:00` open), event locks (`$B890/$B891/$B899` block; `$B89A` allows entry), `SELECT` Gold overlay (`$C0A2: $C3 → $E3`), all `16` (`4` seasons × `4` player-name groups `'K'`, `'k'`, `'7'`, `'♥'`) inventory and slot permutations, CGB dynamic BG Palette `7` bag colors, `99`-bag slot-shift + live buy-to-`99` mid-shop slot rebuild, insufficient-gold rejection, first-crop-purchase free Water Can (`$B8AE: 0 → 1`), Grass exemption (`$B8AE` stays `0`), Sprinkler ownership suppression (`$B8AF = 1` keeps `$B8AE = 0`), empty-shop (`$CD33 == 0`) clean goodbye → Town Map, DMG mode (`Banks 0x29` + `0x3B`) parity, scratch patch enabling all `9` seeds in Spring (`$CD33 = 9`, 5-slot L/R scroll + arrow OAM), scratch patch moving Broccoli to Spring Slot `0` at `75G` (`2` bags bought: `5000G → 4850G`, `$B8D9 = 2`, `$B8BC = 1`), and overnight farm season checks (Spring: Tomato `$50 → $11` withered, Turnip `$90 → $91` grew; Summer: Tomato `$50 → $51` grew, Turnip `$90 → $11` withered).

---

## 6. Tool Shop (`Map ID 0x17` — Ann)

### Entry, Hours & Map Dispatch
- **Town Map Node `5` (`$CD2A = 5`, sign `"Tool Shop"`, 3× `RIGHT` from `Exit`):**
  - CGB `09:$412C..$413B` (file `0x2412C`; `open=$08` at `0x24139`, `close=$12` at `0x2413A`, `map=$17` at `0x2413B`).
  - DMG `29:$411F..$412E` (file `0xA411F`; `open=$08` at `0xA412C`, `close=$12` at `0xA412D`, `map=$17` at `0xA412E`).
  - Open **daily (`08:00..17:59`, Mon–Sun, all 4 seasons)**; blocked only by the same three temporary town-wide search events (`$B890`, `$B891`, `$B899`).
- **Map Dispatch (`Map ID 0x17`, `$FFA4 = $03`):**
  - CGB init `00:$02DC` → `09:$4F00` (descriptor `09:$4F0B` → tiles `1C:$5A2F` → `$9500`, `1C:$6B3C` → `$8A00`, BG map `1C:$6E33`, outer VM `09:$4F3F` via `$5435`); loop `00:$05BB` → `09:$4F16`.
  - DMG init `00:$035A` → `29:$4EDB` (descriptor `29:$4EE2` → tiles `3C:$5C18`, `3C:$6E4A`, BG map `3C:$7141`, outer VM `29:$4F16` via `$5404`); loop `00:$0639` → `29:$4EED`.
  - Exact **`-0x29`** address shift from CGB `09:$4F3F..$50F5` to DMG `29:$4F16..$50CC`. `SELECT` toggles the Gold Window identically to Flower Shop (`09:$4747` → `$47E9`).

### Procedural Slot Builder (`09:$4FBE` CGB / `29:$4F95` DMG) & 7-Entry Descriptor Table
Unlike the Seed Shop's uniform season/name bitmask loop, the Tool Shop uses **explicit per-item prerequisite checks** in `09:$4FBE..$506A` (CGB) / `29:$4F95..$5041` (DMG). Each passing item copies a 5-byte `[hover_ptr16, buy_ptr16, vram_tile]` entry from **`09:$506B..$508D`** (file **`0x02506B..0x02508D`**) / **`29:$5042..$5064`** (file **`0x0A5042..0x0A5064`**, 35 bytes, identical) sequentially into WRAM `$CD34 + 5*k` (`k = [$CD33]++`, via helper `09:$508E` / `29:$5065`):

| Eval `#` | Item | 5B Record (CGB / DMG File) | Tile / CGB Pal (`20:$5815`) | Hover Script / 16B Label (`1B` & `3B`) | Buy Script / `u16` Price (`1B` / `3B` File) | Price Text (`1B` & `3B`) | Owned Flag / Tool ID | Vanilla Price |
|---|---|---|---|---|---|---|---|---|
| `0` | **Sprinkler** | `09:$506B` (`0x2506B`) / `29:$5042` (`0xA5042`) | `$5C` (gold) / `20:$56A6` | `$78C7` / `$7903` (`0x6F903`,`0xEF903`) | `$492A` / `$4967` (`0x6C967` / `0xEC967`) | `$4C2C`, `$4C6C` | `$B8AF` (`$496D`) / `$12` | `2,000G` (+ Water Can) |
| *(—)* | *(Unused Silver Sprinkler)* | `09:$5070` (`0x25070`) / `29:$5047` (`0xA5047`) | `$60` (silver) / `20:$56AE` | `$78C7` / `$7903` | `$492A` / `$4967` | `$4C2C`, `$4C6C` | `$B8AF` / `$12` | *(unreachable)* |
| `1` | **Brush** | `09:$5075` (`0x25075`) / `29:$504C` (`0xA504C`) | `$50` / `20:$5696` | `$78D1` / `$7913` (`0x6F913`,`0xEF913`) | `$48BA` / `$48E6` (`0x6C8E6` / `0xEC8E6`) | `$4B0C` | `$B8AD` (`$48EC`) / `$09` | `800G` |
| `2` | **Saddlebag** | `09:$507A` (`0x2507A`) / `29:$5051` (`0xA5051`) | `$58` / `20:$569E` | `$78DB` / `$7923` (`0x6F923`,`0xEF923`) | `$4A00` / `$4A2C` (`0x6CA2C` / `0xECA2C`) | `$4DCC` | `$B8C2` (`$4A32`) + `$B8EA=3` | `2,500G` |
| `3` | **Milker** | `09:$507F` (`0x2507F`) / `29:$5056` (`0xA5056`) | `$54` / `20:$569E` | `$78E5` / `$7933` (`0x6F933`,`0xEF933`) | `$48F2` / `$491E` (`0x6C91E` / `0xEC91E`) | `$4BEC` | `$B8AC` (`$4924`) / `$08` | `1,800G` |
| `4` | **Cheese Maker** | `09:$5084` (`0x25084`) / `29:$505B` (`0xA505B`) | `$64` / `20:$56AE` | `$78EF` / `$7943` (`0x6F943`,`0xEF943`) | `$49BC` / `$49F4` (`0x6C9F4` / `0xEC9F4`) | `$4D4C` | `$B944` (`$49FA`) / *(barn)* | `30,000G` |
| `5` | **Butter Maker** | `09:$5089` (`0x25089`) / `29:$5060` (`0xA5060`) | `$68` / `20:$56AE` | `$78F9` / `$7953` (`0x6F953`,`0xEF953`) | `$4978` / `$49B0` (`0x6C9B0` / `0xEC9B0`) | `$4CCC` | `$B943` (`$49B6`) / *(barn)* | `30,000G` |

> **Note:** Basic farm tools (**Sickle, Hoe, Hammer, Ax**), their **Super** upgrades (granted by Harvest Sprites), **Pick Ax**, **Umbrella**, and **Fishing Rod** are **never** sold in the Tool Shop; animal supplies (**Cow Bell**, **Cow Medicine**, **P. Medicine**, **M. Potion**) are sold at the **Animal Shop** (`Map ID 0x19`).
>
> **Cut Content Discovery (`09:$5070`, tile `$60`):** Record `09:$5070` / `29:$5047` sits unused between Sprinkler and Brush with a complete, distinct **silver Sprinkler** `2×2` sprite (`tile $60`) and silver CGB palette (`20:$581D → 20:$56AE`), matching the duplicate `"Sprinkler"` entry in the Bank `0x00` tool-name table (`00:$1BE7` and `00:$1BF5`). Can be repurposed cleanly as a bonus randomized shop icon.

### Exact Stock & Unlock Conditions for All 6 Items
All 6 items are **one-time purchases** with zero season or player-name restrictions:

1. **Sprinkler (`09:$4FC9..$4FF4` CGB / `29:$4FA0..$4FCB` DMG):**
   - Stocked iff `[$B898] != 0`, `[$B8AF] == 0` (not in Tool Shed), `$12` not in `$B8F8..$B8FA` (via `09:$4896` / `29:$4879`), **and** `09:$5096` / `29:$506D` returns No Carry (`elapsed_days >= 7`, immediate `$07` at `09:$50CB` / `0x250CB` CGB and `29:$50A2` / `0xA50A2` DMG).
   - **How `$B898` and `$BA3B..$BA3D` get set:** Overnight handler `02:$6E3A..$6E6C` (DMG `22:$6D9A..$6DCC`) calls `02:$7073` to count bare tilled-soil tiles (`tile_id == $11` or `$12`, `attr == $00`) across Farm SRAM `$A000..$B87F`. When count `>= 128` (`$0080`, immediate at `02:$6E51` / `0x0AE51` CGB and `22:$6DB1` / `0x8ADB1` DMG), `$B898 == 0`, `$B88D == 0`, and Sprinkler is unowned, it sets `[$B898] = $01` and queues Ann's morning visit (`[$B88D] = $0A`, `Dialogue ID 0x2C`). Talking to Ann on the farm executes `01:$776B..$778A` (DMG `21:$764B..$766A`), stamping current `(day, season, year)` into SRAM `($BA3B, $BA3C, $BA3D)`.
   - **Vanilla Timer Quirk (`09:$5096` / `29:$506D` — Verified Live):** Computes 16-bit `HL = ($B885*120 + $B884*30 + $B883) - ($BA3D*120 + $BA3C*30 + $BA3B)`, then tests only **`LD A,L; CP $07`** (ignoring `H`). Whenever `elapsed_days % 256` is `0..6` (e.g., `+256..+262` days later), Carry is set and the Sprinkler temporarily hides for 7 days; negative differences (`$FFxx`) also pass because `L >= $07`.
   - **One-Time Ready Greeting (`$BA3E`):** On first eligible entry (`[$BA3E] != 2`), sets `[$BA3E] = 1`; inner welcome script `1B/3B:$4888` executes `$4756 $48AA`, setting `[$BA3E] = 2` and printing `"Hi! The Sprinkler is ready! ▼"` (`$4AAC`) instead of `"Hi {PLAYER}! What can I do for you? ▼"` (`$4A8B`).
   - **Water Can Trade-In (`1B/3B:$492A..$4977`):** Checks `$43D7 $B8AE $0A $4949`. If the player owns/equips a Water Can, prompts `"It is 2000G along with A Water Can / Buy  Don't Buy"` (`$4C2C` + `$4C4C`); if the player does **not** have a Water Can, falls back to `"It is 2000G. / Buy  Don't Buy"` (`$4C6C`) and **still allows purchase**. On `Buy`, sets `[$B8AF] = 1` and executes `$4418 $B8AE $0A` (`$496F`, file `0x6C96F` / `0xEC96F`), clearing `[$B8AE] = 0` and replacing `$0A` with `$FF` in `$B8F8..$B8FA` if equipped.
2. **Brush (`09:$4FF5..$500C` CGB / `29:$4FCC..$4FE3` DMG):**
   - Available immediately on **Day 1** (`Spring 1, Year 1, 08:00`). Stocked iff `[$B8AD] == 0` and Tool ID `$09` is not in `$B8F8..$B8FA`. Menu name at `00:$1BCB`.
3. **Saddlebag (`09:$500D..$5024` CGB / `29:$4FE4..$4FFB` DMG):**
   - Stocked iff `[$B8C2] == 0` **and** Horse state **`[$B8EA] == $02`** (adult horse without saddlebag).
   - Horse lifecycle (`$B8EA`): `$00` = none → `$01` = foal (adopted Spring Year 1 day `>= 19` via `$B88D = $01`, callback `01:$76A4` stores adoption day in `$B8EB`) → `$02` = matured adult horse (Summer Year 1 on day `[$B8EB]` via `02:$6ECE` / `02:$452A`) → **`$03` = adult horse wearing Saddlebag** (mobile shipping bin).
   - On `Buy` (`1B/3B:$4A00`), deducts `2,500G`, sets `[$B8C2] = 1`, and calls opcode `$46C2` (`$4A34`, `LD A,$03; LD ($B8EA),A`).
4. **Milker (`09:$5025..$503C` CGB / `29:$4FFC..$5013` DMG):**
   - Available immediately on **Day 1** (`Spring 1, Year 1, 08:00`). Stocked iff `[$B8AC] == 0` and Tool ID `$08` is not in `$B8F8..$B8FA`. Menu name at `00:$1BBD`.
5. **Cheese Maker (`09:$5046..$5056` / `29:$501D..$502D`) & Butter Maker (`09:$5057..$5068` / `29:$502E..$503F`):**
   - Share a single gate at `09:$5040` / `29:$5017`: **`[$B942] >= 200` (`$C8`**, immediate byte at `09:$5044` / `0x25044` CGB and `29:$501B` / `0xA501B` DMG). Then independently test `[$B944] == 0` (Cheese Maker) and `[$B943] == 0` (Butter Maker).
   - **`$B942` (Total Milk Bottles Shipped, capped at `201` / `$C9`):** Incremented `+1` per S/M/L Milk shipped via the Barn Shipping Bin (CGB `03:$4FD0, $5049, $50C3` / DMG `23:$4F8A..`) or via Harvest Sprite animal care (CGB `12:$6F2E` / DMG `32:$6F3E`). Also read by Juice Bar (`09:$4DB0`, `>= 1` unlocks Milk) and Shipment Ledger (`09:$5D56`).
   - Buying sets `[$B944] = 1` (Cheese Maker) or `[$B943] = 1` (Butter Maker), which spawns the respective machine inside the Cow Barn (`03:$40AC..$40B9`).

### Slots, Scrolling, Buy/Cancel Flow & CGB Palettes
- **Dynamic Left-Packing & 6-Item Scrolling:** Identical counter layout to Flower Shop (`$9943..$994F`, up to 5 visible, OAM cursor `$4816` + scroll arrows `$484E`). When all 6 items are simultaneously eligible, `$CD33 = 6` and the counter scrolls cleanly between `scroll=0` (`[Sprinkler, Brush, Saddlebag, Milker, Cheese Maker]`) and `scroll=1` (`[Brush, Saddlebag, Milker, Cheese Maker, Butter Maker]`). Rebuilds immediately after every purchase; when `$CD33 == 0` (on entry or after buying the last stocked item), prints `"See you later."` (`$4DEC`) and exits to Town Map (`$13`).
- **Hover Labels (`1B/3B:$7903..$7962`):** 16-byte single-line item names only (`len = $10`); prices are stated in each buy script's `" Buy   Don't Buy"` prompt box (selected via `$412D` / `$4168`).
- **Fixed-Price Deduction (`1B/3B:$4385`):** Uses the same unsigned 24-bit compare/subtract helpers `$46A4` / `$46B1` as the Seed Shop — verified live with a scratch `$00FF` (`255G`) Brush patch (`1,000G → 745G`, zero `$0CBB` carry bug). Choosing `"Don't Buy"` jumps to `$4A73` (`"What! Are you sure? / I worked very hard on it..."`); insufficient Gold jumps to `$4A3A` (`$553C`, `"You don't have enough money."`).
- **CGB Highlight Palettes (`20:$5815..$5822`, file `0x081815`):** Indexed by `(vram_tile - $50) / 4` into BG Palette `7` (`$50` Brush → `$5696` brown, `$54` Milker → `$569E` blue, `$58` Saddlebag → `$569E` blue, `$5C` Sprinkler → `$56A6` gold, `$60` unused Sprinkler → `$56AE` silver, `$64` Cheese Maker → `$56AE` silver, `$68` Butter Maker → `$56AE` silver).
- **100% Identical `1B` / `3B` Bytecode & Text:** `1B:$4888..$4E0B` (`0x6C888..0x6CE0B`) and `3B:$4888..$4E0B` (`0xEC888..0xECE0B`), plus hover blocks `$78C7..$7962`, are byte-for-byte identical.

### Live Verification Summary (CGB, DMG & Scratch ROM Patches)
Verified live in PyBoy: all 8 Town Map sign names, Tool Shop hours (`07:00`/`18:00` closed, `08:00`/`17:00`/Sunday `10:00` open) and event locks (`$B890/$B891/$B899` block, `$B89A` open), Day 1 stock (`[Brush, Milker]`), overnight 128-tilled-tile trigger (`$B898: 0 → 1`), Sprinkler `+6d` hidden / `+7d` ready (with one-time `"Hi! The Sprinkler is ready!"` & `$BA3E → 2` then normal repeat greeting) / `+256d` vanilla low-byte timer bug (hidden), Horse `$B8EA = 1` hidden / `2` Saddlebag stocked / `3` hidden, Milk `$B942 = 199` hidden / `200` both Makers stocked, equipped-Brush (`$B8F8 = $09`) exclusion, all-6 concurrent stock + 5-slot scroll, sequential purchase of all 6 items (`70,000G → 68,000G → 67,200G → 64,700G → 62,900G → 32,900G → 2,900G`, exact flags `$B8AF=$B8AD=$B8C2=$B8AC=$B944=$B943=1`, `$B8EA=3`, equipped Water Can `$B8F8: $0A → $FF` consumed, auto-exit to `$13` on 6th buy), Sprinkler purchase *without* Water Can (`2,500G → 500G`, `$4C6C` prompt), insufficient Gold rejection, `"Don't Buy"` cancel, scratch patch revealing unused silver Sprinkler tile `$60` & `$00FF` (`255G`) Brush price (`1,000G → 745G`), and DMG mode (`Banks 0x29` + `0x3B`) parity.

---

## 7. Animal Shop (`Map ID 0x19` — Livestock Dealer)

### Entry, Hours, Map Dispatch & Subscreen Return States (`$CD2B`)
- **Town Map Node `7` (`$CD2A = 7`, sign `"Animal Shop"`, 1× `RIGHT` from `Exit`):**
  - CGB `09:$414C..$415B` (file `0x2414C`; `open=$08` at `0x24159`, `close=$12` at `0x2415A`, `map=$19` at `0x2415B`).
  - DMG `29:$413F..$414E` (file `0xA413F`; `open=$08` at `0xA414C`, `close=$12` at `0xA414D`, `map=$19` at `0xA414E`).
  - Open **daily (`08:00..17:59`, Mon–Sun, all 4 seasons)**; blocked only by temporary town search events `$B890`, `$B891`, `$B899`.
- **Map Dispatch (`Map ID 0x19`, `$FFA4 = $05`):**
  - CGB init `00:$02E2` → `09:$51FE` (descriptor `09:$5252` → tiles `1C:$5CE7` → `$9500`, `1C:$73D3` → `$8A00`, BG map `1C:$69E5`, outer VM `09:$5286`); loop `00:$05C1` → `09:$525D`.
  - DMG init `00:$0360` → `29:$51D1` (descriptor `29:$5221` → tiles `3C:$5ED0`, `3C:$778B`, BG map `3C:$6C9E`, outer VM `29:$5255`); loop `00:$063F` → `29:$522C`.
  - Exact **`-0x31`** address shift from CGB `09:$5252..$5434` to DMG `29:$5221..$5403`.
- **Subscreen Re-Entry Jump Table (`$CD2B` at `09:$5209` / `29:$51D9`):**
  - `$CD2B == 0`: Fresh visit from Town Map → welcome `"How are you doing today? ▼"` (`1B/3B:$559C`).
  - `$CD2B == 1`: Returning from **Cow Naming Screen** (`Map ID 0x1D`, CGB `1D:$530E` / DMG `3D:$52C9`) → resumes inner VM at `1B/3B:$55F1` (`"{COW}, is a good name."` + Cow Bell gift) and outer VM at `09:$5302` / `29:$52D1` (redraw & rebuild slots).
  - `$CD2B == 2` / `3`: Returning from **Sell Cow** (`Map ID 0x1E`, `09:$65CD` / `29:$6574`) or **Sell Chicken** (`Map ID 0x1F`, `09:$67D4` / `29:$677B`) picker (`$CD2B = 3` on cancel via `$7CBA`).

### Procedural Slot Builder (`09:$5331` CGB / `29:$5300` DMG) & 9-Record Table
Clears the counter (`$4B09`), **unconditionally** places `Buy a cow` and `Buy a chicken` into Slots `0..1` (`$CD33 = 2`), then evaluates records `2..8` in order and appends every passing 5-byte entry `[hover_ptr16, buy_ptr16, vram_tile]` from **`09:$5408..$5434`** (file **`0x025408..0x025434`**) / **`29:$53D7..$5403`** (file **`0x0A53D7..0x0A5403`**, 45 bytes, identical) into WRAM `$CD34 + 5*k` (`2` to `9` active slots; uses the same 5-slot scrolling counter & `◀`/`▶` arrows `$484E` even on Day 1 with `6` default items):

| `#` | Counter Option | 5B Record (CGB / DMG File) | Tile / CGB Pal (`20:$582D`) | Hover Script / 16B Label (`1B` & `3B`) | Script / `u16` Price (`1B` / `3B` File) | Price Text (`1B` & `3B`) | SRAM Flag(s) / Tool ID | Vanilla Price |
|---|---|---|---|---|---|---|---|---|
| `0` | **Buy a cow** | `09:$5408` (`0x25408`) / `29:$53D7` (`0xA53D7`) | `$50` / `20:$56BE` | `$7963` / `$79BD` (`0x6F9BD`,`0xEF9BD`) | `$55AE` / **3 sites** *(see below!)* | `$5A8C` | `$B949+$16*k` (`k=0..3`) + `$B8B3` Bell | `5,000G` |
| `1` | **Buy a chicken** | `09:$540D` (`0x2540D`) / `29:$53DC` (`0xA53DC`) | `$54` / `20:$56B6` | `$796D` / `$79CD` (`0x6F9CD`,`0xEF9CD`) | `$5652` / `$567B` (`0x6D67B` / `0xED67B`) | `$5AEC` | `$B9A7+$0C*k` (`k=0..3`) | `500G` |
| `2` | **Cow Feed** | `09:$5412` (`0x25412`) / `29:$53E1` (`0xA53E1`) | `$58` / `20:$56C6` | `$7977` / `$79DD` (`0x6F9DD`,`0xEF9DD`) | `$5699` / `$56DD` (`0x6D6DD` / `0xED6DD`) | `$5C6C` | `$B8C0` + `$B946=12` / `$10` | `700G` |
| `3` | **Chicken Feed** | `09:$5417` (`0x25417`) / `29:$53E6` (`0xA53E6`) | `$5C` / `20:$56CE` | `$7981` / `$79ED` (`0x6F9ED`,`0xEF9ED`) | `$56ED` / `$5731` (`0x6D731` / `0xED731`) | `$5CEC` | `$B8C1` + `$B947=12` / `$11` | `500G` |
| `4` | **Cow Medicine** | `09:$541C` (`0x2541C`) / `29:$53EB` (`0xA53EB`) | `$64` / `20:$569E` | `$798B` / `$79FD` (`0x6F9FD`,`0xEF9FD`) | `$5741` / `$5779` (`0x6D779` / `0xED779`) | `$5BAC` | `$B8AB` / `$00` | `1,000G` |
| `5` | **M.Potion** | `09:$5421` (`0x25421`) / `29:$53F0` (`0xA53F0`) | `$60` / `20:$56C6` | `$7995` / `$7A0D` (`0x6FA0D`,`0xEFA0D`) | `$5785` / `$57BD` (`0x6D7BD` / `0xED7BD`) | `$5C2C` | `$B8B2` / `$01` | `4,000G` |
| `6` | **P.Medicine** | `09:$5426` (`0x25426`) / `29:$53F5` (`0xA53F5`) | `$68` / `20:$56B6` | `$799F` / `$7A1D` (`0x6FA1D`,`0xEFA1D`) | `$57C9` / `$5801` (`0x6D801` / `0xED801`) | `$5D6C` | `$B8B1` / `$52` (gated `$B945>=8`) | `2,000G` |
| `7` | **Sell a cow** | `09:$542B` (`0x2542B`) / `29:$53FA` (`0xA53FA`) | `$6C` / `20:$56BE` | `$79A9` / `$7A2D` (`0x6FA2D`,`0xEFA2D`) | `$580D` *(sell flow → `Map 0x1E`)* | *(dynamic)* | clears `$B949+$16*k` to `$FF` | `+5k/8k/12kG` |
| `8` | **Sell a chicken** | `09:$5430` (`0x25430`) / `29:$53FF` (`0xA53FF`) | `$70` / `20:$56B6` | `$79B3` / `$7A3D` (`0x6FA3D`,`0xEFA3D`) | `$58D8` *(sell flow → `Map 0x1F`)* | *(dynamic)* | clears `$B9A7+$0C*k` to `$FF` | `+500G / +15kG` |

*(Note: `20:$582D` maps tile `$60` M.Potion to palette slot `[4]` and tile `$64` Cow Medicine to `[5]`, matching `(vram_tile - $50) / 4` even though Cow Medicine is evaluated before M.Potion in `09:$5373`.)*

### Exact Eligibility, Purchase & Sell Mechanics
All `1B:$555C..$68AB` + `$7963..$7D80` scripts and strings are **100% byte-for-byte identical** between CGB `Bank 0x1B` and DMG `Bank 0x3B`:

1. **Planted-Grass Gates (`1B/3B:$461B`) & Barn/Coop Caps (`$45F2` / `$4611`):**
   - `Buy a cow` and `Buy a chicken` are always shown on the counter; clicking `Buy` runs `$461B min_tiles fail_ptr`, which scans all `$0C40` Farm SRAM tiles at `$A000` counting tiles with `(tile_id & $F0) == $40` **and** `attr == $48` (all planted grass stages `$43..$49` count immediately, including newly sown and Winter-dormant grass):
     - **Cow:** requires **`>= 54` (`$36`**, `6` `3×3` patches; immediate byte at `1B/3B:$55CE` / file `0x6D5CE` & `0xED5CE`) grass tiles and `< 4` cows in `$B949, $B95F, $B975, $B98B` (22 bytes each, `$FF` = empty).
     - **Chicken:** requires **`>= 36` (`$24`**, `4` `3×3` patches; immediate byte at `1B/3B:$5672` / file `0x6D672` & `0xED672`) grass tiles and `< 4` chickens in `$B9A7, $B9B3, $B9BF, $B9CB` (12 bytes each, `$FF` = empty); writes `[state=$02, 0, 0, 0]` via `$466F`.
2. **CRITICAL 3-Site Cow Price Architecture (Verify → Temp Refund → Confirm Deduct):**
   - `1B/3B:$55D5` (`$4385 $1388 $59A3`, `u16` at **`0x6D5D7` / `0xED5D7`**): verifies `Gold >= 5,000G` and subtracts `$1388`.
   - `1B/3B:$55DB` (`$476B $1388`, `u16` at **`0x6D5DD` / `0xED5DD`**): **immediately refunds `+$1388`** via `$7C4B` → `CALL $0CBB` (crash/reset protection before entering the naming subscreen — **subject to the `$0CBB` `low_byte == $FF` quirk!**).
   - `1D:$53D7` (CGB `u16` at **`0x0753D8`**) / `3D:$5392` (DMG `u16` at **`0x0F5393`**): upon confirming the cow's name in `Map ID 0x1D`, executes unsigned 24-bit `Gold -= $1388`, writes the new cow into the first `$FF` slot at `$B949 + $16*k`, and returns to `Map ID 0x19` with `$CD2B = 1`.
   - **Repricing a Cow MUST patch all 3 `u16` pairs in lockstep** (and avoid `price & 0xFF == 0xFF`; verified live at `1,234G`: `2,000G → 766G`).
   - **Bought-Cow RNG Jackpot (`1D:$5410..$5452` / `3D:$53CB..$540D`):** Rolls starting affection `80` (`10/256` = `3.9%`), `10` (`85/256` = `33.2%`), or `0` (`161/256` = `62.9%`). Normal cows spawn as juvenile `state = $01`; rolling `80` spawns an **instant adult `state = $02` cow** (confirmed live!). Buying a cow also adds `+20` (`$14`) to Farmer Happiness `$B8F2` (`$45AB`).
3. **First Cow Purchase Bonus — Free Cow Bell (`1B/3B:$5608..$562D`, file `0x6D608` / `0xED608`):**
   - After naming a bought cow, runs `$43D7 $B8B3 $02 $562E`: if **Cow Bell** is neither in the Tool Shed (`[$B8B3] != 0`) nor equipped (Tool ID `$02` in `$B8F8..$B8FA`), executes `$43F1 $B8B3` (`[$B8B3] = 1`, menu name `00:$1C1F`) and prints `"Here, take this / cow bell as a ▼"` → `"gift. ▼"` (`$5F0C..$5F4B`).
4. **Refillable Supplies (`[2..6]`):** Hidden whenever already in Tool Shed or equipped (`09:$4896` / `29:$4879`); reappear immediately after being used up on the ranch:
   - **Cow Feed** (`$B8C0`, Tool ID `$10`, `700G`) & **Chicken Feed** (`$B8C1`, Tool ID `$11`, `500G`): also invoke `$43FB` (`$56E5` / `$5739`) to load **`12` (`$0C`)** portions into SRAM `$B946` / `$B947` (decremented on use at `01:$6395` / `01:$6385`; shed byte cleared at `0`).
   - **Cow Medicine** (`$B8AB`, Tool ID `$00`, `1,000G`) & **M.Potion** (`$B8B2`, Tool ID `$01`, `4,000G`): available from Day 1, single-use (cleared on use at `01:$62FE` / `01:$6306`).
   - **P.Medicine Unlock (`09:$53A5` CGB / `29:$5374` DMG):** Requires **`[$B945] >= 8`** (immediate `$08` at `09:$53A9` / `0x253A9` CGB and `29:$5378` / `0xA5378` DMG), where `$B945` is the **cumulative count of cow sickness incidents** (incremented up to `$0A` = `10` in `Bank 0x03:$660B..$66F7` and `$7175..$729C` whenever a cow transitions to sick state `$04`). Grants `$B8B1 = 1` (Tool ID `$52`, 30-day sickness immunity; cleared on use at `01:$62F6`).
5. **Selling Animals (`1B/3B:$580D`, `$58D8`, `$7C72..$7C8A`):**
   - `Sell a cow` (`[7]`) appears iff any `$B949 + $16*k != $FF`; only adult healthy cows (`state == $02`) can be sold: **`12,000G`** (`$2EE0`, affection `>= $A0`), **`8,000G`** (`$1F40`, affection `>= $50`), or **`5,000G`** (`$1388`, affection `< $50`); applies `-40` (`$28`) Happiness `$B8F2` (`$45BA`).
   - `Sell a chicken` (`[8]`) appears iff any `$B9A7 + $0C*k != $FF`; sells adult `state == $02` chickens for **`500G`** (`$01F4`) or special `state == $03` chickens (`04:$6D02`) for **`15,000G`** (`$3A98`); applies `-20` (`$14`) Happiness `$B8F2` (`$45C8`). Payouts add via `$7C4B` → `$0CBB` (clamped to `99,999G`).

### Live Verification Summary (CGB, DMG & Scratch ROM Patches)
Verified live in PyBoy: hours (`07:00`/`18:00` closed, `08:00`/`17:00`/Sun `10:00` open) and event flags (`$B890` closed, `$B89A` open), Day 1 6-slot counter, `P.Medicine` hidden at `$B945 = 7` / shown at `$B945 = 8`, all 9 slots active + full 5-slot L/R scroll + all 9 CGB highlight palettes, equipped-supply exclusion (`$B8F8 = $10`), 2-slot minimum when all 5 supplies owned, exact grass thresholds (`35` blocks Chicken, `53` blocks Cow, `54` allows both), full Cow purchase through `Map ID 0x1D` naming (temp refund `20,000G` → confirm `15,000G`, observed both rare `state=$02, aff=80` jackpot and standard `state=$01`), Free Cow Bell (`$B8B3: 0 → 1`, Happiness `+20`), Chicken buy (`15,000G → 14,500G`, `$B9A7 = $02`) & sell (`+500G`, `$B9A7 → $FF`, Happiness `-20`), live appearance/removal of `Sell a cow` / `Sell a chicken`, all 5 supplies bought (`-700G, -500G, -1000G, -4000G, -2000G`; `$B946 = $B947 = 12`), 4-cow full-barn rejection, 3-site scratch Cow reprice to `1,234G` (`2,000G → 766G`), and DMG mode (`Banks 0x29` + `0x3B` + `0x3D`) parity.

---

## 8. Carpenter (`Map ID 0x16`) & Farm Home Expansion Pipeline

The Carpenter in Town (`Map ID 0x16`) shares the dual-VM Town Shop engine (§5) for its 2-item counter (`Material` and `Home Expansion`), while ordering a `Home Expansion` kicks off a multi-day Farm pipeline across `Banks 01/21`, `02/22`, and `07/27` that culminates in two permanent Farmhouse upgrades (`$B911: 0 → 1 → 2`) and the **Super Hammer** progression check (`HMGB-CHK-004`).

### Town Map Gate & Map Dispatch
- **Town Map Node `3`** (`Map ID 0x13`):
  - CGB `09:$410C..$411B` (file `0x2410C`; `open=$08` at `0x24119`, `close=$12` at `0x2411A`, `map=$16` at `0x2411B`).
  - DMG `29:$40FF..$410E` (file `0xA40FF`; `open=$08` at `0xA410C`, `close=$12` at `0xA410D`, `map=$16` at `0xA410E`).
  - Open **daily (`08:00..17:59`, Mon–Sun, all 4 seasons)**; closed only during global town search events `$B890`, `$B891`, `$B899` (neither `$B89A` nor active home construction `$B8FE = 1..5` closes the shop — the player can still buy `Material` while the Carpenter is expanding their house).
- **Map Dispatch (`Map ID 0x16`, `$FFA4 = $02`):**
  - CGB init `00:$02D9` → `09:$4DF3` (`0x24DF3`; loads via shared `09:$5435` from descriptor `09:$4DFE` / `0x24DFE`: tiles `1C:$59AD`, BG map `1C:$6F8A`, attr `1C:$727C`, outer VM `09:$4E32`); loop `00:$05B8` → `09:$4E09` (`0x24E09`).
  - DMG init `00:$0357` → `29:$4DD2` (`0xA4DD2`; loads via shared `29:$5404` from descriptor `29:$4DD9` / `0xA4DD9`: tiles `3C:$5B96`, BG map `3C:$72ED`, attr `3C:$75DF`, outer VM `29:$4E0D`); loop `00:$0636` → `29:$4DE4` (`0xA4DE4`).
  - Exact **`-0x25`** address shift from CGB `09:$4DFE..$4EFF` to DMG `29:$4DD9..$4EDA`.

### Procedural Slot Builder (`09:$4EB0` CGB / `29:$4E8B` DMG) & 2-Record Table
Clears the counter (`$4B09`), sets `$CD33 = 0`, evaluates the 2 records below in order, and packs passing 5-byte entries `[hover_ptr16, buy_ptr16, vram_tile]` from **`09:$4EF6..$4EFF`** (file **`0x024EF6..0x024EFF`**) / **`29:$4ED1..$4EDA`** (file **`0x0A4ED1..0x0A4EDA`**, 10 bytes, byte-identical) left-to-right into WRAM `$CD34 + 5*k`:

| `#` | Counter Option | 5B Record (CGB / DMG File) | Tile / CGB Pal (`20:$5811`, `0x81811`) | Hover Script / 16B Label (`1B` & `3B`) | Script / `u16` Price (`1B` / `3B` File) | Price Text (`1B` & `3B`) | Gate Condition | SRAM Effect | Vanilla Price |
|---|---|---|---|---|---|---|---|---|---|
| `0` | **Material (`×50`)** | `09:$4EF6` (`0x24EF6`) / `29:$4ED1` (`0xA4ED1`) | `$50` / `20:$570E` (`0x8170E`) | `$7893` / `$78A7` (`0x6F8A7`,`0xEF8A7`) | `$4E36` / `$4E6E` (`0x6CE6E` / `0xECE6E`) | `"50"` `$500C` (`0x6D00C`,`0xED00C`), `"500G"` `$5020` (`0x6D020`,`0xED020`) | 16-bit LE `[$B938:$B939] < 999` (`$03E7` at `0x24EBF` CGB / `0xA4E9A` DMG) | `[$B938:$B939] = min(999, val + 50)` (`$32` at `0x6CE76` / `0xECE76`) | `500G` |
| `1` | **Home Expansion** | `09:$4EFB` (`0x24EFB`) / `29:$4ED6` (`0xA4ED6`) | `$54` / `20:$56CE` (`0x816CE`) | `$789D` / `$78B7` (`0x6F8B7`,`0xEF8B7`) | `$4E9B` / *(free shop order)* | *(quoted next day on Farm)* | `[$B911] != $02` (`0x24EDC` / `0xA4EB7`) **and** `[$B8FE] == $00` (`0x24EE0` / `0xA4EBB`) | Sets `[$B8FE] = $01` (target `$B8FE` at `0x6CEC9` / `0xECEC9`) | `0G` in shop *(see quote costs below)* |

- **Dynamic Slot Shifting & 0-Slot Auto-Exit:**
  - When `Material == 999` (`$03E7`), `Material` disappears and `Home Expansion` shifts left from Slot `1` (`$9946`) into Slot `0` (`$9943`), retaining its own CGB highlight palette via `(tile $54 - $50) / 4 = 1` → `20:$56CE`.
  - When both gates fail (`Material == 999` **and** `($B911 == 2 || $B8FE != 0)`), `$CD33 == 0` skips cursor selection after the welcome (`1B/3B:$4E0C`), prints `"Well, see ya! / Take care!"` (`1B/3B:$4EE7` → `$50BC`), and warps straight back to Town Map (`0x13`).
  - **Harmless Copy-Length Quirk (`09:$4EEF` / `0x24EEF` CGB, `29:$4ECA` / `0xA4ECA` DMG):** Copying Slot `1` executes `LD B,$06; CALL $22F8` instead of `LD B,$05`, spilling 1 extra trailing byte into the unused slot after `Home Expansion` (`$CD39` or `$CD3E`, never read because `$CD33` stops before it).
- **Shop Purchase Bytecode (`1B/3B:$4E0C..$50DB`, `$7893..$78C6`, byte-identical across `1B` and `3B`):**
  - **Material (`1B/3B:$4E36`):** Prompts `"Material, ▼ / It is used for expansion and ▼ / for your fence! / 50 Materials ▼ / for 500G! / Buy  Don't Buy"` (`$4FBC..$503B`). On `Buy`:
    - `$4E6C`: `$4385 $01F4 $4ECF` — clean unsigned 24-bit Gold check & subtract `500G` (`$01F4` at `0x6CE6E` / `0xECE6E`; no `$0CBB` `$FF` bug; fail jumps to `$4ECF` → shared `"You don't have enough money!"` `$553C`).
    - `$4E72`: **`$443F $B938 $32`** — inner VM add-`u8`-to-`u16`-clamped opcode (`1B/3B:$443F..$446B`, file `0x6C43F` / `0xEC43F`): adds unsigned byte `$32` (`50`, at `0x6CE76` / `0xECE76`) to 16-bit LE SRAM `[$B938:$B939]` (target at `0x6CE74` / `0xECE74`; initialized to `0` on New Game) and clamps any result `> $03E7` (`999`, compare at `0x6C459`/`0xEC459`, store at `0x6C465,0x6C468`/`0xEC465,0xEC468`) to `999` (e.g., `980 + 50 → 999` still charges the full `500G`), then rebuilds the counter (auto-removing `Material` once it hits `999`). On `Don't Buy` (`$4E8F`), prints `"Then don't waste my time!"` (`$507C`).
  - **Home Expansion (`1B/3B:$4E9B`):** No Buy/Don't Buy choice and no upfront cost; prints 4 boxes (`$4F3C..$4FBB`, `"I see, you want to expand your ▼ house! Alright- Tomorrow I will ▼ let you know the cost and ▼ material needed for the job!"`), executes `$43F1 $B8FE` at `$4EC7` (`[$B8FE] = 0x01`), then prints farewell and exits to Town Map (`0x13`).

### Farm Home Expansion State Machine (`$B8FE` & `$B911`)
Ordering `Home Expansion` drives a 6-state SRAM state machine in **`$B8FE`** (`.sav[0x18FE]`) paired with 1-byte house tier **`$B911`** (`.sav[0x1911]`: `0` = Starter House, `1` = Deluxe Wood House, `2` = Ultra-Deluxe Wood House):

1. **Night 0 — Schedule Quote Visit (`02:$6E70..$6E8C` / `0x0AE70` CGB; `22:$6DD0..$6DEC` / `0x8ADD0` DMG, identical):**
   - If tomorrow's weather `$B89F != $03` (not hurricane), morning visitor `$B88D == $00` (otherwise deferred 1 night while `$B8FE` stays `1`), `$B911 != $02`, and `$B8FE == $01`, sets **`$B88D = $0B`** (spawns Carpenter outside the Farmhouse door the next morning).
2. **Quote Morning — Format Digits & Run `Dlg 0x2E` (`01:$7DFE..$7E4C` / `0x07DFE` CGB; `21:$7CD1..$7D1F` / `0x87CD1` DMG, identical):**
   - Talking to the morning Carpenter (`$B88D == $0B`, dispatched at `01:$7D9F`) writes ASCII-like glyph digits (`'0'..'9' = $34..$3D`) into WRAM `$CBF7..$CBF9` (3 Material digits) and `$CBFA..$CBFD` (4 Gold digits), then opens **`Dlg 0x2E`** (CGB `07:$5C50` / `0x1DC50`; DMG `27:$5C18` / `0x9DC18`) with `Yes` / `No` callback **`07:$730C..$73C9`** (`0x1F30C` CGB) / **`27:$72A9..$7366`** (`0x9F2A9` DMG):

| Expansion Tier | House Transition (`$B911`) | Vanilla Cost | Quote Digit Bytes (CGB / DMG File) | Cost-Check Immediates (CGB `07` / DMG `27` File) | Deduction `u16` Operands (CGB `07` / DMG `27` File) |
|---|---|---|---|---|---|
| **Tier 1 — Deluxe Wood House** | `0 → 1` | **`100` Material + `2,500G`** | Mat `'1','0'` at `0x07E29,0x07E2E` / `0x87CFC,0x87D01`; Gold `'2','5','0'` at `0x07E36,0x07E3B,0x07E40` / `0x87D09,0x87D0E,0x87D13` | Mat lo `CP $64`: `0x1F353` / `0x9F2F0`; Gold mid `CP $0A,$09`: `0x1F363,0x1F367` / `0x9F300,0x9F304`; Gold lo `CP $C4`: `0x1F36E` / `0x9F30B` | Mat `+$FF9C` (`-100`, `CALL $0CCE`): `0x1F3B0` / `0x9F34D`; Gold `+$F63C` (`-2500`, `CALL $0CBB`): `0x1F3B9` / `0x9F356` |
| **Tier 2 — Ultra-Deluxe Wood House** | `1 → 2` | **`300` Material + `5,000G`** | Mat `'3','0'` at `0x07E06,0x07E0B` / `0x87CD9,0x87CDE`; Gold `'5','0'` at `0x07E13,0x07E18` / `0x87CE6,0x87CEB` | Mat hi `CP $02`: `0x1F31E` / `0x9F2BB`; Mat lo `CP $2C`: `0x1F329` / `0x9F2C6`; Gold mid `CP $14,$13`: `0x1F339,0x1F33D` / `0x9F2D6,0x9F2DA`; Gold lo `CP $88`: `0x1F344` / `0x9F2E1` | Mat `+$FED4` (`-300`, `CALL $0CCE`): `0x1F397` / `0x9F334`; Gold `+$EC78` (`-5000`, `CALL $0CBB`): `0x1F3A0` / `0x9F33D` |

   - **Accept (`Yes` with enough Gold & Material):** Sets `$B8FE = $02`, deducts Material via clean 16-bit `00:$0CCE` + `00:$0F73` (clamps `0..999` & refreshes HUD) and Gold via `00:$0CBB` + `00:$1056` *(vanilla `$F63C` / `$EC78` have low bytes `$3C` / `$78 != $FF`; if randomizing quote Gold cost `P`, avoid `P & $FF == $01` so `(-P) & $FF != $FF`, and keep `P <= 32,768`)*, and says `"Alright- It will take me 3 days to complete, starting tomorrow!"`.
   - **Decline (`No`, `07:$73C5` / `27:$7362`) or Insufficient Resources:** Resets **`$B8FE = $00`** without touching Gold or Material (`Home Expansion` immediately reappears at the Carpenter shop so it can be re-ordered anytime — never locks out).
3. **Construction Days (`$B8FE = 2 → 3 → 4 → 5 → 0`, `02:$7014..$7054` / `0x0B014` CGB; `22:$6F74..$6FB4` / `0x8AF74` DMG, identical):**
   - On Farm map load (`02:$4550..$467D` CGB / `22:$4542..` DMG), `$B8FE = 2..5` spawns the working Carpenter sprites around the Farmhouse.
   - Each night while `$B88D == $00`, `$B8FE` increments `2 → 3 → 4 → 5` (3 full construction days after quote day); on the 4th night (`$B8FE == $05`, `CP $05` at `0x0B022` / `0x8AF82`), clears `$B8FE = $00`, increments **`$B911`** (`0 → 1` or `1 → 2`), schedules completion visitor **`$B88D = $12`** (`0x0B034` / `0x8AF94`), and awards **`+10` (`$0A`) Happiness** (`$B8F2`) via `00:$0F47` **on Tier 1 only**.
   - **Tier 2 Happiness Dead-Code Quirk (`02:$7040` / `0x0B040` CGB; `22:$6FA0` / `0x8AFA0` DMG):** Immediately after `CP $01; JR Z,$7047`, a stray `RET` (`$C9`) sits right in front of an orphaned `LD A,$0A; CALL $0F47; RET`, so completing Ultra-Deluxe Wood House (`$B911 = 2`) adds **`+0` Happiness** instead of the intended `+10` (confirmed live: `50 → 60` on Tier 1, `50 → 50` on Tier 2).

### Deluxe Wood House Completion Reward — Super Hammer (`HMGB-CHK-004`) & Triple-Site Failsafe Hazard
Super Hammer is **Tool ID `$16`** (`00:$1B26`), stored in Tool Shed flag **`$B8A9`** (`.sav[0x18A9]`), replacing the basic Hammer (Tool ID `$0D` at `00:$1B22`, Tool Shed flag `$B8A5`). A full-ROM scan of `$B8A9` and `$BA42` uncovered **three separate CGB + DMG code sites** that write `[$B8A9] = 1`:

1. **Site A — Morning Carpenter Completion Dialogue (`$B88D == $12`, LIVE):** CGB **`01:$7E65..$7EBA`** (`0x07E65`, `[$B8A9]=1` at `01:$7E80` / **`0x07E80`**) / DMG **`21:$7D38..$7D8D`** (`0x87D38`, `[$B8A9]=1` at `21:$7D53` / **`0x87D53`**).
   - If `[$B911] == $01`: opens **`Dlg 0x39`** (CGB `07:$6791` / `0x1E791`; DMG `27:$672E` / `0x9E72E`: `"It's completed! I have named it Deluxe Wood House! I will give you this Super Hammer! You can break a huge rock with one swing!"`), clears Harvest Sprite lent-Hammer flag `$B897 = 0`, sets dedicated 1-byte completion-reward flag **`$BA42 = 1`** (`.sav[0x1A42]`, `0x07E7B` / `0x87D4E`), sets **`$B8A9 = 1`**, and removes the basic Hammer (`$B8A5 = 0` if in shed, else `$B8F8 = $FF` or `$B8F9 = $FF` + HUD icon reload `08/28:$45C5`).
   - If `[$B911] != $01` (Tier 2 Ultra-Deluxe Wood House): opens **`Dlg 0x3A`** (CGB `07:$68BE` / `0x1E8BE`; DMG `27:$685B` / `0x9E85B`) — **no item granted** (note: `Dlg 0x3A` itself is still a clean optional Tier 2 House Expansion check candidate if desired later).
2. **Site C — Farmhouse (`Map ID 0x02`) Init Failsafe (LIVE — CRITICAL RANDOMIZER HAZARD!):** CGB **`02:$509F..$50EC`** (`0x0909F`, `[$B8A9]=1` at `02:$50BF` / **`0x090BF`**) / DMG **`22:$505B..$50A8`** (`0x8905B`, `[$B8A9]=1` at `22:$507B` / **`0x8907B`**), inside `Map 0x02` init (`02:$5039` / `22:$501B`).
   - Runs **every single time `Map ID 0x02` loads** (including waking up on completion morning and every subsequent Farmhouse entry): if Tool ID `$16` is not in `$B8F8..$B8FA` **and** `[$B911] == $01`, forcibly sets `$B897 = 0`, **`$B8A9 = 1`**, and deletes basic Hammer from `$B8A5` / `$B8F8` / `$B8F9`!
   - **When randomizing `HMGB-CHK-004` (or Super Hammer), Site C MUST be neutralized (`NOP` / `JR` skip) in both `Bank 02` and `Bank 22`**, otherwise entering a Tier 1 Farmhouse silently grants a free Super Hammer and deletes the basic Hammer. Conversely, to guarantee `HMGB-CHK-004` is 100% unmissable even if the player goes back to sleep without speaking to the morning Carpenter on completion day, `$BA42` (`.sav[0x1A42]`, unused elsewhere) serves as a ready-made 1-byte check-collected flag (`$B911 >= 1 && $BA42 == 0`).
3. **Site B — Orphaned Overnight Failsafe (UNCALLED):** CGB **`02:$6F46..$6F95`** (`0x0AF46`, `[$B8A9]=1` at `02:$6F5B` / `0x0AF5B`) / DMG **`22:$6EA6..$6EF5`** (`0x8AEA6`, `[$B8A9]=1` at `22:$6EBB` / `0x8AEBB`). Checks `$B911 != 0` and `$BA42 != 1` to set `$B897 = 0, $BA42 = 1, $B8A9 = 1`; has zero callers in either CGB or DMG ROM (superseded by Site C).
4. **Vanilla Slot-2 (`$B8FA`) Hammer Survival Quirk:** All three Hammer-removal blocks check `$B8A5` (shed), `$B8F8` (slot 0), and `$B8F9` (slot 1) for `$0D`, forgetting 3rd equipped slot `$B8FA` — confirmed live that keeping Hammer `$0D` in `$B8FA` preserves it alongside the newly granted Super Hammer `$B8A9 = 1`.

### Live Verification Summary (CGB & DMG)
Verified live in PyBoy (with visual inspection of Town Map node `3`, 2-slot counter, `Material = 999` slot shift, `500G` buy prompt, `Home Expansion` order dialogue, and DMG mode): hours (`08:00..17:59` open incl. Sunday, `07:00`/`18:00` closed), event flags (`$B890` closed; `$B89A` and `$B8FE = 3` still open), all slot combinations (`Mat < 999` + `$B911 < 2` → 2 slots; `Mat = 999` → `Home Expansion` shifts to Slot `0`; `$B8FE = 2` or `$B911 = 2` → `Material` only; `Mat = 999` + `$B911 = 2` → 0-slot auto-exit), starting `Material = 0`, `Material` purchase (`-500G`, `+50` and `980 → 999` clamp + instant slot removal), insufficient Gold & `Don't Buy`, `Home Expansion` shop order (`0G`, `$B8FE = 1`), DMG (`Banks 29` + `3B`) parity, live Tier 1 farm quote (`Dlg 0x2E`: `5,000G → 2,500G`, `150 → 50` Mat, `$B8FE = 2`), live Tier 2 farm quote (`10,000G → 5,000G`, `350 → 50` Mat, `$B8FE = 2`), live insufficient-Material quote failure (`Mat = 299` leaves Gold/Mat untouched and resets `$B8FE = 0`), live Tier 1 completion (`$B8FE: 5 → 0`, `$B911: 0 → 1`, Happiness `50 → 60`, `Dlg 0x39`, `$B8A9 = 1`, `$BA42 = 1`, `$B8F8: $0D → $FF`, plus `$B8FA = $0D` survival), live Tier 2 completion (`$B8FE: 5 → 0`, `$B911: 1 → 2`, Happiness `50 → 50` stray-`RET` quirk, `Dlg 0x3A`), and live Farmhouse (`Map 0x02`) init failsafe (`$B911 = 1, $B8A9 = 0 → 1`).

---

## 9. Restaurant (`Map ID 0x18`) & Take-Out Pantry (`$B8C5..$B8C7`)

### Town Map Node, Operating Hours & Map Dispatch
- **Town Map Node `6` (`Map ID 0x13`):** CGB **`09:$413C..$414B`** (`0x2413C..0x2414B`) / DMG **`29:$412F..$413E`** (`0xA412F..0xA413E`, bottom-right building with plate-and-fork sign, reached via `Right, Right` from Town entry).
  - **Operating Hours:** Open daily **`08:00..17:59`** (`open = $08` at `0x24149` / `0xA413C`; `close = $12` at `0x2414A` / `0xA413D`; `map_id = $18` at `0x2414B` / `0xA413E`). Open all 7 days of the week (including Sunday) across all 4 seasons (including Winter); closed during town-wide search/shipping events (`$B890`, `$B891`, `$B899`), unaffected by Church-only `$B89A`.
- **Map `0x18` Dispatch & Graphics (`-0x2D` shift across `09:$5101..$51FD` → `29:$50D4..$51D0`):**
  - **Init:** CGB `00:$02DF` → **`09:$50F6`** (`0x250F6`, sets CGB shop palette selector `$FFA4 = $04`, graphics descriptor `09:$5101` = `1C:$5BDC` / `$6724` / `$69E5`, starts outer VM at `09:$5135`) / DMG `00:$035D` → **`29:$50CD`** (`0xA50CD`, descriptor `29:$50D4` = `3C:$5DC5` / `$69DD` / `$6C9E`, starts outer VM at `29:$5108`).
  - **Loop:** CGB `00:$05BE` → **`09:$510C`** (`0x2510C`) / DMG `00:$063C` → **`29:$50DF`** (`0xA50DF`).
  - **CGB Item Highlight Palettes (`$FFA4 = $04`, `20:$57DF` → `20:$5823..$582C` / file `0x81823..0x8182C`):** Updates BG Palette `7` dynamically by `(vram_tile - $50) / 4`: idx `0` (`$50` Lunch Box) → `20:$56EE`, idx `1` (`$54` Cake) → `20:$56E6`, idx `2` (`$58` Rice Ball) → `20:$56EE`, idx `3` (`$5C` Croissant) → `20:$56F6`, idx `4` (`$60` Meat Dumpling) → `20:$56DE`. Reordering or hiding slots preserves each food's exact CGB color automatically.

### Hybrid 5-Item Slot Builder (`09:$51B1` / `29:$5184`) & 37-Byte Master Table (`09:$51D9` / `29:$51AC`)
Built on shop entry and rebuilt immediately after **every** menu interaction by CGB **`09:$51B1..$51D8`** (`0x251B1`) / DMG **`29:$5184..$51AB`** (`0xA5184`) from the byte-identical 37-byte table at CGB **`09:$51D9..$51FD`** (`0x251D9..0x251FD`) / DMG **`29:$51AC..$51D0`** (`0xA51AC..0xA51D0`):
1. Unconditionally copies **2 five-byte records** (`10` bytes: `Cake` and `Lunch Box`) into WRAM `$CD34..$CD3D` and sets `$CD33 = 2` (so the Restaurant **always** has `2..5` active slots and never hits a 0-slot auto-exit).
2. Loops `3` times calling the Flower Shop's **shared 9-byte filter helper** (`09:$49AE` CGB / `29:$4991` DMG: `[sram_addr] < $63` (`99`), `(1 << [$B884]) & season_mask != 0`, and `NameLUT[[$B8F4]] & name_mask != 0`) over `Croissant`, `Rice Ball`, and `Meat Dumpling`. Vanilla sets `season_mask = $0F` (all 4 seasons) and `name_mask = $1F` (all player names) on all three, so only the `< 99` pantry cap hides a take-out item — immediately packing remaining slots left (e.g., capping `Rice Ball` at Slot `3` reduces `$CD33: 5 → 4`, shifts `Meat Dumpling` into Slot `3` while keeping cursor `$CD32 = 3`; capping the rightmost slot clamps `$CD32` down by `1`).

| Order | Item (`HMGB-SHP`) | Record Offset (CGB / DMG) | Fmt / Filter Bytes | Tile / CGB Pal | Hover Script / Text (`1B` == `3B`) | Buy Script / Price `u16` (`1B` == `3B`) | Effect Opcode & Operand(s) (`1B` == `3B`) | Price / Qty Strings (`1B` == `3B`) | Vanilla Price & Effect |
|---|---|---|---|---|---|---|---|---|---|
| `0` | **Cake** (`SHP-025`) | `09:$51D9` (`0x251D9`) / `29:$51AC` (`0xA51AC`) | 5B (always shown) | `$54` / `20:$56E6` | `$7A4D` / `$7A7F` (`0x6FA7F`,`0xEFA7F`) | `$68D6` / `$691A` (`0x6E91A`,`0xEE91A`) | `$45D6 $02` — shift `02` at `$6920` (`0x6E920`,`0xEE920`) | `"300G"` `$6B54` (`0x6EB54`,`0xEEB54`) | **`300G`** — dine-in `+$B8EE >> 2` (`+25%` Max Stamina, clamped to `$B8EE`) |
| `1` | **Lunch Box** (`SHP-026`) | `09:$51DE` (`0x251DE`) / `29:$51B1` (`0xA51B1`) | 5B (always shown) | `$50` / `20:$56EE` | `$7A57` / `$7A8F` (`0x6FA8F`,`0xEFA8F`) | `$6925` / `$695D` (`0x6E95D`,`0xEE95D`) | `$45D6 $01` — shift `01` at `$6963` (`0x6E963`,`0xEE963`) | `"500G"` `$6C4E` (`0x6EC4E`,`0xEEC4E`) | **`500G`** — dine-in `+$B8EE >> 1` (`+50%` Max Stamina; **overflow bug at `Max >= 180`!**) |
| `2` | **Croissant (`×10`)** (`SHP-027`) | `09:$51E3` (`0x251E3`) / `29:$51B6` (`0xA51B6`) | 9B: `$B8C5, $0F, $1F` (`< 99`) | `$5C` / `20:$56F6` | `$7A61` / `$7A9F` (`0x6FA9F`,`0xEFA9F`) | `$6968` / `$6994` (`0x6E994`,`0xEE994`) | `$442D $B8C5 $0A` — addr `0x6E99A`/`0xEE99A`, qty `0x6E99C`/`0xEE99C` | `"300G"` `$6CA0` (`0x6ECA0`,`0xEECA0`), `"10"` `$6CAC` (`0x6ECAC`,`0xEECAC`) | **`300G`** — `[$B8C5] = min(99, [$B8C5] + 10)` (`+30` Stamina/meal) |
| `3` | **Rice Ball (`×10`)** (`SHP-028`) | `09:$51EC` (`0x251EC`) / `29:$51BF` (`0xA51BF`) | 9B: `$B8C6, $0F, $1F` (`< 99`) | `$58` / `20:$56EE` | `$7A6B` / `$7AAF` (`0x6FAAF`,`0xEFAAF`) | `$69A1` / `$69D9` (`0x6E9D9`,`0xEE9D9`) | `$442D $B8C6 $0A` — addr `0x6E9DF`/`0xEE9DF`, qty `0x6E9E1`/`0xEE9E1` | `"200G"` `$6D1E` (`0x6ED1E`,`0xEED1E`), `"10"` `$6D28` (`0x6ED28`,`0xEED28`) | **`200G`** — `[$B8C6] = min(99, [$B8C6] + 10)` (`+20` Stamina/meal) |
| `4` | **Meat Dumpling (`×10`)** (`SHP-029`) | `09:$51F5` (`0x251F5`) / `29:$51C8` (`0xA51C8`) | 9B: `$B8C7, $0F, $1F` (`< 99`) | `$60` / `20:$56DE` | `$7A75` / `$7ABF` (`0x6FABF`,`0xEFABF`) | `$69E6` / `$6A1E` (`0x6EA1E`,`0xEEA1E`) | `$442D $B8C7 $0A` — addr `0x6EA24`/`0xEEA24`, qty `0x6EA26`/`0xEEA26` | `"100G"` `$6DA3` (`0x6EDA3`,`0xEEDA3`), `"10"` `$6DAC` (`0x6EDAC`,`0xEEDAC`) | **`100G`** — `[$B8C7] = min(99, [$B8C7] + 10)` (`+10` Stamina/meal) |

### Purchase Flow, Zero Hidden Checks & Vanilla 8-Bit Stamina-Overflow Bug (`1B/3B:$45D6`)
- **Stay-in-Shop Loop & Zero Hidden Checks (`1B/3B:$68AC..$6A87`, byte-identical across `1B` and `3B`):**
  - Welcome script `$68AC` prints `"Ahh, {PLAYER}. / Would you like ▼ to eat here or / get take-out?"` (`$6A88`). Every item uses clean unsigned 24-bit Gold subtraction `$4385` (no `$0CBB` `$FF` bug); `Buy` success (`$6A58` `"Thank you very much!"`), `Don't Buy` (`$6A70` `"Oh, I see."`; note Meat Dumpling renders lowercase `"Don't buy"` at `$6DBD`), and insufficient Gold (`$6A2B` `"I'm sorry, you don't have enough money."`) **all** converge on `$6A3F` (`"Anything else?"`) and loop inside the Restaurant so players can buy unlimited items per visit. Pressing `B` runs `$6A4C` (`"Thank you! Please come again!"`) and exits to Town Map (`0x13`).
  - Neither Happiness (`$B8F2`) nor the in-game clock (`$B882`) changes on any purchase, and there are **zero** one-time bonus items, gifts, or hidden event triggers anywhere in `09/29:$50F6..$51FD` or `1B/3B:$68AC..$6A87`.
- **Vanilla Bug — Missing Carry Check in Fractional Stamina Heal Opcode `1B/3B:$45D6` (`0x6C5D6` CGB / `0xEC5D6` DMG):**
  - Shared exclusively between **Restaurant** (`Cake` shift `2`, `Lunch Box` shift `1`) and **Juice Bar** (`1B/3B:$511E` shift `1`, `$5161` shift `0`). Computes `D = [$B8EE]` (Max Stamina), `E = D >> shift` (`floor($B8EE / 2^shift)`), then executes at `$45E6..$45EE` (`0x6C5E6..0x6C5EE` / `0xEC5E6..0xEC5EE`): `LD A,[$B8ED]; ADD E; CP D; JR C,$45EE; LD A,D; LD [$B8ED],A; RET` — **omitting a `JR C` check after `ADD E`!**
  - Max Stamina `$B8EE` starts at `100` (`$64`, `1D:$5291` / `3D:$524C`) and increases by `+10` per Power Berry up to **`200`** (`$C8` at `10` Power Berries, `08/28:$48DC`, `$B904` cap `10`).
  - For **Lunch Box** (`shift = 1`, `E = $B8EE >> 1`), safe through `7` Power Berries (`Max = 170`: `170 + 85 = 255`, `CP 170` clamps to `170`), but at **`8..10` Power Berries (`$B8EE = 180 / 190 / 200`, `E = 90 / 95 / 100`)**, buying whenever current Stamina `$B8ED >= 256 - E` (**`166 / 161 / 156`**, including full stamina!) overflows 8 bits and wraps `(Cur + E) & $FF < D`, **catastrophically draining Stamina while still charging `500G`** (confirmed live in CGB & DMG: `Max=180`: `166→0`, `180→14`; `Max=190`: `161→0`, `190→29`; `Max=200`: `155→200` safe, `156→0`, full `200→44`! Also hits Juice Bar `shift=1` at `Max >= 180` and `shift=0` at **every** `Max >= 130` / `3+` Power Berries!). `Cake` (`shift = 2`, max `200 + 50 = 250 < 256`) never overflows in vanilla.
  - Both `Cake` and `Lunch Box` also charge full price even when `$B8ED == $B8EE` (no full-stamina refusal guard). Optional QoL bugfix can insert `JR C,.clamp` (matching Bank `00`'s carry-safe Stamina helper `00:$1AE6`).
- **8-Bit Pantry Add Opcode `1B/3B:$442D` (`0x6C42D` CGB / `0xEC42D` DMG):**
  - Shared by Restaurant take-out (`$B8C5..$B8C7`, `+$0A`) and Juice Bar take-out (`$B8CA..$B8CC`, `+$0A` at `1B/3B:$51A4, $51E9, $5222`). Computes `A = [addr] + qty; CP $63; JR C,+2; LD A,$63; LD [addr],A` — charges full Gold even when clamping near cap (e.g., `95 + 10 → 99` for `200G`), and lacks a carry check before `CP $63` (safe for all `qty <= 157` since the shop hides `[addr] >= 99`; keep randomized bundle sizes `<= 99`).

### Take-Out Pantry SRAM (`$B8C5..$B8C7`) & Automatic Daily Meal Consumption
- **Storage, Starting Counts & Status Book:**
  - `Croissant` = **`$B8C5`** (`.sav[0x18C5]`), `Rice Ball` = **`$B8C6`** (`.sav[0x18C6]`), `Meat Dumpling` = **`$B8C7`** (`.sav[0x18C7]`), each 1-byte unsigned `0..99` (`$00..$63`). *(Adjacent `$B8C8..$B8C9` are unreferenced unused pantry bytes between foods and Juice Bar drinks `$B8CA..$B8CC`.)*
  - **New Game Init:** Both Male and Female farmers start with **`10` each** — written twice unconditionally during `Map 0x24` init: Pass 1 in Core Wipe (CGB `1D:$5274` / `0x75274`; DMG `3D:$522F` / `0xF522F`) and Pass 2 in Finalize (CGB `1D:$51EE` / `0x751EE`; DMG `3D:$51A9` / `0xF51A9`, which overwrites Pass 1 — see §12).
  - **Status Book Display:** Rendered as 2-digit counts by CGB `09:$5CD8` (`0x25CD8`) / DMG `29:$5C7F` (`0xA5C7F`).
- **Automatic 3-Meals-a-Day Consumption (`02:$5FC1` / `22:$5F2E` → `08/28:$41EE` → `00:$1AE6`):**
  - Take-out items are not manually equipped; they are automatically picked and consumed at **three daily meal triggers**: **06:00 Breakfast** on Farmhouse wake-up (`02:$60A5` / `22:$6012` when `$B900 == 1`), **12:00 Lunch** (`$CB90 = 1`, triggered at `01:$7821`), and **18:00 Supper** (`$CB90 = 2`, triggered at `01:$783C`, which also clears `$B890 = $B891 = 0`), handled on the Farm (`01:$7911` / `21:$77F1`) and in the Harvest Sprite Underground (`0E:$533F` / `2E:$531C`).
  - Picker CGB `02:$5FC1` (`0x09FC1`, 16-byte LUT `02:$5FAE` / `0x09FAE`) / DMG `22:$5F2E` (`0x89F2E`, LUT `22:$5F1B` / `0x89F1B`) indexes `[$FF9C] & $0F` across the 6-slot table `[$B8C5, $B8CA, $B8C6, $B8CB, $B8C7, $B8CC]` (falling back sequentially in that order if the chosen item is `0`, or setting `$CB8D = $FF` if all 6 are empty), decrements the picked byte by `1`, sets `$CB8D = 0 / 2 / 4` for `Croissant / Rice Ball / Meat Dumpling`, and heals Stamina via carry-safe **`00:$1AE6`** inside byte-identical `08/28:$41EE` (`0x201EE` / `0xA01EE`):
    - **Croissant (`$B8C5`, `$CB8D = 0`):** **`+30` (`$1E`)** Stamina — immediate at CGB `08:$4240` (`0x20240`) / DMG `28:$4240` (`0xA0240`).
    - **Rice Ball (`$B8C6`, `$CB8D = 2`):** **`+20` (`$14`)** Stamina — immediate at CGB `08:$425B` (`0x2025B`) / DMG `28:$425B` (`0xA025B`).
    - **Meat Dumpling (`$B8C7`, `$CB8D = 4`):** **`+10` (`$0A`)** Stamina — immediate at CGB `08:$4276` (`0x20276`) / DMG `28:$4276` (`0xA0276`).
  - All three take-out packs yield an identical **`1.0 Stamina / Gold`** (`300/300G`, `200/200G`, `100/100G`), differing in Stamina recovered per fixed daily meal slot.

### Live Verification Summary (CGB & DMG)
Verified live in PyBoy (with visual inspection of Town Map node `6`, 5-item welcome counter, CGB highlight palettes on `Cake`/`Lunch Box`/`Croissant`, `300G` prompt, `Rice Ball = 99` left-shift placing `Meat Dumpling` at Slot `3`, and DMG mode): hours (`08:00..17:59` open incl. Sunday & Winter; `07:00`/`18:00` closed), event flags (`$B890`/`$B891`/`$B899` closed; `$B89A` open), New Game pantry (`$B8C5..$B8C7 = 10` + automatic wake-up breakfast), all 4 seasons & 4 name classes (`$CD33 = 5`), consecutive in-shop `Cake` buys (`40 → 65 → 90`, `-300G` each, Happiness/hour unchanged), fractional `Cake` at `Max = 150` (`40 → 77`), full-stamina charge (`100 → 100`, `200 → 200` safe), `Lunch Box` (`40 → 90`, `-500G`, safe `170 → 170`) and live reproduction of the vanilla `1B/3B:$45D6` 8-bit overflow bug (`Max = 180`: `166 → 0`, `180 → 14`; `Max = 190`: `161 → 0`, `190 → 29`; `Max = 200`: `155 → 200`, `156 → 0`, `200 → 44`), `Croissant` (`10 → 20`, `-300G`), `Rice Ball` (`95 → 99` clamp, `-200G`, instant slot shift `$CD33: 5 → 4`, cursor stays `3`), `Meat Dumpling` at shifted Slot `3` (`10 → 20` & `95 → 99` last-slot cursor clamp `3 → 2`, `-100G`), all-`99` 2-slot minimum, stay-in-shop on insufficient Gold & `Don't Buy`, `B`-exit to `0x13`, live Farm meal consumption (`+30`/`+20`/`+10` + carry-safe `Max = 200: 190 → 200` clamp), and full DMG parity (`Banks 29 + 3B + 28 + 3D`, incl. `Lunch Box` `200 → 44` overflow parity).

---

## 10. Juice Bar (`Map ID 0x15` — Eve) & Take-Out Drink Pantry (`$B8CA..$B8CC`)

### Town Map Node, Night-Only Operating Hours & Map Dispatch
- **Town Map Node `2` (`Map ID 0x13`):** CGB **`09:$40FC..$410B`** (`0x240FC..0x2410B`) / DMG **`29:$40EF..$40FE`** (`0xA40EF..0xA40FE`, top-center building with mug sign and outdoor barrels, reached via `Left, Left` from Town entry; neighbor links `Left`/`Up` → Carpenter node `3`, `Right`/`Down` → Flower Shop node `1`).
  - **Unique Evening/Night Hours (`18:00..23:59`):** Only night venue on the Town Map — `open = $12` (`18`) at **`0x24109`** / **`0xA40FC`**, `close = $18` (`24`) at **`0x2410A`** / **`0xA40FD`**, `map_id = $15` at **`0x2410B`** / **`0xA40FE`** (checked via `$B882 >= $12 && $B882 < $18` at `09:$4277..$4281` / `29:$426A..$4274`). Open all 7 days of the week (including Sunday) across all 4 seasons (including Winter); closed when `$B890 != 0`, `$B891 != 0`, or `$B899 == $01` (note: `$B890` and `$B891` auto-clear at `18:00` on the Farm at `01:$783C`, and `$B899 == $03` leaves the bar open; unaffected by Church-only `$B89A == $01`).
- **Map `0x15` Dispatch & Graphics (`-0x21` shift across `09:$4D01..$4DF2` → `29:$4CE0..$4DD1`):**
  - **Init:** CGB `00:$02D6` → **`09:$4CC2`** (`0x24CC2`, sets CGB shop palette selector `$FFA4 = $01` → BG palette set `20:$51B1` / `0x811B1` via shared shop loader `09:$5435` → `20:$556C`, graphics descriptor `09:$4CCD` = `1C:$58A4` / `$5EE9` / `$61DA`, starts outer VM at `09:$4D01`) / DMG `00:$0354` → **`29:$4CA5`** (`0xA4CA5`, descriptor `29:$4CAC` = `3C:$5A8D` / `$60D6` / `$63C7`, starts outer VM at `29:$4CE0`).
  - **Loop:** CGB `00:$05B5` → **`09:$4CD8`** (`0x24CD8`) / DMG `00:$0633` → **`29:$4CB7`** (`0xA4CB7`).
  - **CGB Drink Highlight Palettes (`$FFA4 = $01`, `20:$57CA` → `20:$5807..$5810` / file `0x81807..0x81810`):** Updates BG Palette `7` dynamically by `(vram_tile - $50) / 4`:
    - idx `0` (`$50` Apple Juice) → `20:$5706` (`0x81706`: `$001F, $00A5, $013C, $469D` — red-orange glass)
    - idx `1` (`$54` Orange Juice) → `20:$56E6` (`0x816E6`: `$001F, $00A5, $02DF, $53FF` — golden-yellow glass)
    - idx `2` (`$58` Wild Grape Juice) → `20:$56FE` (`0x816FE`: `$001F, $00A5, $3C55, $663A` — purple goblet)
    - idx `3` (`$5C` Green Tea) → `20:$56D6` (`0x816D6`: `$001F, $00A5, $05E1, $07AA` — green cup)
    - idx `4` (`$60` Milk) → `20:$56EE` (`0x816EE`: `$001F, $00A5, $42D8, $7FFF` — white bottle)
    - Highlight lookup is keyed purely by `vram_tile`, so hiding or reordering slots preserves each drink's CGB palette automatically.

### Hybrid 5-Drink Slot Builder (`09:$4D7D` / `29:$4D5C`), Date & Milk Gates, and 37-Byte Master Table
Built on entry and rebuilt immediately after every counter interaction by CGB **`09:$4D7D..$4DBA`** (`0x24D7D`) / DMG **`29:$4D5C..$4D99`** (`0xA4D5C`) from the byte-identical 37-byte table at CGB **`09:$4DBB..$4DDF`** (`0x24DBB..0x24DDF`) / DMG **`29:$4D9A..$4DBE`** (`0xA4D9A..0xA4DBE`):
1. **`Orange Juice` (5B unconditional):** Always copied into Slot `0` (`$CD34..$CD38`, `$CD33 = 1`), so the Juice Bar always shows `1..5` drinks and never triggers a 0-slot auto-exit.
2. **`Apple Juice` (5B date-gated by CGB `09:$4DE0..$4DF2` / `0x24DE0`, DMG `29:$4DBF..$4DD1` / `0xA4DBF`):** Calls the shared 16-bit elapsed-day helper (`09:$50D0` / `29:$50A7`: `HL = 120*[$B885] + 30*[$B884] + [$B883]`, all 0-indexed) and subtracts `DE = $0086` (**`134`**, `u16` immediate at **`0x24DE7..0x24DE8`** CGB / **`0xA4DC6..0xA4DC7`** DMG). Hidden while `total_days < 134` (through **Year 2, Spring 14** = `120*1 + 30*0 + 13 = 133`); permanently unlocked starting **Year 2, Spring 15** (`120*1 + 30*0 + 14 = 134`), packing into Slot `1` and pushing all take-out drinks right by `1`. *(True 16-bit subtract — never re-locks in later years.)*
3. **`Wild Grape Juice` & `Green Tea` (9B filter via `09:$49AE` / `29:$4991`):** Stocked from Day 1 whenever `[$B8CA] < 99` / `[$B8CB] < 99` (`season_mask = $0F`, `name_mask = $1F`).
4. **`Milk` (gated by `[$B942] != 0` at `0x24DB0` / `0xA4D8F` + 9B filter `[$B8CC] < 99`):** Reads lifetime Milk Shipped counter **`$B942`** (`.sav[0x1942]`, capped at `$C9` = `201` — incremented by `+1` per S/M/L Milk shipped via the Barn shipping bin at CGB `03:$4FD0, $5049, $50C3` / DMG `23:$4F8A, $5003, $507D` or Harvest Sprite shipping at CGB `12:$6F2E` / DMG `32:$6F3E`; this is the **same** counter whose `>= $C8` (`200`) threshold unlocks Tool Shop's Cheese & Butter Makers at `09:$5040` / `29:$5017`). Shipping just **`1`** bottle of Milk (requires Cow + Milker) permanently unlocks `Milk` at the Juice Bar **and** unblanks the Status Book's Milk row (`09:$5D56` / `0x25D56` CGB; `29:$5CFD` / `0xA5CFD` DMG, which overwrites `$9962..$9971` & `$9922..$9931` with blank tiles while `$B942 == 0` even if `$B8CC > 0`!).

| Order | Drink (`HMGB-SHP`) | Record Offset (CGB / DMG) | Fmt / Unlock & Filter | Tile / CGB Pal | Hover Script / Text (`1B` == `3B`) | Buy Script / Price `u16` (`1B` == `3B`) | Effect Opcode & Operand(s) (`1B` == `3B`) | Price / Qty Strings (`1B` == `3B`) | Vanilla Price & Effect |
|---|---|---|---|---|---|---|---|---|---|
| `0` | **Orange Juice** (`SHP-030`) | `09:$4DBB` (`0x24DBB`) / `29:$4D9A` (`0xA4D9A`) | 5B (always shown) | `$54` / `20:$56E6` | `$7ACF` / `$7B01` (`0x6FB01`,`0xEFB01`) | `$50EE` / `$511A` (`0x6D11A`,`0xED11A`) | `$45D6 $01` — shift `01` at `$5120` (`0x6D120`,`0xED120`) | `"300G"` `$52E5` (`0x6D2E5`,`0xED2E5`) | **`300G`** — dine-in `+$B8EE >> 1` (`+50%` Max Stamina; **overflow bug at `Max >= 180`!**) |
| `1` | **Apple Juice** (`SHP-031`) | `09:$4DC0` (`0x24DC0`) / `29:$4D9F` (`0xA4D9F`) | 5B (`days >= 134` = **Y2 Spr 15**, `0x24DE7`/`0xA4DC6`) | `$50` / `20:$5706` | `$7AD9` / `$7B11` (`0x6FB11`,`0xEFB11`) | `$5125` / `$515D` (`0x6D15D`,`0xED15D`) | `$45D6 $00` — shift `00` at `$5163` (`0x6D163`,`0xED163`) | `"500G"` `$539F` (`0x6D39F`,`0xED39F`) | **`500G`** — dine-in `+$B8EE` (`+100%` Max Stamina; **severe overflow bug at `Max >= 130`!**) |
| `2` | **Wild Grape Juice (`×10`)** (`SHP-032`) | `09:$4DC5` (`0x24DC5`) / `29:$4DA4` (`0xA4DA4`) | 9B: `$B8CA, $0F, $1F` (`< 99`) | `$58` / `20:$56FE` | `$7AE3` / `$7B21` (`0x6FB21`,`0xEFB21`) | `$5168` / `$51A0` (`0x6D1A0`,`0xED1A0`) | `$442D $B8CA $0A` — addr `0x6D1A6`/`0xED1A6`, qty `0x6D1A8`/`0xED1A8` | `"300G"` `$542F` (`0x6D42F`,`0xED42F`), `"10"` `$5440` (`0x6D440`,`0xED440`) | **`300G`** — `[$B8CA] = min(99, [$B8CA] + 10)` (`+30` Stamina/drink) |
| `3` | **Green Tea (`×10`)** (`SHP-033`) | `09:$4DCE` (`0x24DCE`) / `29:$4DAD` (`0xA4DAD`) | 9B: `$B8CB, $0F, $1F` (`< 99`) | `$5C` / `20:$56D6` | `$7AED` / `$7B31` (`0x6FB31`,`0xEFB31`) | `$51AD` / `$51E5` (`0x6D1E5`,`0xED1E5`) | `$442D $B8CB $0A` — addr `0x6D1EB`/`0xED1EB`, qty `0x6D1ED`/`0xED1ED` | `"200G"` `$54B4` (`0x6D4B4`,`0xED4B4`), `"10"` `$54C0` (`0x6D4C0`,`0xED4C0`) | **`200G`** — `[$B8CB] = min(99, [$B8CB] + 10)` (`+20` Stamina/drink) |
| `4` | **Milk (`×10`)** (`SHP-034`) | `09:$4DD7` (`0x24DD7`) / `29:$4DB6` (`0xA4DB6`) | `$B942 != 0` (`0x24DB0`/`0xA4D8F`) + 9B `$B8CC, $0F, $1F` (`< 99`) | `$60` / `20:$56EE` | `$7AF7` / `$7B41` (`0x6FB41`,`0xEFB41`) | `$51F2` / `$521E` (`0x6D21E`,`0xED21E`) | `$442D $B8CC $0A` — addr `0x6D224`/`0xED224`, qty `0x6D226`/`0xED226` | `"100G"` `$5514` (`0x6D514`,`0xED514`), `"10"` `$5520` (`0x6D520`,`0xED520`) | **`100G`** — `[$B8CC] = min(99, [$B8CC] + 10)` (`+10` Stamina/drink) |

### Purchase Flow, Severe Apple Juice Overflow Bug & Take-Out Drink Consumption (`00:$1AFE`)
- **Direct Stay-in-Bar Loop & Zero Hidden Checks (`1B/3B:$50DC..$559B`, byte-identical across `1B` and `3B`):**
  - Greeting `$50DC` prints `"Hi. Would you / like a drink?"` (`$527C`). All 5 items deduct Gold via clean unsigned `$4385` (`u16` LE, no `$0CBB` `$FF` bug). `Buy` success (`$5258` → `$52FC` / `$5264` → `$53BC`, `"Here you go."`), `Don't Buy` (`$5270` → `$531C`, `"Really? It all / tastes good."`), and insufficient Gold (`$522B` → `$553C`, `"You don't have / enough money."`) all end at `$5233` (`$4523; $452A $FF; $453D`), returning **directly** to the counter item cursor (no extra `"Anything else?"` box). Pressing `B` runs `$5240` (`"Leaving? Are you / refreshed now? ▼ Come back soon."`, `$555C..$559B`) and exits to Town Map (`0x13`).
  - Zero Happiness (`$B8F2`) change, zero clock (`$B882`) advance, and **zero** one-time bonus items, Eve affection counters, or hidden event triggers anywhere in `09/29:$4CC2..$4DF2` or `1B/3B:$50DC..$559B`. *(Note: compared to Restaurant dine-in, `Orange Juice` heals `+50%` for `300G` — strictly outclassing `Cake` `+25%` for `300G` and costing `200G` less than `Lunch Box` `+50%` for `500G` — while `Apple Juice` heals `+100%` for `500G`!)*
- **Vanilla Bug — Severe `Apple Juice` & `Orange Juice` 8-Bit Stamina Overflow (`1B/3B:$45D6`, `0x6C5D6` / `0xEC5D6`):**
  - `Orange Juice` (`$45D6 $01`, `E = $B8EE >> 1`) exhibits the exact same 8-bit overflow as Restaurant's `Lunch Box` at **`8..10` Power Berries** (`$B8EE = 180 / 190 / 200` when `$B8ED >= 166 / 161 / 156`; e.g., `Max = 200`: `156 → 0`, `200 → 44`).
  - `Apple Juice` (`$45D6 $00`, `E = $B8EE`) is **far worse**: safe only at `0..2` Power Berries (`$B8EE = 100..120`, max `120 + 120 = 240 < 256`), it overflows for **every player with `3..10` Power Berries (`$B8EE = 130..200`)** whenever `$B8ED >= 256 - $B8EE` (`126` down to **`56`**!), wrapping `$B8ED` to `($B8ED + $B8EE) - 256` while charging `500G` (e.g., `Max = 130`: `125 → 130` safe, `126 → 0`, `130 → 4`; `Max = 200`: `55 → 200` safe, `56 → 0`, `200 → 144`!). A single carry-guard patch in `1B/3B:$45D6` cures `Lunch Box`, `Orange Juice`, and `Apple Juice` simultaneously.
- **Take-Out Drink Storage (`$B8CA..$B8CC`) & Home-Bank Stamina Handler (`00:$1AFE`):**
  - `Wild Grape Juice` = **`$B8CA`** (`.sav[0x18CA]`), `Green Tea` = **`$B8CB`** (`.sav[0x18CB]`), `Milk` = **`$B8CC`** (`.sav[0x18CC]`), each 1-byte unsigned `0..99` (`$00..$63`). Both Male and Female farmers start New Game with **`10` Wild Grape Juice, `10` Green Tea, and `0` Milk** — written twice unconditionally during `Map 0x24` init: Pass 1 at CGB `1D:$527F..$5288` (`0x7527F`) / DMG `3D:$523A..$5243` (`0xF523A`, incl. `$B8CC = 0`) and Pass 2 at CGB `1D:$51EE..$51F9` (`0x751EE`) / DMG `3D:$51A9..$51B4` (`0xF51A9`, rewriting `$B8C5..$B8C7, $B8CA, $B8CB = $0A` — see §12).
  - Interleaved directly with Restaurant foods in the 06:00 / 12:00 / 18:00 meal picker (`02:$5FC1` / `22:$5F2E`: `[$B8C5, $B8CA, $B8C6, $B8CB, $B8C7, $B8CC]` → `$CB8D = 0..5`), so 1 meal slot eats **either** 1 food or 1 drink. Whereas `08/28:$41EE` heals only even `$CB8D` (`0, 2, 4` foods) and returns early on odd values, drink Stamina is restored by a separate shared Bank `00` routine **`00:$1AFE..$1B21`** (`0x01AFE..0x01B21`, called from CGB `01:$793C,$7945`, `02:$611C,$6154,$61DB,$6210,$629A`, `0E:$536A,$5373` and DMG `21:$781C,$7825`, `22:$6089,$60C1,$6148,$617D,$6207`, `2E:$5347,$5350`), calling carry-safe `00:$1AE6` (no overflow even at `Max = 200`):
    - **Wild Grape Juice (`$B8CA`, `$CB8D = 1`):** **`+30` (`$1E`)** Stamina — single shared byte at **`00:$1B0F`** (`0x01B0F`).
    - **Green Tea (`$B8CB`, `$CB8D = 3`):** **`+20` (`$14`)** Stamina — single shared byte at **`00:$1B15`** (`0x01B15`).
    - **Milk (`$B8CC`, `$CB8D = 5`):** **`+10` (`$0A`)** Stamina — single shared byte at **`00:$1B1B`** (`0x01B1B`).
  - Note: randomizing `Milk` (`$B8CC`) as an early reward before the player ships Milk still heals `+10` Stamina cleanly at mealtime, though the Status Book hides the Milk row until `$B942 >= 1` (easily unhidden via `09:$5D56` / `29:$5CFD` if desired).

### Live Verification Summary (CGB & DMG)
Verified live in PyBoy (with visual inspection of Town Map node `2`, Day 1 3-drink counter, full 5-drink counter with all CGB drink highlight palettes on `Orange Juice`/`Apple Juice`/`Wild Grape Juice`/`Milk`, `Apple Juice` `500G` prompt, and DMG 5-slot mode): night-only hours (`18:00..23:59` open incl. Sunday & Winter; `00:00..17:59` closed), event flags (`$B890`, `$B891`, `$B899=1` closed; `$B899=3` and `$B89A=1` open), exact slot gates (`total_days = 0` & `133` Y2 Spr 14 → `3` drinks; `134` Y2 Spr 15 → `4` drinks with `Apple Juice` at Slot `1`; `$B942 = 1` → `Milk` added for `5` drinks; all take-outs at `99` → `1` slot `Orange Juice` only), all 5 purchases (`-300G/-500G/-300G/-200G/-100G`, `95 → 99` caps, instant left-pack slot shifts, Happiness/hour unchanged), live `1B/3B:$45D6` overflow on both `Orange Juice` (`Max = 200`: `156 → 0`, `200 → 44`) and `Apple Juice` (`Max = 120`: `120 → 120` safe; `Max = 130`: `125 → 130`, `126 → 0`, `130 → 4`; `Max = 200`: `55 → 200`, `56 → 0`, `200 → 144`), low-Gold & `Don't Buy` stay-in-bar loop, `B`-exit to `0x13`, live CGB & DMG Farm drink auto-consumption (`+30`/`+20`/`+10` + `Max = 200` carry-safe clamp), and full DMG parity (`Banks 29 + 3B + 22 + 00`).

---

## 11. Church (`Map ID 0x1A`) — Pray Miracles, Fortune Telling & Post-Game Organ Sound Test

### Town Map Node, Operating Hours, Unique `$B89A` Closure & Map Dispatch
- **Town Map Node `4` (`Map ID 0x13`):** CGB **`09:$411C..$412B`** (`0x2411C..0x2412B`) / DMG **`29:$410F..$411E`** (`0xA410F..0xA411E`, far-left middle path off-screen archway, reached via `Left, Left, Left, Left` from Town entry; `Left`/`Up` → Carpenter node `3`, `Right`/`Down` → Tool Shop node `5`).
  - **Operating Hours:** Open daily **`08:00..17:59`** (`open = $08` at **`0x24129`** / **`0xA411C`**; `close = $12` at **`0x2412A`** / **`0xA411D`**; `map_id = $1A` at **`0x2412B`** / **`0xA411E`**), all 7 days of the week (including Sunday) across all 4 seasons (including Winter). Closed during town-wide flags `$B890 != 0`, `$B891 != 0`, and `$B899 == $01`.
  - **Unique Church-Only Guest-Event Closure (`$B89A == $01`):** At CGB **`09:$4256..$425F`** (`0x24256`) / DMG **`29:$4249..$4252`** (`0xA4249`), Town entry explicitly checks `if node == 4 && [$B89A] == $01 → closed` (`"It's closed today."`). Set by the optional 3-day ranch-guest event (`07:$60B4` → `07:$73DB` / `27:$7378`, `"...can I please stay at your ranch for a while? YES / NO"` — `YES` sets `$B89A = 1` + `+10` Happiness; auto-clears to `$00` once `[$B883] == [$B89B] + 3` at `02:$6547,$6FBA` / `22:$64B4,$6F1A`, `07:$7417` / `27:$73B4`). No other Town building checks `$B89A`.
- **Map `0x1A` Dispatch, Graphics & Menu Loop (Non-Counter Architecture):**
  - **Init:** CGB `00:$02E5` → **`09:$55A1`** (`0x255A1`: loads bespoke Church CGB palette set via `20:$567B` → 8 BG palettes at `20:$563B..$567A` / `0x8163B` + VRAM1 attr map `20:$5593`, decompresses stained-glass/altar/Sister graphics from `06:$7771`, `1E:$6E55`, `1C:$780D`, plays Town/Church BGM `$54` via `00:$25C5`, starts 5-op outer VM script `09:$5661` / `0x25661` → inner script `1B:$620C`) / DMG `00:$0363` → **`29:$5563`** (`0xA5563`: decompresses `26:$76B8`, `3E:$6CF2`, `3C:$7C1A`, BGM `$54`, starts outer script `29:$5608` / `0xA5608` → `3B:$620C`).
  - **Loop:** CGB `00:$05C4` → **`09:$5641`** (`0x25641`) / DMG `00:$0642` → **`29:$55E8`** (`0xA55E8`).
  - **Inner Menu (`1B/3B:$620C..$6245`, `0x6E20C` / `0xEE20C`):** Greets `"Good Afternoon / {PLAYER}."` (`$644C`, even at `08:00` morning!), then loops at `$6224` (`"How can I help / you today?"` → `" Pray /  Fortune Telling"`, `$648C`) using custom 2-row + secret-`SELECT` menu opcode **`1B/3B:$4474..$4522`** (`0x6C474` / `0xEC474`) at `$623C`: `[0]` `Pray` → `$6246`, `[1]` `Fortune Telling` → `$631E`, `B` → `$642B` (`"Wishing you good luck and good fortune."` → exits to Town Map `0x13`), and hidden `SELECT` (when `[$B8D1] != 0`) → `$6367` (`Organ Sound Test`).
  - **Every Church service costs `0G`**, never advances the clock (`$B882`), and awards **zero** one-time inventory/tool checks; all RNG draws read the inner VM's frame counter **`$CD98`** (incremented once per frame at `1B/3B:$401C`, zeroed before timed pauses by `$4523`, so outcomes depend on the player's `A`-press frame).

### Service 1 — `Pray` (`1B/3B:$6246`, `0G`): Once-Per-Day Sick Cow Cure & Goddess Cow Blessing
After `"You really have a righteous heart and soul. Please come in and join the service."` (`$64AC..$650B`) + `$4523; $452A $FF` (skippable via `A`/`B`), opcode **`1B/3B:$46CA`** (`0x6C6CA` / `0xEC6CA`) checks the 4 cow slots (`$B949, $B95F, $B975, $B98B`, stride `$16`) for any sick cow (`state == $04`), branching to `$6273` (sick cow present) or `$62DE` (no sick cow). Both branches gate miracles through **`1B/3B:$46EC`** (`0x6C6EC` / `0xEC6EC`) using dedicated 1-byte daily flag **`$BA3F`** (`.sav[0x1A3F]`, cleared to `$00` each night at CGB `0F:$61D7` / `0x3E1D7` and DMG `2F:$61D7` / `0xBE1D7`):
- **Daily Lock (`[$BA3F] != 0`):** Immediately skips the RNG roll to the miss message `$627A` (`"Some day your prayers will be heard by the Harvest Goddess."`, `$686C..$68AB`) — so only the **first** `Pray` each day can ever roll a miracle (even if that first roll misses!).
- **First `Pray` of the Day (`[$BA3F] == 0`):** Sets `[$BA3F] = $01` and tests `[$CD98] & mask == 0`:
  1. **Sick Cow Present (`$6273`, `mask = $0F` at `0x6E275`/`0xEE275` → `1/16` = `6.25%` chance):**
     - On hit (`$6299`), calls **`1B/3B:$4707..$4729`** (`0x6C707` / `0xEC707`), which finds the **first** cow slot with `state == $04` and sets `cow[+$00] = $02` (healthy adult) and `cow[+$05] = $00` (clears sick-day death timer) — a free Cow Medicine (`1,000G` value; if multiple cows are sick, cures only the lowest-indexed sick cow) — and prints `"I have heard and understood your serious thoughts / Now, please go to your barn. / You will see a miracle there by the Harvest Goddess."` (`$676C..$680B`).
  2. **No Sick Cow (`$62DE`, `mask = $1F` at `0x6E2E0`/`0xEE2E0` → `1/32` = `3.125%` chance):**
     - On hit (`$62E5`), calls **`1B/3B:$472A..$4755`** (`0x6C72A` / `0xEC72A`), looping over **all 4** cow slots: for every healthy adult cow (`cow[+$00] == $02`) whose sickness-shield byte `cow[+$14] == $00` (`$B95D, $B973, $B989, $B99F`), writes **`cow[+$14] = $07`** (immature calves `$00`/`$01` and pregnant cows `$03` are skipped; existing non-zero shields such as `P.Medicine`'s `$1E` = `30` from `03:$4B22` / `23:$4AE9` are **never** downgraded), then prints `"...The Harvest Goddess will bless your cow. / Your cow is now protected!!"` (`$676C, $680C..$686B`).
     - **Sickness-Shield Mechanics Discovery (`cow[+$14]`):** Across the entire ROM, `cow[+$14]` only decrements (`DEC [HL]` at CGB `03:$7180` / `0x0F180`, DMG `23:$713A` / `0x8F13A`) on nights a `state == $02` cow is left **unfed** (`BIT i, [$B948] == 0`), spending `1` charge to block unfed sickness. While `cow[+$14] > 0`, it also blocks bad-weather sickness (`03:$71E3` / `23:$719D`), seasonal-change sickness (`03:$7265` / `23:$721F`), and tool-hit sickness (`03:$65F7..$66D8` / `23:$65B1..$6692`) **without decrementing** (and renders the Barn status immunity marker at `08:$529D` / `28:$52A2`) — meaning a well-fed blessed cow keeps her `$07` Goddess protection indefinitely (cleared only by `7` unfed nights or Link Cable trade at `00:$2628`)!
     - **Vanilla 0-Cow Dialogue Quirk:** `$62DE` never checks whether the player owns a cow, so a `1/32` hit with `0` cows (or only calves/already-blessed cows) still plays `"Your cow is now protected!!"` harmlessly with no memory corruption.

### Service 2 — `Fortune Telling` (`1B/3B:$631E`, `0G`, Unlimited Repeats)
Prints `"Would you like me to tell you your fortune? I am happy to do it. {PLAYER}, your fortune today is ...."` (`$650C..$658B`), pauses `128` frames (`$4523; $452A $80` at `$635A`, skippable with `A`/`B`), and dispatches via **`1B/3B:$4545`** (`0x6C545` / `0xEC545`) using the 16-byte LUT at **`1B/3B:$4560..$456F`** (`0x6C560` / `0xEC560`: `00 04 02 02 02 02 00 02 04 02 02 02 00 02 02 04`, indexed by `[$CD98] & $0F`), with **no daily lock** (unlike `Pray`, repeatable endlessly for `0G` — averaging `0` net per roll, or deterministically farmable for full Stamina & `255` Happiness!):

| Outcome | LUT Val / Indices (`$CD98 & $0F`) | Probability | Script / Effect Opcode (`1B` == `3B`) | Exact SRAM Effect (Clamps & Overflow Safety) |
|---|---|---|---|---|
| **Great** (`"Your fortune looks great!"`) | `$00` (`0, 6, 12`) | **`3/16` (`18.75%`)** | `$63B2` / `$4570` (`0x6C570`, `0xEC570`) | **`$B8ED = min($B8EE, $B8ED + $0A)`** (`+10` Stamina, operand `0x6C579`/`0xEC579`; safe at `Max = 200` since `210 <= 255`) **and** **`$B8F2 = min(255, $B8F2 + $0A)`** (`+10` Happiness, operand `0x6C585`/`0xEC585`, carry-clamped) |
| **Normal** (`"Your fortune looks normal."`) | `$02` (`2..5, 7, 9..11, 13..14`) | **`10/16` (`62.50%`)** | `$63DF` / `$458F` (`0x6C58F`, `0xEC58F`) | No-op (`0` Stamina, `0` Happiness) |
| **Bad** (`"Your fortune doesn't look good"`) | `$04` (`1, 8, 15`) | **`3/16` (`18.75%`)** | `$6405` / `$4592` (`0x6C592`, `0xEC592`) | **`$B8ED = max(0, $B8ED - $0A)`** (`-10` Stamina, operand `0x6C597`/`0xEC597`) **and** **`$B8F2 = max(0, $B8F2 - $0A)`** (`-10` Happiness, operand `0x6C5A2`/`0xEC5A2`), both borrow-clamped to `0` |

### Secret Post-Game Feature — `Organ Sound Test` (`$B8D1` + `SELECT`, `1B/3B:$6367`, 40 Tracks)
- **Unlock Flag (`$B8D1`, `.sav[0x18D1]`):** Set to `$01` exclusively by the best Boy/Girl Grand Ranch Master ending handlers at CGB **`10:$52B2..$52D1`** (`0x412BE, 0x412CE`, `Dlg $C7`/`$C8`) / DMG **`30:$521D..$523C`** (`0xC1229, 0xC1239`), gated at `10:$546E..$54E5` / `30:$53D9..$5450` (`$B904 >= 6` Power Berries, RTC auto-grow hours `$BA10:$BA11 <= $00F0` [`240`], Happiness `$B8F2 >= $FA` [`250`], Gold `>= $010000` [`65,536G`], plus post-ending continuation requirements). *(Also unlocks an interactable commemorative plaque in the Farmhouse at CGB `02:$515E, $5A1D` / DMG `22:$511A, $598A`, `Dlg $52`.)*
- **Activation & Controls (`1B/3B:$4489` → `$6367..$63B1`, opcodes `$4779, $4785, $4797, $47EB`):**
  - Pressing **`SELECT`** on the `"Pray / Fortune Telling"` menu when `[$B8D1] != 0` jumps to `$6367` (`"Would you like to listen to the organ? It will put you in a good mood."`), zeroes track selector **`$CB68 = $00`** and stops BGM (`$4779`), and enters the 40-track jukebox loop (`$638D..$639C`).
  - **Vanilla Reversed D-Pad Quirk (`1B/3B:$47A4..$47E9`):** Pressing **`Left`** (`BIT 5, [$CD24]`) **increments** `$CB68` (`0 → 1 → ... → 39 → 0`), while pressing **`Right`** (`BIT 4, [$CD24]`) **decrements** `$CB68` (`0 → 39 → ... → 1 → 0`); `Up`/`Down` do nothing.
  - **`A`** (`$47B1`) plays track `[$CB68]` via CGB **`09:$6A24`** (`0x26A24`, 80B table **`09:$6A5E..$6AAD`** / `0x26A5E`) / DMG **`29:$69CB`** (`0xA69CB`, identical 80B table **`29:$6A05..$6A54`** / `0xA6A05`): `[0]` `'Stop Playing'` (`$4D`), `[1..13]` 13 BGMs/jingles (`'Opening'`, `'Enter A Name'`, `'Spring'`, `'Summer'`, `'Fall'`, `'Winter'`, `'Happy Event'`, `'Sad Event'`, `'Staff Credits'`, `'Exchange'`, `'HarvestSprites'`, `'GrandpaAppears'`, `'Town'`), and `[14..39]` 26 sound effects (`'SE00'..'SE25'`). Track name loader: CGB **`09:$6882`** (`0x26882`, 40 pointers at `09:$68C6`) / DMG **`29:$6829`** (`0xA6829`, 40 pointers at `29:$686D`). *(The far-call targets in `$478A..$478F` and `$47B4..$47B9` account for **5 of the only 8 byte differences** between the entire 16 KB `Bank 1B` and `Bank 3B` — the remaining 3 being bank self-ID `$4000` and `Bank 11`/`31` at `$405D, $40C4`!)*
  - **`B`** (`$47C3` → `$47EB`) resets `$CB68 = $00`, restores Church BGM `$54` (`00:$25C5`), prints `"Please come again."`, pauses `255` frames, and returns cleanly to the Church menu.

### Live Verification Summary (CGB & DMG)
Verified live in PyBoy (with visual inspection of Town Map node `4`, Church altar & `"Pray / Fortune Telling"` menu, Sick Cow Miracle dialogue, Goddess Cow Blessing dialogue, Great & Bad Fortune dialogues, CGB Secret Organ playing `'Spring'`, and DMG Secret Organ playing `'Opening'`): hours (`08:00..17:59` open incl. Sunday & Winter; `07:00`/`18:00` closed), event flags (`$B890`, `$B891`, `$B899=1`, and Church-unique `$B89A=1` closed; `$B899=3` open), `Pray` sick-cow miracle cure (`1/16`, `state $04 → $02`, `sick_days → 0`, `$BA3F = 1`, `0G`), `Pray` Goddess Cow Blessing (`1/32`, healthy adult `cow[+$14]: 0 → $07`, calf `$01` skipped, existing `P.Medicine` `$1E` preserved, plus 0-cow dialogue quirk), `Pray` miss & `$BA3F = 1` once-per-day lock, all `16/16` `Fortune Telling` LUT entries (`3` Great `+10/+10` with `Max = 200` & `255` clamps, `10` Normal `0/0`, `3` Bad `-10/-10` with `0/0` clamps, `0G` unlimited loop), Secret Organ Sound Test (`$B8D1 = 0` blocked on `SELECT`; `$B8D1 = 1` + `SELECT` opens; inverted `Right` `0 → 39` & `Left` `39 → 0 → 3` wrap; `'Spring'` play; `B` resets `$CB68 = 0` & restores BGM `$54` back to menu), `B`-exit to `0x13`, and full DMG parity (`Banks 29 + 3B`, incl. `$B89A = 1`, `Pray` cure & blessing, `Fortune Telling` `+10/+10`, and the 5 differing `3B → 29:$6829/$69CB` Organ far-call bytes).

---

## 12. Starting Inventory, Character Creation (`Map ID 0x12`), New Game Init (`Map ID 0x24`) & 31-Slot Tool System

### Boot Flow, Active-Map Registers (`$C0A7` vs. `$CB50`) & Dual Save Signature (`1D:$440C` / `3D:$43F2`)
- **Boot & New-Game Map Sequence (`Banks 0x1D` CGB / `0x3D` DMG):**
  - `Map 0x00` (Natsume / Victor logos) → `Map 0x10` (Opening Attract / Title, CGB `1D:$4001` / `0x74001`; `START` executes direct switch `LD A,$11; LD [$C0A7],A; LD A,$01; LD [$C0A6],A` at CGB `1D:$4321` / `0x74321`, DMG `3D:$4314` / `0xF4314`) → `Map 0x11` (Title / `Start`–`Continue` menu, CGB `1D:$435D` / `0x7435D`; choosing `Start` switches directly via `LD A,$12; LD [$C0A7],A; LD A,$01; LD [$CB92],A` at CGB `1D:$4570` / `0x74570`, DMG `3D:$4556` / `0xF4556`) → **`Map 0x12`** (Character Creation Wizard, CGB `1D:$46A6` / DMG `3D:$468C`; confirming Pet Name sets fade-target `LD A,$24; LD [$CB50],A; LD A,$1D; LD [$CB4F],A` at CGB `1D:$4993` / `0x74993`, DMG `3D:$4961` / `0xF4961`) → **`Map 0x24`** (one-shot New Game SRAM Initializer, dispatched from `00:$0303` → CGB **`1D:$5192`** / `0x75192`, `00:$0381` → DMG **`3D:$5160`** / `0xF5160`; exits via `$CB50 = $0E, $CB4F = $02` at `1D:$5212` / `3D:$51CD`) → **`Map 0x0E`** (Grandpa's Spirit Intro speech, `Dlg 0xC9`) → **`Map 0x02`** (Farmhouse, **Year 1, Spring 1 [Sunday], `06:00 AM`**).
  - Note: `$C0A7` is the engine's live active map ID (`00:$0297` CGB / `00:$0315` DMG 3-byte `<addr16, bank8>` dispatch), while `$CB50` only updates on fade transitions (`0x10 → 0x11 → 0x12` bypass `$CB50` via `$C0A7`/`$C0A6`).
- **Dual Save-Validity Check (CGB `1D:$440C` / `0x7440C`; DMG `3D:$43F2` / `0xF43F2`):**
  - Clears WRAM `$CD61 = 0`, then validates **both**:
    1. **16-byte ASCII Magic String at SRAM `$BFF0..$BFFF` (`.sav[0x1FF0..0x1FFF]`):** `"BackUp_Check!#$%"` (`42 61 63 6B 55 70 5F 43 68 65 63 6B 21 23 24 25`, ROM source CGB `1D:$4438` / `0x74438`, DMG `3D:$441E` / `0xF441E`).
    2. **16-byte Top-Row Farm Border at SRAM `$A002..$A011` (`.sav[0x0002..0x0011]`):** Must equal 8 consecutive `($0E, $01)` permanent border cells (checked at CGB `1D:$441C` / `0x7441C`, DMG `3D:$4402` / `0xF4402`). *(Any future farm-layout randomizer must preserve `$A002..$A011` or adjust `1D:$441C` / `3D:$4402`, or `Continue` will disappear!)*
  - Only when both pass does `$CD61 = 1` (defaulting the `Map 0x11` cursor `$CD66` to `1` = `Continue` at `1D:$43DE` / `3D:$43C4`); corrupting even 1 byte in either region drops `$CD61 = 0` and boots `Start` straight into Player Name entry without an overwrite warning.

### Character Creation Wizard (`Map ID 0x12`, `Banks 0x1D` & `0x3D`)
Stages results exclusively in WRAM `$CD75..$CD7E` before touching SRAM (there is **no** Ranch Name prompt and **no** petless option; `$B8DB` is strictly binary):
1. **Player Name (`$CD75..$CD78` → SRAM `$B8F4..$B8F7`, `.sav[0x18F4..0x18F7]`):** 4-character buffer `$CD6B..$CD6E` prefilled with 4×`$4E` (underscore) on a `13×6` grid (`$CD65` = col `0..12`, `$CD66` = row `0..5`, coord LUT CGB `1D:$4CE3` / DMG `3D:$4CB1`, compressed tilemap CGB `1D:$6C4B` / DMG `3D:$6AFB` → `$CD7F` lookup at `1D:$4CBA` / `3D:$4C88`; `A` types, `B` backspaces, `START` jumps to `End` `$FC` at `(11, 5)`). Confirm validator CGB **`1D:$4A49`** (`0x74A49`) / DMG **`3D:$4A17`** (`0xF4A17`) rejects all-blank names (all `$4E`/`$D8`, plays error SFX `$51`), then copy loop **`1D:$4A81`** (`0x74A81`) / **`3D:$4A4F`** (`0xF4A4F`) converts trailing `$4E` underscores to **`$D8`** (space tile, e.g. `"A"` → `[$00, $D8, $D8, $D8]`). **Gameplay impact:** First byte **`$B8F4`** selects the vanilla Fall/Winter Flower Shop crop seed subset via `09:$4A09` / `29:$49EC` (§5).
2. **Player Gender (`"You are a Male? / Female?"`, `$CD69 = 3`, `$CD79` → SRAM `$B8EC`, `.sav[0x18EC]`):** Vertical cursor `$CD64` defaults to `0` (`Male`), toggles with `Up`/`Down` to `1` (`Female`). Stored directly via `LD A,[$CD64]; LD [$CD79],A` at CGB **`1D:$4FAF`** (`0x74FAF`) / DMG **`3D:$4F7D`** (`0xF4F7D`): **`$00` = Male (Boy)**, **`$01` = Female (Girl)**.
3. **Pet Choice (`"Which do you like?"`, `$CD69 = 4`, `$CD7A` → SRAM `$B8DB`, `.sav[0x18DB]`):** Vertical cursor `$CD64` defaults to `0` (`Dog`), toggles to `1` (`Cat`). **Inverted via `XOR $01`** (`LD A,[$CD64]; XOR $01; LD [$CD7A],A`) at CGB **`1D:$5046`** (`0x75046`) / DMG **`3D:$5014`** (`0xF5014`): **`$01` = Dog** (top option `0`), **`$00` = Cat** (bottom option `1`).
4. **Pet Name (`$CD7B..$CD7E` → SRAM `$B8DC..$B8DF`, `.sav[0x18DC..0x18DF]`):** Same 4-char grid, blank-name rejection, and `$D8` right-padding; confirming jumps to `1D:$4993` / `3D:$4961` (`$CB50 = $24, $CB4F = $1D`).

### New Game SRAM Initializer (`Map ID 0x24`, CGB `1D:$5192..$530D` / DMG `3D:$5160..$52C8`)
CGB `1D:$5192..$51A4` first clears LCD/OAM and zeroes 16 bytes at `$9000..$900F`, then falls into **`1D:$51A5..$530D`** (`0x751A5..0x7530D`, `361` bytes), which matches DMG **`3D:$5160..$52C8`** (`0xF5160..0xF52C8`, `361` bytes) at an exact uniform **`-0x45`** address shift (differing in only `10` bank/relocated-address operand bytes across all `361` bytes, producing a **0-byte difference across all 8,192 bytes of SRAM** between CGB and DMG). Execution runs **Phase 1 (Core Wipe)** via `CALL $521E` / `CALL $51D9` first, then **Phase 2 (Finalize)** at `$51A8..$521C` / `$5163..$51D7`:

| Exec Step | CGB Addr (`File`) | DMG Addr (`File`) | SRAM Target(s) (`.sav` Offset) | Vanilla Value & Subsystem Effect |
|---|---|---|---|---|
| **1.1** SRAM Clear | `1D:$521E..$522B` (`0x7521E`) | `3D:$51D9..$51E6` (`0xF51D9`) | `$A000..$BFFE` (`0x0000..0x1FFE`) | `CALL $0CD5` (MBC3 RTC/SRAM enable), then `00:$2282` zeroes `$1FFF` bytes (`$B884` Spring `0`, `$B885` Year `0`=Y1, `$B8F2` Happiness `0`, `$B911` Starter House `0`, `$B938:$B939` Material `0`, `$B93A:$B93B` Fodder `0`, `$B946/$B947` Feed `0`, `$B8EA` Horse `0`, all tool/seed/maker/check flags `0`) |
| **1.2** Livestock Init | `1D:$522C..$5233` (`0x7522C`) | `3D:$51E7..$51EE` (`0xF51E7`) | `$B949..$BA06` (`0x1949..0x1A06`) | Far-calls byte-identical CGB `03:$762A` (`0x0F62A`) / DMG `23:$75E4` (`0x8F5E4`): 4 empty Cow states `$B949,$B95F,$B975,$B98B = $FF` + `$AF`-padded names, `$B9A1 = $0F`, `$B9A2 = $FF`, 4 empty Chicken states `$B9A7,$B9B3,$B9BF,$B9CB = $FF`, Coop coords `$B9EB,$B9ED = $18` & `$B9EF,$B9F1 = $1E`, `$BA06 = $E0` |
| **1.3** Farm Template | `1D:$5249..$524B` (`0x75249`) | `3D:$5204..$5206` (`0xF5204`) | `$A000..$B87F` (`0x0000..0x187F`) | `CALL $1301` copies shared `64×49` = `3,136` 2-byte cells (`6,272` bytes in 4×16-col strips) from `11:$5001..$6880` (`0x45001..0x46880`): `1,675` clear arable `($00,$00)`, `311` weeds `($02,$01)`, `122` small rocks `($03,$01)`, `24` large `2×2` boulders (`$04..$07`), `21` `2×2` wood stumps (`$08..$0B`), `6` pre-tilled `($0C,$00)`, fences/buildings/borders |
| **1.4** Clock & Misc | `1D:$5254..$5273` (`0x75254`) | `3D:$520F..$522E` (`0xF520F`) | `$B882,$B883,$B892,$B89B,$B8A1..$A2,$BA4C..$4D` | Hour `$B882 = $06` (`06:00 AM`), pre-day `$B883 = $FF` (bumped to `$00` = Day 1 on first `Map 0x02` entry), `$B892 = $B89B = $80`, shipping/link sentinels `$B8A1 = $B8A2 = $BA4C = $BA4D = $FF` |
| **1.5** Pantry Pass 1 | `1D:$5274..$5288` (`0x75274`) | `3D:$522F..$5243` (`0xF522F`) | `$B8C5..$B8C7, $B8CA..$B8CC` | Sets `$B8C5,$B8C6,$B8C7,$B8CA,$B8CB = $0A` (`10` each), `$B8CC = $00` (`0` Milk) — **overwritten by Step 2.5!** |
| **1.6** Stamina & Berries | `1D:$5289..$52A1` (`0x75289`) | `3D:$5244..$525C` (`0xF5244`) | `$B8EB,$B8ED,$B8EE,$B906,$B907,$B904` | Horse maturity day `$B8EB = $80`; **Stamina `$B8ED` = Max Stamina `$B8EE` = `$64` (`100`)** (imm. at **`0x7528F` / `0xF524A`**); `$B906 = $01`; Tool Shed flag `$B907 = $00`; **Power Berries `$B904 = $00`** (`0x7529F` / `0xF525A`) |
| **1.7** Rucksack & Pos | `1D:$52A2..$52C2` (`0x752A2`) | `3D:$525D..$527D` (`0xF525D`) | `$B8F8..$B8FB, $B901..$B903` | All 3 rucksack slots empty (`$B8F8 = $B8F9 = $B8FA = $FF`), active slot index **`$B8FB = $02`** (bare hands); `$B901 = $00, $B902 = $16, $B903 = $00` |
| **1.8** Starting Gold | `1D:$52C3..$52D3` (`0x752C3`) | `3D:$527E..$528E` (`0xF527E`) | `$B8EF..$B8F1` (`0x18EF..0x18F1`) | **`$0001F4` = `500G`** (`$F4` imm. at **`0x752C4`/`0xF527F`**, `$01` imm. at **`0x752C9`/`0xF5284`**, `XOR A` at **`0x752CD`/`0xF5288`**) + `CALL $1056` (clamps to `99,999G` / `$01869F` & formats `$B928..$B92C`) |
| **1.9** Formatters & Sun | `1D:$52D4..$530D` (`0x752D4`) | `3D:$528F..$52C8` (`0xF528F`) | `$B912..$B932, $BA43..$BA5B` | Calls `00:$070B` (hour), `00:$07AB` (day), `00:$08B7` (season), `00:$1002` (Fodder `0..999` clamp + `$B92D..$B92F`), `00:$0F73` (Material `0..999` clamp + `$B930..$B932`), far-call `08/28:$456E` (active tool HUD), `00:$0FC7` (`$BA44..$BA48`); zeroes `$B89F..$B8A0`; Day 1 weather Sunny (`$B924..$B927 = $50,$83,$AF,$AF`); `$BA43 = $02` |
| **2.1** Save Signature | `1D:$51A8..$51B5` (`0x751A8`) | `3D:$5163..$5170` (`0xF5163`) | `$BFF0..$BFFF` (`0x1FF0..0x1FFF`) | Copies 16B `"BackUp_Check!#$%"` via `00:$2308` |
| **2.2** Name & Gender | `1D:$51B6..$51C7` (`0x751B6`) | `3D:$5171..$5182` (`0xF5171`) | `$B8F4..$B8F7, $B8EC` | Copies 4B Player Name via `1D:$499E` / `3D:$496C`; copies `$CD79 → $B8EC`; `OR A; JR Z,+23` skips Step 2.3 if Male (`$B8EC == 0`) |
| **2.3** Girl Starter Kit | `1D:$51C8..$51DE` (`0x751C8`) | `3D:$5183..$5199` (`0xF5183`) | `$B8B5/$B8D2, $B8B6/$B8D3, $B8B4/$B8DA, $B8AE` | `23` bytes (`$B8EC != 0` only): `LD A,$01` stored to Turnip (`$B8B5,$B8D2`), Potato (`$B8B6,$B8D3`), Grass (`$B8B4,$B8DA`), and **Water Can** (`$B8AE` at `1D:$51DC` / `3D:$5197`) |
| **2.4** Pet & Pet Name | `1D:$51DF..$51ED` (`0x751DF`) | `3D:$519A..$51A8` (`0xF519A`) | `$B8DB, $B8DC..$B8DF` | Unconditional merge: copies `$CD7A → $B8DB` (`$01` Dog / `$00` Cat) and 4B Pet Name `$CD7B..$CD7E → $B8DC..$B8DF` |
| **2.5** **Pantry Pass 2** | `1D:$51EE..$51F9` (`0x751EE`) | `3D:$51A9..$51B4` (`0xF51A9`) | `$B8C5..$B8C7, $B8CA..$B8CB` | **Unconditional clobber (`3E 0A 21 C5 B8 22 22 22 23 23 22 22`):** writes `$0A` (`10`) to Croissant `$B8C5`, Rice Ball `$B8C6`, Meat Dumpling `$B8C7`, skips unused `$B8C8..$B8C9`, and writes `$0A` (`10`) to Wild Grape Juice `$B8CA` & Green Tea `$B8CB` (leaving Milk `$B8CC = 0` from Step 1.5; verified live that patching only Step 1.5 is silently overwritten back to `10`!) |
| **2.6** 4 Base Tools | `1D:$51FA..$5207` (`0x751FA`) | `3D:$51B5..$51C2` (`0xF51B5`) | `$B8A3..$B8A6` (`0x18A3..0x18A6`) | Unconditional (`3E 01 EA A3 B8 EA A4 B8 EA A5 B8 EA A6 B8`): places **Sickle** (`$B8A3`), **Hoe** (`$B8A4`), **Hammer** (`$B8A5`), and **Ax** (`$B8A6`) into the Tool Shed; then sets `$CB50 = $0E, $CB4F = $02` (`$5212` / `$51CD`) |

### Grandpa's Intro (`Map 0x0E`) → Spring 1, `06:00 AM` Farmhouse (`Map 0x02`) Transition
- **Intro Exit (CGB `10:$527F` / `0x4127F`; DMG `30:$51E1` / `0xC11E1`):** Zeroes RTC auto-grow hours `$BA10:$BA11` and `$BA49` (plus `$B93F..$B941` on DMG, all already `$00`), keeps `$BA43 = $02`, and transitions to Farmhouse (`$CB50 = $02, $C910 = $01`). Zero inventory bytes touched.
- **First Morning Rollover & Automatic Breakfast (`Map 0x02`):** Increments `$B883: $FF → $00` (Spring 1, Year 1 = Sunday), skips all overnight farm/animal decay via `LD A,[$B884]; LD B,A; LD A,[$B883]; OR B; JR Z,+24` at CGB **`02:$67D7`** (`0x0A7D7`) / DMG **`22:$6744`** (`0x8A744`), and runs the normal `06:00 AM` wake-up breakfast picker (`02:$5FC1` / `22:$5F2E`), consuming **`1`** RNG-selected pantry item before player control begins — so on the first controllable frame of Spring 1, total pantry stock is **`49`** (`1` slot at `9`, `4` slots at `10`, Milk at `0`; custom starting `Milk > 0` can also be drunk cleanly on Day 1 without owning a cow). Across all 8 KB of SRAM, only `14` bytes change between `Map 0x24` return and Day 1 `06:00 AM` (`$B883`, `$B888`, `$B889`, `$B88B`, `1` decremented pantry byte in `$B8C5..$B8CC`, `$B93C`, `$B90C..$B90D`, and formatted day/weekday tiles `$B914,$B919..$B91A,$BA53..$BA55`) — **zero** tools, seeds, rucksack slots, gold, berries, happiness, wood, fodder, feed, or livestock are modified.

### Gender (`$B8EC`) & Pet (`$B8DB`) Full-ROM Impact Audit
- **Boy (`$B8EC = 0`) vs. Girl (`$B8EC = 1`) — `42` ROM refs (`21` CGB + `21` DMG):**
  - Full 8 KB SRAM diff between a fresh Boy and Girl file (same pet) is **exactly `8` bytes**: `$B8EC` plus the `7` Girl bonus bytes (`$B8AE` Water Can, `$B8B4/$B8DA` Grass ×1, `$B8B5/$B8D2` Turnip ×1, `$B8B6/$B8D3` Potato ×1 — `670G` of seeds + Water Can). Starting Gold (`500G`), Stamina (`100/100`), Berries (`0`), Pantry (`50` → `49`), Rucksack (`3×$FF, slot 2`), Wood/Fodder/Feed (`0`), Happiness (`0`), and Farm layout are 100% identical.
  - **`HMGB-CHK-002` Coupling (Verified Live CGB & DMG):** Vanilla Girl (`$B8AE = 1`) and any starter loadout containing Water Can (in Shed `$B8AE = 1` or equipped `$0A`) or Sprinkler (`$B8AF = 1` / `$12`) skip Nina's first-crop-purchase bonus at `1B/3B:$717A`; clearing a Girl's `$B8AE = 0` (with no Sprinkler) causes Nina to award the free Water Can normally (`$B8AE: 0 → 1`).
  - **Other `$B8EC` Sites:** Player sprite/palette selection (`00:$2FB8`, `01/21`, `03/23`, `06/26`, `07/27`, `09/29`, `0E/2E`, `0F/2F`, CGB `20:$58D7`), Girl-only Year 3 Fall 1 morning event `$0D` (`$B885 == 2 && $B899 != 0 && $B8EC != 0` at CGB `02:$6FAE` / `0x0AFAE`, DMG `22:$6F0E` / `0x8AF0E`), and Male vs. Female Grand Ranch Master ending IDs `$0B` vs. `$0C` (CGB `10:$548F,$54D3` / `0x4148F,0x414D3`; DMG `30:$53FA,$543E` / `0xC13FA,0xC143E`).
- **Dog (`$B8DB = 1`) vs. Cat (`$B8DB = 0`) — `40` ROM refs (`21` CGB + `19` DMG):**
  - Verified independently across all 4 `(Gender × Pet)` pairs: `$B8DB` governs **only** pet sprites/palettes (`01/21`, `02:$642D`/`22:$639A`, `06:$560F`/`26:$5556`, CGB `1F:$7890`), voice SFX (`08/28:$44F0`, `08:$5F16`/`28:$5F25`: Cat `$2E`, Dog `$2F`), movement AI (`08:$5A3D`/`28:$5A36`: Dog follow vs. Cat wander), Status Book icon/label (`09:$57AC,$588E,$58A2` / `29:$5753,$5835,$5849`), and Game Link pet trade (`1F:$5556` / `3F:$5445`). It has **zero** effect on starting inventory, seeds, wild animals/moles, livestock protection, or check logic. *(Companion SRAM: `$B8E0` unreferenced, `$B8E1` indoor/outdoor flag, `$B8E2..$B8E5` pixel X/Y; note `$BA28..$BA33` is Chicken runtime scratch RAM, not moles.)*

### 31-Slot Master Tool Table (`00:$1B20`), Tool Shed Layout (`Map 0x05`) & Rucksack Rules
Every equippable item is indexed `i = 0..30` across three parallel tables: 1-byte **Tool ID** at **`00:$1B20 + i`** (`0x01B20..0x01B3E`), 14-byte `$AF`-padded **Name** at **`00:$1B3F + 14*i`** (`0x01B3F..0x01CE2`), and 1-byte **Storage Presence Flag** at **SRAM `$B8A3 + i`** (`.sav[0x18A3..0x18C1]`, `0` = absent/equipped, `1` = sitting on shelf/barrel). On `Map 0x05` init, CGB **`05:$45B6`** (`0x145B6`) / DMG **`25:$459A`** (`0x9459A`) loops `i = 0..28` (`$B8A3..$B8BF`) over the byte-identical `29 × 16`-byte placement table at CGB **`05:$461D..$47EC`** (`0x1461D`) / DMG **`25:$45FE..$47CD`** (`0x945FE`; CGB BG palette LUT at **`05:$4821..$483D`** / `0x14821`), while `i = 29..30` render on the Cow Barn (`03:$40FB` / `23:$40EE`) and Chicken Coop (`04:$40E4` / `24:$40D7`) feed barrels:

| Idx `i` | SRAM Flag (`.sav`) | Tool ID | ROM Name (`00:$1B3F+14i`) | Shed `(X,Y)` / VRAM / Tile / Pal | Paired Counter | Vanilla Start & Exclusivity Notes |
|---|---|---|---|---|---|---|
| `0..3` | `$B8A3..$B8A6` (`0x18A3..A6`) | `$0B, $0C, $0D, $0E` | `Sickle`, `Hoe`, `Hammer`, `Ax` | `(6..9, 7)` / `$99AB..B1` / `$48..$4E` / `2` | — | **`1` (Boy & Girl)**; share `(6..9, 7)` with Super `4..7`! |
| `4..7` | `$B8A7..$B8AA` (`0x18A7..AA`) | `$13, $14, $16, $15` | `Super Sickle`, `Super Hoe`, `Super Hammer`, `Super Ax` | `(6..9, 7)` / `$99AB..B1` / `$68..$6E` / `4` | — | `0` *(note `$16`/`$15` ID order swap!)*; overwrite `0..3` on `(6..9, 7)` if both `= 1` |
| `8..10` | `$B8AB..$B8AD` (`0x18AB..AD`) | `$00, $08, $09` | `Cow Medicine`, `Milker`, `Brush` | `(1..3, 7)` / `$99A1..A5` / `$60..$64` / `5,5,2` | — | `0` (`Cow Medicine` single-use; `Milker`/`Brush` permanent) |
| `11..13` | `$B8AE..$B8B0` (`0x18AE..B0`) | `$0A, $12, $12` | `Water Can.`, `Sprinkler`, `Sprinkler` | **`(4, 7)`** / `$99A7` / `$66,$A6,$A4` / `5,4,4` | — | `$B8AE = 1` **Girl only** (`0` Boy); all 3 share `(4, 7)`! (`$B8B0` unused duplicate — leave `0`) |
| `14..16` | `$B8B1..$B8B3` (`0x18B1..B3`) | `$52, $01, $02` | `P.Medicine`, `M.Potion`, `Cow Bell` | `(1..3, 5)` / `$9921..25` / `$28..$2C` / `3,3,4` | — | `0` (`P.Medicine`/`M.Potion` single-use; `Cow Bell` permanent) |
| `17` | `$B8B4` (`0x18B4`) | `$03` | `Grass Seeds` | `(4, 5)` / `$9927` / `$2E` / `6` | **`$B8DA`** (`1..99`) | **`1` bag Girl only** (`0` Boy) |
| `18..21` | `$B8B5..$B8B8` (`0x18B5..B8`) | `$04, $05, $06, $07` | `Turnip`, `Potato`, `Tomato`, `Corn Seeds` | `(6..9, 5)` / `$992B..31` / `$08..$0E` / `3,2,3,4` | **`$B8D2..$B8D5`** (`1..99`) | Turnip & Potato **`1` bag Girl only**; Tomato & Corn `0` |
| `22..25` | `$B8B9..$B8BC` (`0x18B9..BC`) | `$4E, $4F, $50, $51` | `Eggplant`, `Peanut`, `Carrot`, `Broccoli Seeds` | `(6..9, 3)` / `$98AB..B1` / `$D8..$DE` / `5,2,3,6` | **`$B8D6..$B8D9`** (`1..99`) | `0` |
| `26..28` | `$B8BD..$B8BF` (`0x18BD..BF`) | `$53, $54, $55` | `Pick Ax`, `Umbrella`, `Fishing Rod` | `(2..4, 3)` / `$98A3..A7` / `$D2..$D6` / `2,5,2` | — | `0` (post-evaluation reward tools; render & equip cleanly on Day 1) |
| `29..30` | `$B8C0..$B8C1` (`0x18C0..C1`) | `$10, $11` | `Cow Feed`, `Chicken Feed` | Barn (`Map 0x03`) / Coop (`Map 0x04`) barrel | **`$B946, $B947`** (`1..255`) | `0` (returned to Barn/Coop barrel, never Tool Shed) |

- *(Adjacent passive upgrade **`$B8C2`** = `Saddlebag`, `0` → `1`, no Tool ID/shed slot; `$B8C3..$B8C4` are unreferenced across the ROM.)*
- **Rucksack (`$B8F8..$B8FB`) & Seed/Feed Depletion Invariants:**
  - Only **2** rucksack slots are equippable: **`$B8F8`** (Slot 0) and **`$B8F9`** (Slot 1); **`$B8FA`** (Slot 2) is the permanent bare-hands sentinel and **must stay `$FF`**, with active cursor **`$B8FB` in `{0, 1, 2}`** (`SELECT` cycler `00:$1941` increments `$B8FB` mod `3`).
  - Tool Shed pickup/swap (CGB `05:$41D3` / `0x141D3`, return helper `05:$42AF` / `0x142AF`; DMG `25:$41C3` / `0x941C3`, `25:$429F` / `0x9429F`) enforces mutual exclusion: an owned item has **either** `$B8A3+i = 1` (on table/barrel) **or** its Tool ID in `$B8F8`/`$B8F9` with `$B8A3+i = 0` — never both (otherwise picking the table copy clones the tool). For exclusive table pairs (`$B8A3+k` vs. `$B8A7+k`, `$B8AE` vs. `$B8AF`), clear the superseded base flag when granting a starter upgrade in progressive mode.
  - Every seed (`$B8B4..$B8BC` ↔ `$B8DA, $B8D2..$B8D9`) and feed (`$B8C0..$B8C1` ↔ `$B946..$B947`) requires **both** presence (Shed/Barn/Coop flag `= 1` or equipped in `$B8F8`/`$B8F9`) **and** `count >= 1`; setting a flag with `count = 0` creates an unusable bag that wraps `0 → 255` on use! When the final bag is sown (`DEC [qty] == 0`, e.g. Turnip at CGB `01:$643C` / `0x0643C`, DMG `21:$632A` / `0x8632A`), the handler automatically zeroes `$B8B5` and arms `$CB8F = $50` (`01:$644C` / `21:$633A` → `08/28:$43F1`), clearing the active rucksack slot `$B8F8 + [$B8FB]` to `$FF` and refreshing the HUD with no ghost bag.

### Starting Power Berries (`$B904`), Stamina (`$B8ED`/`$B8EE`) & Two Newly Discovered Vanilla Bugs
- **Safe Starting Power Berry Rule (`0 <= n <= 10`):** Set **`$B904 = n`** and **`$B8EE = $B8ED = 100 + 10*n`** (`100..200`, `$64..$C8`). Never exceed `n = 10`: `$B904` indexes fixed 10/11-entry tables without bounds checks at `00:$1A1C/$1A3D/$1A68 → $1ABA`, `02:$65B6` / `22:$6523` (Farmhouse planter tiles), and `0E:$5725` / `2E:$56F5`, and `n >= 6` satisfies the Grand Ranch Master ending gate at `10:$546E,$54AC` / `30:$53D9,$5417`.
- **Vanilla Bug 1 — Goddess & Underground Power Berries Omit Both `+10` Max Stamina and `CP $0A` Cap:** Standard Farm/event Power Berry grant CGB **`08:$48D0..$48E3`** (`0x208D0`) / DMG **`28:$48D4..$48E7`** (`0xA08D4`) checks `LD A,[$B904]; CP $0A; JR Z,+12; INC A; LD [$B904],A; LD A,[$B8EE]; ADD A,$0A; LD [$B8EE],A`. However, both the **Harvest Goddess** Power Berry (CGB **`0C:$7D5F..$7D66`** / `0x33D5F`; DMG **`2C:$7D09..$7D10`** / `0xB3D09`) and the **Harvest Sprite Underground** Power Berry (CGB **`0E:$4558..$455F`** / `0x38558`; DMG **`2E:$4535..$453C`** / `0xB8535`) execute only `LD A,[$B904]; INC A; LD [$B904],A` — **granting `+0` Max Stamina (`$B8EE` unchanged!) and bypassing the `10`-Berry cap** (which can push `$B904 > 10` if starting Berries or extra pool Berries are enabled!). Randomizer fix: route all Power Berry grants through a shared capped helper (`if $B904 < 10: $B904++, $B8EE = min(200, $B8EE + 10)`).
- **Vanilla Bug 2 — CGB Starter House Planter Attribute LUT Overrun (`02:$5251..$5270`, file `0x09251`):** On CGB Starter Farmhouse (`$B911 == 0`) load, `02:$5251` writes BG Palette `5` (pink flower) to `[$B904]` VRAM Bank `1` addresses from `02:$526A..$5270` (`61 62 81 82 A1 A2 C1`), which contains only **`7`** bytes instead of `10` — at `$B904 = 8..10`, the loop reads code bytes `$3E, $FF, $EA` from `02:$5271..$5273`, leaving flower #8 (`$99C2`) uncolored and writing palette `5` to off-screen `$993E, $99FF, $99EA`. *(Remember also the `1B/3B:$45D6` Restaurant/Juice Bar Stamina overflow at `$B8EE >= 130` in §9–§10, and the `02:$509F` / `22:$505B` Deluxe House auto-Super-Hammer failsafe in §8!)*

### Randomizer Hook Architecture & Free Space (`Banks 0x1D` & `0x3D`)
- **Single-Hook Table Injector (Recommended):** Replace the final `3`-byte `LD [$B8A6],A` (`EA A6 B8`) at CGB **`1D:$5205..$5207`** (`0x75205..0x75207`) and DMG **`3D:$51C0..$51C2`** (`0xF51C0..0xF51C2`) with `CALL stub` targeting the unused `$FF` tail of each bank (**`1D:$7F63..$7FFF`** = `157` free bytes at `0x77F63..0x77FFF`; **`3D:$7DE3..$7FFF`** = `541` free bytes at `0xF7DE3..0xF7FFF`). Because this runs at the exact end of Phase 2 Finalize — **after** Core Wipe, Girl bonus (`1D:$51C8` / `3D:$5183`), Pass-2 Pantry clobber (`1D:$51EE` / `3D:$51A9`), and base tools (`1D:$51FA` / `3D:$51B5`), and before `$CB50 = $0E` — a single `32`-byte loop + `(addr16, val8)` table cleanly configures any combination of Starting Gold (`0..99,999G`), Tools/Super Tools/Special Tools, pre-equipped Rucksack items, Seeds (`1..99`), Pantry foods/drinks (`0..99`, incl. Milk), Power Berries (`0..10`) + Stamina (`100..200`), Happiness (`0..255`), Material (`0..999`), Silo Fodder (`0..999`), Cow/Chicken Feed (`1..255`), Saddlebag (`$B8C2`), Cheese/Butter Makers (`$B944`/`$B943`), or House tier (`$B911`), then calls `00:$1056` (Gold), `00:$0F73` (Material), `00:$1002` (Fodder), and far-call `08/28:$456E` (active tool HUD) to sync all cached display digits. To normalize or customize Boy vs. Girl starters independently, either `NOP`/patch `1D:$51C6..$51DE` / `3D:$5181..$5199` or branch on `[$B8EC]` inside the stub. *(Starting adult livestock requires populating valid 22B Cow / 12B Chicken structs in `$B949..$B9D6` plus adult counters; avoid unless explicitly supported.)*

### Live Verification Summary (CGB & DMG)
Verified live in PyBoy across both CGB and DMG (with visual inspection of Player Name entry, Gender & Pet menus, Boy+Dog & Girl+Cat Farmhouses mid-breakfast, CGB & DMG vanilla Tool Sheds, Status Book seed counts, and custom-patched CGB & DMG 3-Berry Farmhouses, Tool Sheds, Barn Cow Feed barrel, and Coop Chicken Feed barrel): authentic empty-SRAM boots across all 4 `(Male/Female × Dog/Cat)` pairs (`0x10 → 0x11 → 0x12 → 0x24 → 0x0E → 0x02`), blank Player & Pet name rejection + `$D8` space padding + `START → End (11,5)`, `$CD7A = cursor XOR 1` (`Dog = $01`, `Cat = $00`) with zero inventory coupling, dual save signature `1D:$440C` / `3D:$43F2` (`Continue` enabled on valid save; disabled on single-byte flip in either `$BFFF` or `$A002`), full 8 KB SRAM diffs (`0` bytes CGB vs. DMG; exact `9` bytes Boy+Dog vs. Girl+Cat; exact `14` bytes `Map 0x24 → Day 1 06:00` incl. `50 → 49` morning breakfast), Girl sowing starter Turnip Seed (`$B8D2: 1→0`, `$B8F9: $04→$FF`, `$B8B5 = 0`, zero ghost bag), `HMGB-CHK-002` Flower Shop tests in CGB & DMG (skipped with shed `$B8AE=1`, equipped `$0A`, or shed Sprinkler `$B8AF=1`; granted `$B8AE: 0→1` when stripped), live reproduction of Pass-2 Pantry clobber (`1D:$51EE` / `3D:$51A9` overwriting Pass-1 `42 → 10`), and full CGB + DMG New Game run of a `151`-byte `1D:$5205` / `3D:$51C0` custom starter patch (`2,500G`, `3` Power Berries + `130/130` Stamina + 3 indoor planter flowers, `50` Happiness, `150` Material, `80` Fodder, `12` Cow Feed in Barn, `24` Chicken Feed in Coop, `Super Sickle` replacing `Sickle`, `Milker`, `Brush`, `Sprinkler` replacing Girl `Water Can`, `Fishing Rod`, `Saddlebag`, `Tomato Seeds ×5`, custom Pantry `[25,20,15,0,0,30,5,12]` eating 1 starter Milk/Juice at `06:00`, and pre-equipped `Super Hoe` [`130→127` Stamina] + `Carrot Seeds ×4` [`4→3`, `127→126` Stamina] with zero shed duplication and `0`-byte 8 KB CGB/DMG SRAM diff).
