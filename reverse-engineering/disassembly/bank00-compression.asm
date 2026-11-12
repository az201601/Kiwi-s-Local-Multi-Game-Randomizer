; Confirmed fixed-bank routine: bank 00:$31A0-$323C (file ROM+0031A0)
; Evidence: entry/return hooks during calls in forced CGB and DMG PyBoy runs;
; source-bank switching and restoration observed. Outputs from 19 calls were
; independently decoded and matched runtime size, bytes-consumed, and SHA-256.
; This identifies the routine/format for those callers, not every compressed
; region or every corner case in the ROM.
;
; Entry registers (derived from runtime call sites and instruction use):
;   C  = source ROM bank to map at $4000-$7FFF
;   HL = source stream address; first two bytes are decoded output size
;   DE = destination start address
; Clobbers: AF, BC, DE, HL. Uses HRAM scratch $FFB8-$FFBE. Restores previous
; switchable bank, returns to caller. Destination starts observed so far are
; page-aligned; the pre-buffer back-reference path relies on that alignment.
;
; Flag bits are consumed LSB first. 1 = literal byte; 0 = 16-bit LZ token,
; little-endian with distance in bits 4-15 and (length - 3) in bits 0-3.
; When a reference begins before the output buffer, the routine emits zeroes
; to the buffer's page boundary then copies any remainder from the buffer base.
; A zero-distance reference is not present in the sampled streams and depends
; on pre-existing destination contents; do not assume a particular result.

B00:31A0  FA 00 40       LD A,($4000)       ; save current switchable-bank ID
B00:31A3  F5             PUSH AF
B00:31A4  79             LD A,C             ; requested source bank
B00:31A5  EA 00 21       LD ($2100),A       ; MBC3 switchable ROM bank register
B00:31A8  7A             LD A,D
B00:31A9  E0 B9          LDH ($B9),A         ; output-start high
B00:31AB  7B             LD A,E
B00:31AC  E0 B8          LDH ($B8),A         ; output-start low
B00:31AE  2A             LD A,(HL+)         ; decoded-size low byte
B00:31AF  E0 BA          LDH ($BA),A
B00:31B1  83             ADD A,E            ; output-end low = start low + size low
B00:31B2  E0 BC          LDH ($BC),A         ; output-end low
B00:31B4  2A             LD A,(HL+)         ; decoded-size high byte
B00:31B5  E0 BB          LDH ($BB),A
B00:31B7  8A             ADC A,D            ; output-end high = start high + size high + carry
B00:31B8  E0 BD          LDH ($BD),A         ; output-end high

; Start a new flag byte when the remaining-bit count C reaches zero.
B00:31BA  0E 00          LD C,$00
B00:31BC  79             LD A,C
B00:31BD  A7             AND A
B00:31BE  20 04          JR NZ,$31C4
B00:31C0  2A             LD A,(HL+)         ; next flag byte
B00:31C1  47             LD B,A
B00:31C2  0E 08          LD C,$08
B00:31C4  0D             DEC C              ; one flag bit consumed
B00:31C5  CB 38          SRL B              ; old bit 0 -> carry
B00:31C7  C5             PUSH BC            ; preserve flags/count across token work
B00:31C8  30 05          JR NC,$31CF        ; carry clear: back-reference
B00:31CA  2A             LD A,(HL+)         ; carry set: literal token
B00:31CB  12             LD (DE),A
B00:31CC  13             INC DE
B00:31CD  18 55          JR $3224

; Back-reference: token low/high -> BC, then shift right four to get distance.
B00:31CF  2A             LD A,(HL+)
B00:31D0  4F             LD C,A
B00:31D1  E6 0F          AND $0F
B00:31D3  3C             INC A
B00:31D4  3C             INC A
B00:31D5  3C             INC A
B00:31D6  E0 BE          LDH ($BE),A         ; copy length = token low nibble + 3
B00:31D8  2A             LD A,(HL+)
B00:31D9  47             LD B,A
B00:31DA  E5             PUSH HL             ; save compressed-stream cursor
B00:31DB  CB 38          SRL B
B00:31DD  CB 19          RR C
B00:31DF  CB 38          SRL B
B00:31E1  CB 19          RR C
B00:31E3  CB 38          SRL B
B00:31E5  CB 19          RR C
B00:31E7  CB 38          SRL B
B00:31E9  CB 19          RR C              ; BC = 12-bit distance
B00:31EB  7B             LD A,E
B00:31EC  91             SUB C
B00:31ED  4F             LD C,A
B00:31EE  7A             LD A,D
B00:31EF  98             SBC A,B
B00:31F0  47             LD B,A             ; BC = DE - distance (source address)

; If source address >= output-start, copy from it; otherwise zero-fill the
; prefix that lies before the page-aligned output base, then copy from base.
B00:31F1  F0 B9          LDH A,($B9)
B00:31F3  B8             CP B              ; compare output-start high vs source high
B00:31F4  38 22          JR C,$3218        ; start high < source high: source is in output
B00:31F6  20 07          JR NZ,$31FF       ; start high > source high: before buffer
B00:31F8  F0 B8          LDH A,($B8)
B00:31FA  B9             CP C              ; compare output-start low vs source low
B00:31FB  38 1B          JR C,$3218        ; source low > start low: in output
B00:31FD  28 19          JR Z,$3218        ; equal to start: copy from first output byte
B00:31FF  79             LD A,C
B00:3200  EE FF          XOR $FF
B00:3202  3C             INC A
B00:3203  47             LD B,A             ; zero-fill count to next 256-byte boundary
B00:3204  F0 BE          LDH A,($BE)
B00:3206  4F             LD C,A             ; remaining back-reference length
B00:3207  AF             XOR A
B00:3208  12             LD (DE),A          ; output a zero prefix byte
B00:3209  13             INC DE
B00:320A  0D             DEC C
B00:320B  28 16          JR Z,$3223        ; all requested output was zero-filled
B00:320D  05             DEC B
B00:320E  20 F8          JR NZ,$3208       ; stop at page boundary or length end
B00:3210  21 B8 FF       LD HL,$FFB8        ; source wraps to page-aligned output base
B00:3213  2A             LD A,(HL+)
B00:3214  66             LD H,(HL)
B00:3215  6F             LD L,A             ; HL = output-start
B00:3216  18 05          JR $321D
B00:3218  60             LD H,B
B00:3219  69             LD L,C             ; HL = valid prior output source
B00:321A  F0 BE          LDH A,($BE)
B00:321C  4F             LD C,A             ; full copy length
B00:321D  2A             LD A,(HL+)         ; copy permits overlapping references
B00:321E  12             LD (DE),A
B00:321F  13             INC DE
B00:3220  0D             DEC C
B00:3221  20 FA          JR NZ,$321D
B00:3223  E1             POP HL             ; restore compressed-stream cursor

; Continue until DE reaches the exclusive end address computed from header.
B00:3224  F0 BD          LDH A,($BD)
B00:3226  47             LD B,A
B00:3227  7A             LD A,D
B00:3228  B8             CP B
B00:3229  38 09          JR C,$3234        ; still below end high byte
B00:322B  20 0A          JR NZ,$3237       ; passed end high byte
B00:322D  F0 BC          LDH A,($BC)
B00:322F  4F             LD C,A
B00:3230  7B             LD A,E
B00:3231  B9             CP C
B00:3232  30 03          JR NC,$3237       ; DE >= output-end
B00:3234  C1             POP BC             ; discard saved flag state
B00:3235  18 85          JR $31BC          ; decode another token
B00:3237  C1             POP BC
B00:3238  F1             POP AF             ; restore prior switchable-bank ID
B00:3239  EA 00 21       LD ($2100),A
B00:323C  C9             RET
