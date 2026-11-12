; Verified fixed-bank bootstrap excerpt (not a whole-bank disassembly).
; Address mapping: bank 00 CPU $0000-$3FFF. Bytes were checked against the ROM,
; and startup calls/data were cross-checked with PyBoy; see runtime-observations.md.

SECTION "Reset entry excerpt", ROM0[$0150]
Reset_Entry_0150:
    nop                         ; $0150  00
    nop                         ; $0151  00
    di                          ; $0152  F3
    or  a                        ; $0153  B7
    ld  [$DFF7], a               ; $0154  EA F7 DF ; save post-boot A signature
    xor a                        ; $0157  AF
    ld  [$4000], a               ; $0158  EA 00 40 ; MBC3 select register <- 0
    ld  a, $01                   ; $015B  3E 01
    ld  [$2000], a               ; $015D  EA 00 20 ; MBC3 ROM bank low <- 1
    ld  sp, $DFEF                ; $0160  31 EF DF
    ld  hl, $FF80                ; $0163  21 80 FF
    ld  bc, $007F                ; $0166  01 7F 00
    call ZeroFill_HL_BC          ; $0169  CD 82 22 ; first call: HRAM $FF80-$FFFE
    ld  hl, $DD00                ; $016C  21 00 DD
    ld  bc, $00FF                ; $016F  01 FF 00
    call ZeroFill_HL_BC          ; $0172  CD 82 22 ; first call: $DD00-$DDFE
    call CopyOamDmaToHram        ; $0175  CD 42 22

    ; PyBoy hook at $0178 confirms HRAM now contains the 10-byte routine below.
    xor a                        ; $0178  AF
    ldh [$47], a                 ; $0179  E0 47 ; BGP ($FF47) background palette register

SECTION "Copy OAM DMA helper to HRAM", ROM0[$2242]
CopyOamDmaToHram:
    ld  c, $80                   ; $2242  0E 80
    ld  b, $0A                   ; $2244  06 0A ; 10 bytes
    ld  hl, OamDmaHramPayload    ; $2246  21 50 22
.copy:
    ld  a, [hli]                 ; $2249  2A
    ldh [c], a                   ; $224A  E2 ; writes to $FF00+C, starting at $FF80
    inc c                        ; $224B  0C
    dec b                        ; $224C  05
    jr  nz, .copy                ; $224D  20 FA
    ret                          ; $224F  C9

; This is data at $2250, not executed at that ROM address in this routine.
; CopyOamDmaToHram moves it to $FF80, where it is executed as code.
OamDmaHramPayload:
    db  $3E, $C0, $E0, $46, $3E, $28, $3D, $20, $FD, $C9
; Decodes at HRAM $FF80:
;   $FF80  LD A,$C0       ; source page for OAM DMA
;   $FF82  LDH [$FF46],A  ; start DMA from $C000
;   $FF84  LD A,$28
;   $FF86  DEC A
;   $FF87  JR NZ,$FF86    ; delay loop
;   $FF89  RET

SECTION "Wait for VBlank and disable LCD", ROM0[$225A]
DisableLcdInVBlank:
    ld  hl, $FF40                ; $225A  21 40 FF ; LCDC
    bit 7, [hl]                  ; $225D  CB 7E
    ret z                        ; $225F  C8 ; already disabled
    ldh a, [$FF]                 ; $2260  F0 FF ; save IE ($FFFF)
    push af                      ; $2262  F5
    res 0, a                     ; $2263  CB 87 ; suppress VBlank interrupt while waiting
    ldh [$FF], a                 ; $2265  E0 FF
.wait_for_line_91:
    ldh a, [$44]                 ; $2267  F0 44 ; LY ($FF44)
    cp  $91                      ; $2269  FE 91 ; line 145
    jr  nz, .wait_for_line_91    ; $226B  20 FA
    res 7, [hl]                  ; $226D  CB BE ; disable LCD only during VBlank
    pop af                       ; $226F  F1
    ldh [$FF], a                 ; $2270  E0 FF ; restore IE
    ret                          ; $2272  C9

; PyBoy observation: entry at frame 65 saw LY=$1C/LCDC=$91; at $226D,
; LY=$91/LCDC=$91; at $2272, LCDC=$11. This confirms the safe-disable path.

SECTION "Zero-fill helper", ROM0[$2282]
ZeroFill_HL_BC:
    xor a                        ; $2282  AF
.loop:
    ld  [hli], a                 ; $2283  22
    dec bc                       ; $2284  0B
    ld  a, c                     ; $2285  79
    or  b                        ; $2286  B0
    jr  nz, .loop                ; $2287  20 F9
    ret                          ; $2289  C9

; Runtime-confirmed startup call arguments:
;   $0169: HL=$FF80, BC=$007F -> writes zero to $FF80-$FFFE.
;   $0172: HL=$DD00, BC=$00FF -> writes zero to $DD00-$DDFE.
; The helper is also reused later with other destinations/counts.

SECTION "Post-boot signature branch", ROM0[$01E2]
PostBootSignatureBranch:
    xor a                        ; $01E2  AF
    ld  [$4000], a               ; $01E3  EA 00 40
    ld  a, [$DFF7]               ; $01E6  FA F7 DF
    cp  $11                      ; $01E9  FE 11
    jr  nz, .other_signature     ; $01EB  20 0C

    ld  a, $01                   ; $01ED  3E 01
    ld  [$C0BB], a               ; $01EF  EA BB C0
    ld  a, $00                   ; $01F2  3E 00
    ld  [$C0BC], a               ; $01F4  EA BC C0
    jr  .done                    ; $01F7  18 09
.other_signature:
    xor a                        ; $01F9  AF
    ld  [$C0BB], a               ; $01FA  EA BB C0
    ld  a, $2A                   ; $01FD  3E 2A
    ld  [$C0BC], a               ; $01FF  EA BC C0
.done:
; $0202 continues in the reset flow. PyBoy forced-mode runs show:
;   CGB: initial A=$11 -> $C0BB=$01, $C0BC=$00.
;   DMG: initial A=$01 -> $C0BB=$00, $C0BC=$2A.
; The functional meanings of these two RAM bytes remain unknown.
