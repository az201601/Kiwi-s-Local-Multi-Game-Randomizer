"""Game Boy memory map constants (DMG + CGB) plus address-space classifiers."""

from __future__ import annotations

# Interrupt vectors - the CPU's fixed entry points, all inside bank 0.
RST_VECTORS = (0x00, 0x08, 0x10, 0x18, 0x20, 0x28, 0x30, 0x38)
INT_VECTORS = {
    "vblank": 0x40,   # $0040
    "stat": 0x48,
    "timer": 0x50,
    "serial": 0x58,
    "joypad": 0x60,
}
ENTRY_POINT = 0x100
HEADER_END = 0x150

# Hardware registers worth naming in a listing.  Kept short on purpose:
# anything not here renders as a raw ldh address.
IO = {
    0x00: "P1/JOYP", 0x01: "SB", 0x02: "SC",
    0x04: "DIV", 0x05: "TIMA", 0x06: "TMA", 0x07: "TAC",
    0x0F: "IF",
    0x40: "LCDC", 0x41: "STAT", 0x42: "SCY", 0x43: "SCX",
    0x44: "LY", 0x45: "LYC", 0x46: "DMA", 0x47: "BGP",
    0x48: "OBP0", 0x49: "OBP1", 0x4A: "WY", 0x4B: "WX",
    0x4F: "VBK", 0x51: "HDMA1", 0x52: "HDMA2", 0x53: "HDMA3",
    0x54: "HDMA4", 0x55: "HDMA5", 0x68: "BCPS", 0x69: "BCPD",
    0x6A: "OCPS", 0x6B: "OCPD", 0x6C: "OPRI",
    0x70: "SVBK",
    0xFF: "IE",
}

# Memory regions as (start, end_inclusive, name)
REGIONS = [
    (0x0000, 0x3FFF, "ROM0"),
    (0x4000, 0x7FFF, "ROMX"),
    (0x8000, 0x9FFF, "VRAM"),
    (0xA000, 0xBFFF, "SRAM"),
    (0xC000, 0xCFFF, "WRAM0"),
    (0xD000, 0xDFFF, "WRAMX"),
    (0xE000, 0xFDFF, "ECHO"),
    (0xFE00, 0xFE9F, "OAM"),
    (0xFEA0, 0xFEFF, "UNUSABLE"),
    (0xFF00, 0xFF7F, "IO"),
    (0xFF80, 0xFFFE, "HRAM"),
    (0xFFFF, 0xFFFF, "IE"),
]

HRAM_START, HRAM_END = 0xFF80, 0xFFFE


def region_of(addr: int) -> str:
    for lo, hi, name in REGIONS:
        if lo <= addr <= hi:
            return name
    return "?"


def is_rom(addr: int) -> bool:
    return addr < 0x8000


def is_hram(addr: int) -> bool:
    return HRAM_START <= addr <= HRAM_END


def io_name(addr: int) -> str | None:
    """Name an I/O address given as a full $ffxx address."""
    if 0xFF00 <= addr <= 0xFFFF:
        return IO.get(addr - 0xFF00)
    return None
