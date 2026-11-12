# Initial ROM profile (offline)

**Input:** `Harvest Moon GB (U) [C][!].gbc`<br>
**SHA-256:** `54fe95af5ded33631db84b6ba4ecc4a86f9f7528c9c56ca678150a3246d49623`<br>
**Length:** 1,048,576 bytes (64 × 16 KiB banks)

The repeatable, dependency-free audit is `tools/rom_audit.py`; its complete per-bank hashes and counters are in `rom-profile.json`. These checks identify the exact input and cartridge layout. They do **not** classify the bank contents yet.

## Header facts

| Header field | Value | Evidence / interpretation |
|---|---:|---|
| Title | `HARVEST-MOON GB` | Header bytes `$0134–$0142` |
| CGB flag | `$80` | CGB-enhanced / dual-mode flag, not CGB-only |
| Cartridge type | `$10` | MBC3 + timer + RAM + battery |
| ROM size | code `$05`; 1 MiB | Matches actual length: 64 banks |
| External RAM size | code `$02`; 8 KiB | Header declaration |
| Destination code | `$01` | Non-Japanese destination flag |
| Version | `$00` | Header byte |
| Header checksum | stored `$6F`, calculated `$6F` | Valid |
| Global checksum | stored `$834C`, calculated `$834C` | Valid |
| Nintendo logo | exact match | Header bytes `$0104–$0133` |

The CPU-visible address window `$4000–$7FFF` is banked on this cartridge; any disassembly in that window must therefore carry a ROM-bank identity. A 16 KiB physical bank is not itself a single CPU address range: bank 0 is visible at `$0000–$3FFF`, while switchable banks are mapped into `$4000–$7FFF`.

## Bootstrap observations

The following is a first-pass decode of fixed-bank bytes, using the Game Boy instruction encoding and direct ROM evidence. Addresses refer to CPU addresses in fixed bank 0 unless marked as file offsets.

- At `$0100`: `NOP; JP $0150`. The Nintendo header occupies `$0104–$014F`; execution continues at `$0150`.
- Interrupt vectors found in the fixed bank:
  - `$0040` VBlank: `CALL $04C7; RETI`.
  - `$0048` LCD STAT: `JP $3311`.
  - `$0050` timer: `JP $33F9`.
  - `$0058` serial: `JP $3408`.
  - `$0060` joypad: `RETI`.
  These are entry targets, not yet analyzed handlers.
- Reset code begins `$0150`: two NOPs, `DI`, then writes the post-boot value of A to `$DFF7`; it zeroes A, writes zero to the MBC3 control address `$4000`, selects ROM bank 1 through `$2000`, and sets SP to `$DFEF`.
- The helper at `$2282` is a byte-clear loop: it writes zero at `[HL]`, increments HL, decrements BC, and repeats until BC is zero. Reset calls it with `HL=$FF80, BC=$007F` (clears `$FF80–$FFFE`) and with `HL=$DD00, BC=$00FF` (clears `$DD00–$DDFE`). This identifies two reset-time RAM ranges, not the meanings of their fields.
- The helper at `$2242` copies ten bytes from `$2250` into HRAM `$FF80–$FF89`. Those bytes decode as a short routine that writes `$C0` to `$FF46` (OAM DMA from `$C000`) and waits before returning. Its exact callers and runtime role remain to be traced.

## Runtime cross-check

PyBoy runs with forced CGB and DMG modes confirm the reset entry and the startup branch at `$01E6–$0201`; see `runtime-observations.md` for registers and the resulting values at `$C0BB/$C0BC`.

## Still unknown

This is only the initial foothold. We have not yet mapped the full control flow, verified all bank-switch paths, classified ROM data, decoded any asset/table format, identified game-state fields, or established the behavior of any complete game mechanic. No gameplay or content claims should be treated as complete based on this profile.
