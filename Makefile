PY  ?= .venv/bin/python
ROM ?= romfs/harvest_moon_gbc/hm.gbc
OUT ?= out/hmgbc

.PHONY: help venv demo info disasm verify test clean

help:
	@echo "make venv     create .venv + install pyboy (emulator verification later)"
	@echo "make demo     run the whole pipeline on a synthesised cartridge (no ROM needed)"
	@echo "make info     cart header:   ROM=$(ROM)"
	@echo "make disasm   emit RGBDS project to $(OUT)"
	@echo "make verify   rebuild $(OUT) and diff against the cartridge"
	@echo "make test     unit + end-to-end tests"

venv:
	python3 -m venv .venv && .venv/bin/pip install -q --upgrade pip && \
	.venv/bin/pip install -q pyboy pytest

demo:
	$(PY) tools/demo.py

info:
	$(PY) -m tools.gb.build info $(ROM)

disasm:
	$(PY) -m tools.gb.build disasm $(ROM) --out $(OUT)

verify:
	$(PY) -m tools.gb.build verify $(ROM) --out $(OUT)

test:
	$(PY) -m pytest -q tests

clean:
	rm -rf out
