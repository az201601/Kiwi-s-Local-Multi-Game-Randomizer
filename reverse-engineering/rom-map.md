# ROM bank inventory

> Initial bank-level ledger generated from `rom-profile.json`. Status describes current analysis coverage, not inferred contents. This is **not yet** the required byte-range map; each bank must be subdivided as code, data, assets, padding, or unresolved bytes with evidence.

**Structural observation:** all 63 switchable physical banks (1–63) begin with a byte equal to that bank's index; the values are in `rom-profile.json`. Fixed bank 0 begins `C3 60 21`. Code at `$217F` reads from CPU `$4000` before switching banks, which is consistent with using that prefix byte to recover the active bank for restoration. The far-bank dispatch mechanism and runtime evidence are documented in `disassembly/bank00-dispatchers.md` and `runtime-observations.md`.

| Physical bank | File offsets | CPU window | SHA-256 (prefix) | Classification status |
|---:|---|---|---|---|
| 00 | `0x000000–0X003FFF` | `0x0000-0x3FFF` | `71ef47d8457b` | Partial: runtime-verified bootstrap/far calls and `$31A0` compressed-stream routine; most ranges remain unclassified |
| 01 | `0x004000–0X007FFF` | `0x4000-0x7FFF (banked)` | `c67b5c1a1e7e` | Unclassified |
| 02 | `0x008000–0X00BFFF` | `0x4000-0x7FFF (banked)` | `d449b74efd8f` | Unclassified |
| 03 | `0x00C000–0X00FFFF` | `0x4000-0x7FFF (banked)` | `ad7abd144f82` | Unclassified |
| 04 | `0x010000–0X013FFF` | `0x4000-0x7FFF (banked)` | `125d13681da0` | Unclassified |
| 05 | `0x014000–0X017FFF` | `0x4000-0x7FFF (banked)` | `fe3af424f2bf` | Unclassified |
| 06 | `0x018000–0X01BFFF` | `0x4000-0x7FFF (banked)` | `47029a58f946` | Unclassified |
| 07 | `0x01C000–0X01FFFF` | `0x4000-0x7FFF (banked)` | `c2acd184523d` | Unclassified |
| 08 | `0x020000–0X023FFF` | `0x4000-0x7FFF (banked)` | `ff6cd5d55092` | Unclassified |
| 09 | `0x024000–0X027FFF` | `0x4000-0x7FFF (banked)` | `66ce35b8d6a9` | Unclassified |
| 10 | `0x028000–0X02BFFF` | `0x4000-0x7FFF (banked)` | `72414c144fcd` | Unclassified |
| 11 | `0x02C000–0X02FFFF` | `0x4000-0x7FFF (banked)` | `eac172832d87` | Unclassified |
| 12 | `0x030000–0X033FFF` | `0x4000-0x7FFF (banked)` | `fc96ae6f9ac7` | Unclassified |
| 13 | `0x034000–0X037FFF` | `0x4000-0x7FFF (banked)` | `ec2312b069ed` | Unclassified |
| 14 | `0x038000–0X03BFFF` | `0x4000-0x7FFF (banked)` | `68898ea25658` | Unclassified |
| 15 | `0x03C000–0X03FFFF` | `0x4000-0x7FFF (banked)` | `e9a8f490027d` | Unclassified |
| 16 | `0x040000–0X043FFF` | `0x4000-0x7FFF (banked)` | `7e74b4fbe275` | Unclassified |
| 17 | `0x044000–0X047FFF` | `0x4000-0x7FFF (banked)` | `865b1b7e842e` | Unclassified |
| 18 | `0x048000–0X04BFFF` | `0x4000-0x7FFF (banked)` | `8a045eef3302` | Partial: CGB opening streams cover `$7A58–$7FBC`; remainder unclassified |
| 19 | `0x04C000–0X04FFFF` | `0x4000-0x7FFF (banked)` | `95665eb20c3f` | Unclassified |
| 20 | `0x050000–0X053FFF` | `0x4000-0x7FFF (banked)` | `67e94536eb97` | Unclassified |
| 21 | `0x054000–0X057FFF` | `0x4000-0x7FFF (banked)` | `9d9b2096cb29` | Unclassified |
| 22 | `0x058000–0X05BFFF` | `0x4000-0x7FFF (banked)` | `ba23f728c6cf` | Unclassified |
| 23 | `0x05C000–0X05FFFF` | `0x4000-0x7FFF (banked)` | `f370b8a94ec7` | Unclassified |
| 24 | `0x060000–0X063FFF` | `0x4000-0x7FFF (banked)` | `4a003be7268f` | Unclassified |
| 25 | `0x064000–0X067FFF` | `0x4000-0x7FFF (banked)` | `9c9f7a1a2a56` | Unclassified |
| 26 | `0x068000–0X06BFFF` | `0x4000-0x7FFF (banked)` | `089f7ef3ec08` | Unclassified |
| 27 | `0x06C000–0X06FFFF` | `0x4000-0x7FFF (banked)` | `18239415d8f8` | Unclassified |
| 28 | `0x070000–0X073FFF` | `0x4000-0x7FFF (banked)` | `0ce2d09bce9f` | Unclassified |
| 29 | `0x074000–0X077FFF` | `0x4000-0x7FFF (banked)` | `6b67ef894d9c` | Partial: bank `$1D` compressed streams form contiguous `$5A94–$6679` run plus code; remainder unclassified |
| 30 | `0x078000–0X07BFFF` | `0x4000-0x7FFF (banked)` | `1dad31da2982` | Unclassified |
| 31 | `0x07C000–0X07FFFF` | `0x4000-0x7FFF (banked)` | `e689eb89f8da` | Unclassified |
| 32 | `0x080000–0X083FFF` | `0x4000-0x7FFF (banked)` | `c6b972578081` | Partial: CGB streams at `$49B7–$4A48` and `$4C14–$4C93`, with callers near `$6D29–$6D55`, are runtime-confirmed; remainder unclassified |
| 33 | `0x084000–0X087FFF` | `0x4000-0x7FFF (banked)` | `47b6656c126a` | Unclassified |
| 34 | `0x088000–0X08BFFF` | `0x4000-0x7FFF (banked)` | `1098bd34f5c2` | Unclassified |
| 35 | `0x08C000–0X08FFFF` | `0x4000-0x7FFF (banked)` | `4d0fe078ac25` | Unclassified |
| 36 | `0x090000–0X093FFF` | `0x4000-0x7FFF (banked)` | `6919fb61acd1` | Unclassified |
| 37 | `0x094000–0X097FFF` | `0x4000-0x7FFF (banked)` | `2c7e7e64b2b0` | Unclassified |
| 38 | `0x098000–0X09BFFF` | `0x4000-0x7FFF (banked)` | `30a427116df2` | Unclassified |
| 39 | `0x09C000–0X09FFFF` | `0x4000-0x7FFF (banked)` | `632a74761b12` | Unclassified |
| 40 | `0x0A0000–0X0A3FFF` | `0x4000-0x7FFF (banked)` | `de3b76a9b741` | Unclassified |
| 41 | `0x0A4000–0X0A7FFF` | `0x4000-0x7FFF (banked)` | `320bc878d705` | Unclassified |
| 42 | `0x0A8000–0X0ABFFF` | `0x4000-0x7FFF (banked)` | `3666fd987887` | Unclassified |
| 43 | `0x0AC000–0X0AFFFF` | `0x4000-0x7FFF (banked)` | `1efe48cd9aec` | Unclassified |
| 44 | `0x0B0000–0X0B3FFF` | `0x4000-0x7FFF (banked)` | `ad45baf8a1d2` | Unclassified |
| 45 | `0x0B4000–0X0B7FFF` | `0x4000-0x7FFF (banked)` | `92b777cf7a0d` | Unclassified |
| 46 | `0x0B8000–0X0BBFFF` | `0x4000-0x7FFF (banked)` | `3098b1b32d1f` | Unclassified |
| 47 | `0x0BC000–0X0BFFFF` | `0x4000-0x7FFF (banked)` | `cd396292df69` | Unclassified |
| 48 | `0x0C0000–0X0C3FFF` | `0x4000-0x7FFF (banked)` | `5c769c84d0de` | Unclassified |
| 49 | `0x0C4000–0X0C7FFF` | `0x4000-0x7FFF (banked)` | `3445b503e45b` | Unclassified |
| 50 | `0x0C8000–0X0CBFFF` | `0x4000-0x7FFF (banked)` | `84feca6baf89` | Partial: forced-DMG streams form contiguous `$7A68–$7FD2` run; remainder unclassified |
| 51 | `0x0CC000–0X0CFFFF` | `0x4000-0x7FFF (banked)` | `5ae297c80677` | Unclassified |
| 52 | `0x0D0000–0X0D3FFF` | `0x4000-0x7FFF (banked)` | `9470ef742355` | Unclassified |
| 53 | `0x0D4000–0X0D7FFF` | `0x4000-0x7FFF (banked)` | `5ea0d0ab7d13` | Unclassified |
| 54 | `0x0D8000–0X0DBFFF` | `0x4000-0x7FFF (banked)` | `eff986756fc9` | Unclassified |
| 55 | `0x0DC000–0X0DFFFF` | `0x4000-0x7FFF (banked)` | `b14ed9afa3d2` | Unclassified |
| 56 | `0x0E0000–0X0E3FFF` | `0x4000-0x7FFF (banked)` | `c5489a1a7e3c` | Unclassified |
| 57 | `0x0E4000–0X0E7FFF` | `0x4000-0x7FFF (banked)` | `44eb739cd743` | Unclassified |
| 58 | `0x0E8000–0X0EBFFF` | `0x4000-0x7FFF (banked)` | `489b420dff7f` | Unclassified |
| 59 | `0x0EC000–0X0EFFFF` | `0x4000-0x7FFF (banked)` | `7aab60783a94` | Unclassified |
| 60 | `0x0F0000–0X0F3FFF` | `0x4000-0x7FFF (banked)` | `712c3f7e1b52` | Unclassified |
| 61 | `0x0F4000–0X0F7FFF` | `0x4000-0x7FFF (banked)` | `0cc149f5e29d` | Partial: forced-DMG compressed streams form contiguous `$58D0–$6509` run plus code; remainder unclassified |
| 62 | `0x0F8000–0X0FBFFF` | `0x4000-0x7FFF (banked)` | `01f9d9fcd8ce` | Unclassified |
| 63 | `0x0FC000–0X0FFFFF` | `0x4000-0x7FFF (banked)` | `ba987098a9e8` | Unclassified |

## Bank 00: first byte-range ledger

This is a partial classification only. Offsets are file offsets and, for bank 00, also CPU addresses. “Confirmed” means supported by bytes and/or runtime hooks; table bounds and execution paths still carry their stated caveats.

| ROM/CPU range | First-pass classification | Evidence / remaining uncertainty |
|---|---|---|
| `$0000–$0002` | RST `$00` vector: `JP $2160` | Direct bytes; handler uses an inline 16-bit table |
| `$0003–$0007` | `$FF` fill | Exact bytes; whether intentional padding is not yet relevant |
| `$0008–$000A` | RST `$08` vector: `JP $216C` | Direct bytes; handler uses an inline 24-bit far table |
| `$000B` | `RET` instruction candidate | Byte `$C9`; callers/use not found |
| `$000C–$000F` | `$FF` fill | Exact bytes |
| `$0010`, `$0018`, `$0020`, `$0028`, `$0030` | `RET` bytes at RST vectors | Direct decode; each vector is otherwise largely `$FF` |
| `$0011–$0017`, `$0019–$001F`, `$0021–$0027`, `$0029–$002F`, `$0031–$0037` | `$FF` fill | Exact bytes |
| `$0038–$003E` | RST `$38` helper: add A to HL, return | Direct decode; call sites not yet enumerated |
| `$003F` | `$FF` fill | Exact byte |
| `$0040–$0043` | VBlank vector: `CALL $04C7; RETI` | Direct decode; handler at `$04C7` not analyzed yet |
| `$0044–$0047` | `$FF` fill | Exact bytes |
| `$0048–$004A` | LCD STAT vector: `JP $3311` | Direct decode; handler not analyzed |
| `$0050–$0052` | Timer vector: `JP $33F9` | Direct decode; handler not analyzed |
| `$0058–$005A` | Serial vector: `JP $3408` | Direct decode; handler not analyzed |
| `$004B–$004F`, `$0053–$0057`, `$005B–$005F` | `$FF` fill | Exact bytes |
| `$0060` | Joypad vector: `RETI` | Direct decode |
| `$0061–$0065` | Far-dispatch thunk: load `$C0A7`, `OR A`, `RST $08` | Dynamic path observed in PyBoy |
| `$0066–$00E3` | 42 consecutive 3-byte far-pointer entries (candidate table bounds) | RST `$08` format and indices observed; upper bound inferred from transition to `$FF` fill |
| `$00E4–$00FF` | `$FF` fill | Exact bytes |
| `$0100–$0103` | Cartridge entry: `NOP; JP $0150` | Direct decode |
| `$0104–$0133` | Nintendo header logo | Exact expected logo match |
| `$0134–$014F` | Cartridge header fields | Parsed and checksummed in `rom-profile.json` |
| `$0150–$017A` | Reset code, initial setup | Runtime-confirmed entry/first helpers; not every instruction independently checked |
| `$01E2–$0206` | Mode-signature branch and `$C0A7` initialization | CGB/DMG paths runtime-confirmed; meanings of `$C0BB/$C0BC` remain unknown |
| `$0207–$215F` | Candidate code/data mix | Candidate sweep only; must be split into code, tables, and assets by further tracing |
| `$2160–$217E` | RST `$00/$08` pointer dispatchers | Direct decode; RST `$08` path runtime-confirmed |
| `$217F–$2194` | Bank-call trampoline and `JP (HL)` | Direct decode and runtime-observed far calls |
| `$2242–$224F` | HRAM DMA-payload copy helper | Direct decode and runtime-confirmed copied bytes |
| `$2250–$2259` | 10-byte HRAM DMA routine payload | Executed from HRAM after copy; confirmed at `$FF80–$FF89` |
| `$225A–$2272` | LCD-safe-disable routine | Direct decode and LCD/LY runtime observation |
| `$2282–$2289` | Generic zero-fill helper | Direct decode and multiple runtime call arguments |
| `$2195–$2241`, `$228A–$319F` | Unclassified | No byte-range classification yet |
| `$31A0–$323C` | Length-prefixed LZSS-style decompressor | Exact bank-00 bytes decoded in `disassembly/bank00-compression.asm`; 19 calls in forced-CGB/DMG no-input traces match independent output lengths, consumed-input counts, and hashes |
| `$323D–$3248` | `$C000–$C09F` zero-fill helper | Direct instruction decode; call sites and runtime use not yet established |
| `$3249–$3FFF` | Unclassified | No byte-range classification yet |

This ledger intentionally does not mark the remainder of bank 00 as data or code yet. The 360-instruction candidate sweep is useful for finding leads but is not a substitute for byte-range classification.
