# Memory and state map (initial)

Only fields with direct code or runtime evidence are listed. Address names are provisional unless noted as hardware registers.

## Cartridge state

| Address / range | Current evidence | Status |
|---|---|---|
| `$A000–$BFFF` | 8 KiB cartridge RAM declared by header; cartridge type also includes battery-backed RAM and RTC | Area declared; layout/content unknown |
| MBC3 `$0000–$1FFF` writes | Reset writes `$0A` to `$0000` after initial setup, enabling cartridge RAM/RTC on MBC3 | Hardware behavior from mapper register use; save access not mapped |
| MBC3 `$2000–$3FFF` writes | Reset selects bank 1; far-call trampoline writes a target bank at `$2100` and restores old bank | Runtime bank-dispatch path confirmed |
| MBC3 `$4000–$5FFF` writes | `$00` is written to `$4000` during startup | RAM/RTC select value; later usage unknown |
| MBC3 `$6000–$7FFF` writes | `$01` is written to `$6000` during startup | RTC latch/control behavior not yet determined |

## Working RAM / HRAM

| Address / range | Current evidence | Status |
|---|---|---|
| `$C0A7` | Startup writes `$20`; later `$0061` reads it as A for an RST `$08` inline-table dispatch | Observed dispatcher index; other meanings/usages unknown |
| `$C0BB` | Startup set to `$01` when saved post-boot A is `$11`, else `$00` | Mode-dependent; gameplay meaning unknown |
| `$C0BC` | Startup set to `$00` for saved A `$11`, else `$2A` | Mode-dependent; gameplay meaning unknown |
| `$DFF7` | Reset stores the post-boot A value here; startup later compares it with `$11` | Boot-mode signature storage, runtime-confirmed on PyBoy CGB/DMG paths |
| `$DFEF` | Reset initializes SP to `$DFEF`; a call at the first zero-fill helper observes SP=`$DFED` | Stack initialized here; exact stack bounds unknown |
| `$FF80–$FFFE` | Reset first zero-fills this 127-byte range; `$FF80–$FF89` is then populated with the OAM-DMA wait routine | First ten bytes confirmed; remaining HRAM uses unknown |
| `$FFB8–$FFB9` | Decompressor `$31A0` saves output-start low/high here | Scratch state valid while decompressor runs |
| `$FFBA–$FFBB` | `$31A0` reads decoded-size low/high bytes into these cells | Scratch state valid while decompressor runs |
| `$FFBC–$FFBD` | `$31A0` calculates exclusive output-end low/high here | Scratch state valid while decompressor runs |
| `$FFBE` | `$31A0` stores LZ match length (low token nibble plus 3) here | Scratch state valid while a back-reference is copied |
| `$DD00–$DDFE` | Reset first zero-fills 255 bytes here | Contents/field layout unknown |
| `$C000–$DCFE` | Reset later zero-fills `$1CFF` bytes from `$C000` | Contents/field layout unknown; bank selection and possible I/O side effects need checking |
| `$8000–$82FF` | Opening paths decompress 768-byte streams from CGB bank `$1D:$62BC` or DMG bank `$3D:$6157` here | VRAM tile-data range; contents/visual meaning not yet classified |
| `$8800–$8FFF` | Opening paths decompress 2,048-byte streams from CGB `$1D:$5FE3` or DMG `$3D:$5E7E` here | VRAM tile-data range; grayscale patterns not yet rendered |
| `$9000–$97FF` | CGB bank `$1D` uses `$12:$7A58`; DMG bank `$3D` uses `$32:$7A68` (both `$0800` bytes) | VRAM tile-data range; decoded patterns include a Natsume/copyright wordmark |
| `$9800–$9BFF` | Opening paths load `$0400`-byte data here, including CGB `$12:$7ED9` and DMG `$32:$7EE9` | VRAM tile-map address range; exact map/attribute semantics not yet decoded |
| `$9C00–$9FFF` | Opening paths load a second `$0400`-byte data block here; CGB reuses `$12:$7ED9`, DMG reuses `$32:$7EE9` | Second VRAM tile-map address range; exact map semantics not yet decoded |

The zero-fill helper at `$2282` is generic and is called later with other HL/BC values, including destinations outside ordinary work RAM. These reset clears do not define a complete RAM map.

## Hardware I/O touched in the analyzed startup helpers

| Register | Address | Current evidence |
|---|---:|---|
| BGP | `$FF47` | Startup writes zero |
| OBP0 / OBP1 | `$FF48` / `$FF49` | Startup writes zero |
| LCDC | `$FF40` | `$225A` waits for line `$91`, then clears bit 7 |
| LY | `$FF44` | `$225A` polls until `$91` (145) |
| IE | `$FFFF` | `$225A` saves it, clears bit 0 while waiting, restores it |
| OAM DMA | `$FF46` | HRAM routine writes `$C0`, initiating DMA from `$C000` |
| `$FF8D`, `$FF42`, `$FF43`, `$FF97`, `$FF9A` | listed addresses | Written during reset; semantics not yet established |

Do not assign names to unknown bytes/registers based solely on their addresses. Keep read/write cross-references and runtime values alongside this map as analysis expands.
