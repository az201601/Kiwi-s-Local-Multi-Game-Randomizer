"""Cartridge image: header parsing, bank mapping and address translation.

Only what a static analysis pass needs - no emulation.  The class understands
the MBC types that Harvest Moon GBC and its siblings use (MBC1 / MBC2 / MBC3 /
MBC5) well enough to answer "which ROM offset backs address $4xxx?".
"""

from __future__ import annotations

from dataclasses import dataclass

# Nintendo logo, used as a sanity check that we were handed a GB image.
NINTENDO_LOGO = bytes.fromhex(
    "ce ed 66 66 cc 0d 00 0b 03 73 00 83 00 0c 00 0d"
    "00 08 11 1f 88 89 00 0e dc cc 6e e6 dd dd d9 99"
    "bb bb 67 63 6e 0e ec cc dd dc 99 9f bb b9 33 3e"
)

MBC_NAMES = {
    0x00: ("ROM ONLY", None),
    0x01: ("MBC1", "mbc1"),
    0x02: ("MBC1+RAM", "mbc1"),
    0x03: ("MBC1+RAM+BATTERY", "mbc1"),
    0x05: ("MBC2", "mbc2"),
    0x06: ("MBC2+BATTERY", "mbc2"),
    0x08: ("ROM+RAM", None),
    0x09: ("ROM+RAM+BATTERY", None),
    0x0B: ("MMM01", "mmm01"),
    0x0C: ("MMM01+RAM", "mmm01"),
    0x0D: ("MMM01+RAM+BATTERY", "mmm01"),
    0x0F: ("MBC3+TIMER+BATTERY", "mbc3"),
    0x10: ("MBC3+TIMER+RAM+BATTERY", "mbc3"),
    0x11: ("MBC3", "mbc3"),
    0x12: ("MBC3+RAM", "mbc3"),
    0x13: ("MBC3+RAM+BATTERY", "mbc3"),
    0x19: ("MBC5", "mbc5"),
    0x1A: ("MBC5+RAM", "mbc5"),
    0x1B: ("MBC5+RAM+BATTERY", "mbc5"),
    0x1C: ("MBC5+RUMBLE", "mbc5"),
    0x1D: ("MBC5+RUMBLE+RAM", "mbc5"),
    0x1E: ("MBC5+RUMBLE+RAM+BATTERY", "mbc5"),
    0x20: ("MBC6", "mbc6"),
    0x22: ("MBC7+SENSOR+RUMBLE+RAM+BATTERY", "mbc7"),
    0xFC: ("POCKET CAMERA", "pocket"),
    0xFD: ("BANDAI TAMA5", "tama5"),
    0xFE: ("HuC3", "huc3"),
    0xFF: ("HuC1+RAM+BATTERY", "huc1"),
}

CGB_FLAGS = {
    0x00: "no CGB support",
    0x80: "CGB-enhanced (DMG compatible)",
    0xC0: "CGB only",
}


def _fixed_str(raw: bytes) -> str:
    out = []
    for b in raw:
        if b == 0:
            break
        out.append(chr(b) if 32 <= b < 127 else "?")
    return "".join(out)


@dataclass
class Header:
    title: str
    manufacturer: str
    cgb_flag: int
    new_licensee: str
    sgb_flag: int
    cart_type: int
    cart_type_name: str
    mbc: str | None
    rom_size_code: int
    rom_banks: int
    ram_size_code: int
    ram_size: int
    destination: str
    version: int
    header_checksum: int
    header_checksum_ok: bool
    global_checksum: int
    logo_ok: bool
    entrypoint: bytes


class Rom:
    def __init__(self, data: bytes, name: str = ""):
        if len(data) < 0x8000:
            raise ValueError("image is smaller than 32 KiB - not a Game Boy ROM")
        self.data = data
        self.name = name
        self.header = self._parse_header()
        self.bank_size = 0x4000
        self.nbanks = len(data) // self.bank_size

    # -- parsing ---------------------------------------------------------
    def _parse_header(self) -> Header:
        d = self.data
        title_end = 0x0143 if d[0x0143] == 0x80 or d[0x0143] == 0xC0 else 0x0144
        title = _fixed_str(d[0x0134:title_end])
        cart_type = d[0x0147]
        name, mbc = MBC_NAMES.get(cart_type, (f"UNKNOWN(${cart_type:02x})", None))
        rom_code = d[0x0148]
        banks = 2 << rom_code if rom_code <= 8 else -1
        ram_code = d[0x0149]
        ram_size = {0: 0, 1: 2 * 1024, 2: 8 * 1024, 3: 32 * 1024,
                    4: 128 * 1024, 5: 64 * 1024}.get(ram_code, 0)

        chk = 0
        for i in range(0x0134, 0x014D):
            chk = (chk - d[i] - 1) & 0xFF
        logo_ok = d[0x0104:0x0134] == NINTENDO_LOGO
        return Header(
            title=title,
            manufacturer=_fixed_str(d[0x013F:0x0143]),
            cgb_flag=d[0x0143],
            new_licensee=d[0x0144:0x0146].hex(),
            sgb_flag=d[0x0146],
            cart_type=cart_type,
            cart_type_name=name,
            mbc=mbc,
            rom_size_code=rom_code,
            rom_banks=banks,
            ram_size_code=ram_code,
            ram_size=ram_size,
            destination="non-Japanese" if d[0x014A] else "Japanese",
            version=d[0x014C],
            header_checksum=chk,
            header_checksum_ok=(chk == d[0x014D]),
            global_checksum=d[0x014E] | (d[0x014F] << 8),
            logo_ok=logo_ok,
            entrypoint=d[0x0100:0x0104],
        )

    # -- bank / address mapping ------------------------------------------
    def bank_bytes(self, bank: int) -> bytes:
        start = bank * self.bank_size
        return self.data[start:start + self.bank_size]

    def addr_to_offset(self, addr: int, bank: int = 0) -> int | None:
        """Map a CPU address (with the bank currently in the window) to a file
        offset.  ``bank`` is the number in the $4000-$7FFF window."""
        if addr < 0x4000:
            return addr if addr < len(self.data) else None
        if addr < 0x8000:
            off = bank * self.bank_size + (addr - 0x4000)
            return off if off < len(self.data) else None
        return None

    def offset_to_addr(self, off: int) -> tuple[int, int]:
        """Inverse of :meth:`addr_to_offset`: ``(bank, cpu_addr)``."""
        if off < 0x4000:
            return 0, off
        return off // self.bank_size, 0x4000 + (off % self.bank_size)

    # -- convenience ------------------------------------------------------
    @property
    def is_cgb(self) -> bool:
        return self.header.cgb_flag in (0x80, 0xC0)

    def describe(self) -> str:
        h = self.header
        lines = [
            f"file            {self.name or '<memory>'}  ({len(self.data)} bytes, {self.nbanks} banks)",
            f"title           {h.title!r}",
            f"manufacturer    {h.manufacturer}   new licensee {h.new_licensee}",
            f"cartridge       ${h.cart_type:02x} {h.cart_type_name} (mbc: {h.mbc or 'none'})",
            f"rom             ${h.rom_size_code:02x} -> {h.rom_banks} x 16 KiB banks ({len(self.data)//1024} KiB)",
            f"ram             ${h.ram_size_code:02x} -> {h.ram_size // 1024} KiB",
            f"cgb flag        ${h.cgb_flag:02x} {CGB_FLAGS.get(h.cgb_flag, 'reserved')}",
            f"sgb flag        ${h.sgb_flag:02x}",
            f"destination     {h.destination}   version ${h.version:02x}",
            f"entry point     {h.entrypoint.hex()}  (nop / jp $0150 expected)",
            f"nintendo logo   {'ok' if h.logo_ok else 'MISMATCH'}",
            f"header checksum ${h.header_checksum:02x} {'ok' if h.header_checksum_ok else 'BAD'}",
            f"global checksum ${h.global_checksum:04x}",
        ]
        return "\n".join(lines)


def load(path: str) -> Rom:
    with open(path, "rb") as fh:
        return Rom(fh.read(), name=path)
