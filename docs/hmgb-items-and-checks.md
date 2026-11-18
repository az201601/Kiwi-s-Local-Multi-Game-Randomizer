# Harvest Moon GBC — Items & Checks Registry

Target ROM: **`Harvest Moon GB (USA) (SGB Enhanced).gbc`** (SHA-1: `d407b9c20c5381f46ec460858539a5b6f559e04f`)

This document is the master catalog of **Checks** (randomized reward locations) and **Items** (shuffleable rewards) for Harvest Moon GBC in **Kiwi's Local Multi-Game Randomizer**. Every entry added here has been verified **both** in SM83 ROM disassembly and live in-game emulation (CGB and DMG modes).

For low-level disassembly listings and engine notes, see [`hmgb-rom-research.md`](hmgb-rom-research.md).

---

## Checks (Locations)

### Summary Table

| Check ID | Check Name | Region | Logic / Requirements | Missable? | SRAM Flag (`.sav` Offset) | CGB / DMG Handler | Vanilla Reward | Status |
|---|---|---|---|---|---|---|---|---|
| `HMGB-CHK-001` | Farmhouse Drawer (Grandpa's Rainy Day Money) | Farmhouse (All Tiers) | **Sphere 0** — None (available Spring 1, Year 1 at 6:00 AM; hands empty, face UP into drawer, press `A`) | **No** — Present across all 3 house tiers (`$B911 = 0, 1, 2`), zero date/season/time/weather/gender locks | `$B8FF` (`0x18FF`): `0x00` = unchecked, `0x01` (any non-zero) = checked | CGB `02:$59C8` (`0x099C8`) / DMG `22:$5935` (`0x89935`) | Grandpa's Rainy Day Money (`+1000G`, `HMGB-ITM-001`) | Verified (Code + In-Game) |

---

### Check Details

#### `HMGB-CHK-001` — Farmhouse Drawer (Grandpa's Rainy Day Money)
- **Location:** Player's Farmhouse interior, interaction tile ID `$09` (solid + interactable flags `$03`).
- **Tile Coordinates by House Upgrade Level (`$B911`, loaded by `00:$11C2` into WRAM `$D800..$D953`):**
  - **Level `0` — Starter House** (`Bank 0x11:$7251`, file `0x47251`):
    - `(x=3, y=3)` (`11:$72BD` / file `0x472BD`, WRAM `$D86C`) — bedroom chest of drawers between the Bed and the Exchange terminal.
  - **Level `1` — Expanded House** (`Bank 0x11:$70FD`, file `0x470FD`):
    - `(x=3, y=3)` (`11:$7169` / `0x47169`, WRAM `$D86C`) — bedroom chest of drawers.
    - `(x=12, y=3)` (`11:$717B` / `0x4717B`, WRAM `$D87E`) & `(x=13, y=3)` (`11:$717D` / `0x4717D`, WRAM `$D880`) — right-room kitchen cabinets next to the Calendar.
  - **Level `2` — Super Deluxe House** (`Bank 0x11:$6FA9`, file `0x46FA9`):
    - `(x=1, y=3)` (`11:$7011` / `0x47011`, WRAM `$D868`) & `(x=2, y=3)` (`11:$7013` / `0x47013`, WRAM `$D86A`) — left-room cabinets next to the Calendar.
    - `(x=13, y=3)` (`11:$7029` / `0x47029`, WRAM `$D880`) — right-room bedroom chest of drawers next to the Bed.
  - All 7 drawer tiles across the 3 tiers route to the exact same handler and share the single flag `$B8FF` (upgrading early never locks the check out; upgrading after collection never allows a duplicate payout).
- **Exact Trigger & Flag Logic:**
  - **Flag:** Dedicated 1-byte SRAM flag **`$B8FF`** (`.sav` offset `0x18FF` in the 8 KB battery SRAM bank). Cleared to `0x00` on New Game (`1D:$5223` CGB / `3D:$51DE` DMG). Zero other code sites read or write `$B8FF`.
  - **Runtime Preconditions** (`02:$5913..$595C` CGB / `22:$5880..$58C9` DMG):
    - `$CB4F == 0x00` (no map transition/fade active)
    - `$CB8B == 0x00` (not carrying a lifted item overhead; having a tool equipped in `$B8F8` does **not** block interaction)
    - `$C90E != 0x00` (player control enabled)
    - `($CB34 & 0x02) != 0` and `$CB33 == 0x09` (facing a drawer tile, i.e., standing at `y=4` facing UP into `y=3`)
    - `$CB56 == 0x00` (no dialogue active) and `($FF8B & 0x01) != 0` (fresh `A`-button press)
    - **No** restrictions on season, day, hour, weather, stamina, player gender, pet choice, tool ownership, or house tier.
  - **Unchecked Branch (`[$B8FF] == 0x00`):** Immediately sets `[$B8FF] = 0x01`, adds `+$03E8` (`+1000G`) to 24-bit LE Gold `$B8EF..$B8F1` via `CALL $0CBB`, clamps to `0..99,999G` and refreshes HUD digit tiles `$B928..$B92C` via `CALL $1056`, opens Dialogue `ID 0x09` via `CALL $3F52`, and plays SFX `$00` via `CALL $1658`.
  - **Already-Checked Branch (`[$B8FF] != 0x00`):** Leaves `$B8FF` and Gold untouched, opens Dialogue `ID 0x0A` (`"Let me see… / Everything is alright!"`), and plays SFX `$00`.
- **Associated Dialogues:**
  - `ID 0x09` (pointer `0x0343C` → `Bank 0x07:$4684`, file `0x1C684..0x1C72F`, 4 boxes, `172` bytes): `"What! There is / money in the ▼"` → `"drawer. Was this / grandpa's rainy ▼"` → `"day money? It's / mine now!! ▼"` → `"Received 1000G!!"`.
  - `ID 0x0A` (pointer `0x0343F` → `Bank 0x07:$4730`, file `0x1C730..0x1C785`, 2 boxes, `86` bytes): `"Let me see… ▼"` → `"Everything is / alright!"`.
- **Future Randomization & Multi-Game Hook Notes:**
  - Both CGB (`02:$59C8..$59ED`, file `0x099C8..0x099ED`) and DMG (`22:$5935..$595A`, file `0x89935..0x8995A`) must always be patched in tandem (38 bytes each, byte-for-byte identical structure).
  - The 12-byte grant window (`LD HL,$B8EF; LD BC,$03E8; CALL $0CBB; CALL $1056` at `0x099D3..0x099DE` CGB / `0x89940..0x8994B` DMG) can be replaced directly with a far-call (`LD HL,grant_fn; LD A,bank; CALL $217F` + `NOP`s) to award any randomized HM GBC item (tool, upgrade, seed, power berry, animal, gold bundle) or write a multi-game outgoing item flag.
  - Box 4 Line 1 of `ID 0x09` (`file 0x1C70F..0x1C71E`, `"Received 1000G!!"`) — or all 4 boxes of `ID 0x09` (`0x1C684..0x1C72F`) — can be rewritten to announce the placed item (including cross-game player/item text).
  - For live multi-game check detection, poll SRAM `$B8FF` (`.sav[0x18FF]`) transitioning from `0x00` to `0x01` while game state is active.

---

## Items (Reward Pool)

### Summary Table

| Item ID | Item Name | Category / Classification | Vanilla Source | Grant Mechanism & Addresses | Status |
|---|---|---|---|---|---|
| `HMGB-ITM-001` | Grandpa's Rainy Day Money (`1000G`) | Currency — **Useful** (early-game seed/tool/animal economy boost; configurable to Progression if gold-gated checks require early cash) | `HMGB-CHK-001` (Farmhouse Drawer) | `LD HL,$B8EF; LD BC,$03E8; CALL $0CBB; CALL $1056` (24-bit unsigned LE Gold at SRAM `$B8EF..$B8F1`, `.sav[0x18EF..0x18F1]`, clamped to `99,999G` / `$01869F`) | Verified (Code + In-Game) |

---

### Item Details

#### `HMGB-ITM-001` — Grandpa's Rainy Day Money (`1000G`)
- **Vanilla Amount:** `1,000G` (`$03E8`, immediate operand at `0x099D7..0x099D8` CGB and `0x89944..0x89945` DMG). Triples starting funds on Day 1 (`500G` → `1,500G`).
- **Gold Storage:** 24-bit unsigned little-endian integer at SRAM `$B8EF..$B8F1` (`.sav` offsets `0x18EF..0x18F1`).
  - Initialized on New Game to `$0001F4` (`500G`) at CGB `1D:$52C3` (`0x752C3`) and DMG `3D:$527E` (`0xF527E`).
  - Clamped and formatted by `00:$1056`: underflow (`$B8F1 == $FF`) clamps to `0G`; values `>= $0186A0` (`100,000`) clamp to `$01869F` (`99,999G`); writes 5 right-aligned glyph tiles to SRAM `$B928..$B92C` (`0xAF` leading blank, `0x34..0x3D` digits `'0'..'9'`).
- **Randomizer Grant Implementation Notes:**
  - Can be awarded at any check (or injected remotely from another game) by adding `1000` (or a randomized currency bundle amount) to `$B8EF..$B8F1` and calling `00:$1056` so both the status HUD and `{GOLD}` dialogue tokens (`$B2..$B6`) update immediately.
  - **Important `00:$0CBB` Quirk:** The vanilla signed-16-bit Gold adder executes `INC BC` at `00:$0CBE` before adding `B` with carry (`ADC A,(HL)`). Whenever the low byte `C == $FF` (e.g., `$00FF` = `255`), `INC BC` increments `B` by `+1`, silently granting **`+256G` extra** (verified live: `500G + $00FF` produces `1,011G` instead of `755G`; positive max without sign-flip into negative branch is `$7EFE` = `32,510G`). `$03E8` (`C = $E8`) is unaffected, and standard round gold amounts (`50G`, `100G`, `250G`, `500G`, `1000G`, `2000G`, `5000G`) are all safe — custom gold-amount randomizers must either avoid `C == $FF` (and `>= $7F00`) or use a clean unsigned 24-bit add helper.
  - **Cap Safety:** If granted when the player holds `> 98,999G`, vanilla `00:$1056` silently caps total Gold at `99,999G` (`$01869F`) with no overflow.
