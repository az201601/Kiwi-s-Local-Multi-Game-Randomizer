"""End-to-end: synthesise a cartridge, disassemble it, rebuild it byte-exact."""

import pytest

from tools.gb import analyze, emit, rom as rommod
from tests import synth


@pytest.fixture(scope="module")
def source_rom(tmp_path_factory):
    data = synth.make_rom(nbanks=4)
    path = tmp_path_factory.mktemp("rom") / "synth.gbc"
    path.write_bytes(data)
    return path, data


def test_header_parses(source_rom):
    path, data = source_rom
    r = rommod.load(str(path))
    h = r.header
    assert h.title == "SYNTH TEST"
    assert h.logo_ok and h.header_checksum_ok
    assert h.mbc == "mbc5"
    assert h.cgb_flag == 0x80
    assert r.nbanks == 4
    assert h.entrypoint == bytes([0x00, 0xC3, 0x50, 0x01])


def test_entry_stream_is_code(source_rom):
    path, data = source_rom
    r = rommod.load(str(path))
    results = analyze.analyse(r)
    bank0 = results[0]
    assert 0x100 in {0x0000 + off for off in bank0.code}
    texts = [i.text for i in bank0.code.values()]
    assert "jp $0150" in texts
    # the vector table must be reached
    assert 0x00 in {off for off in bank0.code}


def test_data_is_not_mistaken_for_code(source_rom):
    path, data = source_rom
    r = rommod.load(str(path))
    results = analyze.analyse(r)
    # $0104 holds the Nintendo logo; nothing must claim it as an instruction
    assert 0x104 not in results[0].code


def test_roundtrip_is_byte_identical(source_rom, tmp_path):
    path, data = source_rom
    r = rommod.load(str(path))
    results = analyze.analyse(r)
    out = tmp_path / "proj"
    emit.emit_all(r, results, out)

    from tools.gb import rgbds
    includes = {str(p.relative_to(out)): p.read_bytes()
                for p in out.rglob("*.asm") if p.name != "main.asm"}
    obj = rgbds.assemble((out / "main.asm").read_text(), files=includes)
    built = rgbds.link([obj], link_script=(out / "link.script").read_text())
    built = built.ljust(len(data), b"\x00")
    assert built[:len(data)] == data


def test_emitter_covers_every_byte_exactly_once(source_rom, tmp_path):
    path, data = source_rom
    r = rommod.load(str(path))
    results = analyze.analyse(r)
    out = tmp_path / "proj"
    emit.emit_all(r, results, out)
    for bank in results:
        text = (out / "banks" / f"bank{bank:02d}.asm").read_text()
        emitted = 0
        for line in text.splitlines():
            line = line.strip()
            if line.startswith("db "):
                emitted += line.split(";")[0].count("$")
        code_bytes = results[bank].code_bytes()
        assert emitted + code_bytes == 0x4000, f"bank {bank} byte accounting off"
