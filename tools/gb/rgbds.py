"""Thin wrapper around the RGBDS assembler/linker.

Prefers native ``rgbasm`` if present; otherwise falls back to the WASM build
shipped by the ``romdev-toolchain-rgbds`` npm package (used in the sandbox,
where downloading native binaries is blocked).  Both paths present the same
``assemble()`` / ``link()`` / ``fix()`` API, so the build script does not care
which one it got.
"""

from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
from pathlib import Path

WASM_DIR = Path("/usr/local/lib/node_modules/romdev-toolchain-rgbds/wasm")


def backend() -> str:
    if shutil.which("rgbasm"):
        return "native"
    if (WASM_DIR / "rgbasm.js").exists() and shutil.which("node"):
        return "wasm"
    if WASM_DIR.exists() and shutil.which("node"):
        return "wasm"
    return "missing"


_NODE_RUNNER = r"""
import { pathToFileURL } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
const mkdirp = (p) => { try { fs.mkdirSync(p, { recursive: true }); } catch {} };
const [tool, base, manifestPath, inDir, outDir] = process.argv.slice(2);
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const create = (await import(pathToFileURL(base + '/' + tool + '.js').href)).default;
const mod = await create({
  noInitialRun: true,
  locateFile: (p) => base + '/' + p.replace('.mod.wasm', '.wasm'),
  print: (s) => process.stdout.write(s + '\n'),
  printErr: (s) => process.stderr.write(s + '\n'),
});
mkdirp(inDir);
const stage = path.join(inDir, 'stage');
for (const thePath of manifest.inputs) {
  const dest = path.join(stage, thePath.replace(/^\//, ''));
  const data = new Uint8Array(fs.readFileSync(dest));
  const parts = thePath.split('/').filter(Boolean);
  parts.pop();
  let cur = '';
  for (const seg of parts) {
    cur += '/' + seg;
    try { mod.FS.mkdir(cur); } catch {}
  }
  mod.FS.writeFile(thePath, data);
}
let code = 0;
try { code = mod.callMain(manifest.args) ?? 0; } catch (e) { if (e && e.status !== undefined) code = e.status; else throw e; }
mkdirp(outDir);
for (const thePath of manifest.outputs) {
  let data = null;
  try { data = Buffer.from(mod.FS.readFile(thePath)); } catch { data = null; }
  if (data !== null) {
    const dest = path.join(outDir, thePath.replace(/^\//, ''));
    mkdirp(path.dirname(dest));
    fs.writeFileSync(dest, data);
  }
  manifest.results[thePath] = data !== null;
}
fs.writeFileSync(path.join(outDir, 'result.json'), JSON.stringify({ code, results: manifest.results }));
"""


def _run_wasm(tool: str, args: list[str], inputs: dict[str, bytes],
              outputs: list[str]) -> dict[str, bytes | None]:
    """Run an RGBDS tool inside its WASM sandbox.

    Input/output files are staged on the real filesystem and copied into the
    tool's virtual filesystem by the node runner, so large sources never have
    to travel through argv.
    """
    with tempfile.TemporaryDirectory() as td:
        root = Path(td)
        runner = root / "run.mjs"
        runner.write_text(_NODE_RUNNER)
        in_dir, out_dir = root / "in", root / "out"
        in_dir.mkdir()
        for name, blob in inputs.items():
            dest = in_dir / "stage" / name.lstrip("/")
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(blob)
        manifest = root / "manifest.json"
        manifest.write_text(json.dumps({
            "args": args,
            "inputs": sorted(inputs),
            "outputs": outputs,
            "results": {},
        }))
        proc = subprocess.run(
            [shutil.which("node") or "node", str(runner), tool, str(WASM_DIR), str(manifest),
             str(in_dir), str(out_dir)],
            capture_output=True, text=True, timeout=600,
        )
        result_path = out_dir / "result.json"
        if not result_path.exists():
            raise RuntimeError(
                f"{tool} failed:\n{proc.stdout[-8000:]}\n{proc.stderr[-8000:]}")
        payload = json.loads(result_path.read_text())
        if payload["code"] not in (0, None):
            raise RuntimeError(
                f"{tool} exited {payload['code']}:\n{proc.stdout[-8000:]}\n"
                f"{proc.stderr[-8000:]}")
        out: dict[str, bytes | None] = {}
        for name in outputs:
            produced = payload["results"].get(name)
            p = out_dir / name.lstrip("/")
            out[name] = p.read_bytes() if produced and p.exists() else None
        return out


def assemble(asm_text: str, files: dict[str, bytes] | None = None,
             include_dirs: list[str] | None = None,
             defines: dict[str, str] | None = None) -> bytes:
    """Assemble one .asm source (plus optional include tree) to an object file.

    ``files`` maps paths *as the source refers to them* (e.g.
    ``banks/bank00.asm``) to their contents; they are staged next to the
    source inside the tool's virtual filesystem.
    """
    args = ["-o", "/out.o"]
    for d in include_dirs or []:
        args += ["-I", d]
    for k, v in (defines or {}).items():
        args.append(f"-D{k}={v}")
    args.append("/src.asm")

    inputs: dict[str, bytes] = {"/src.asm": asm_text.encode()}
    for path, blob in (files or {}).items():
        inputs[path if path.startswith("/") else "/" + path] = blob

    if backend() == "native":
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            src = Path(td) / "src.asm"
            src.write_text(asm_text)
            for path, blob in (files or {}).items():
                dest = Path(td) / path
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(blob)
            obj = Path(td) / "out.o"
            subprocess.run(["rgbasm", "-o", str(obj), *args[1:-1], str(src)],
                           check=True, capture_output=True, text=True)
            return obj.read_bytes()
    res = _run_wasm("rgbasm", args, inputs, ["/out.o"])
    obj = res["/out.o"]
    if obj is None:
        raise RuntimeError("rgbasm produced no object file")
    return obj


def link(objects: list[bytes], link_script: str | None = None,
         out: str = "/out.gbc") -> bytes:
    inputs: dict[str, bytes] = {}
    names = []
    for i, o in enumerate(objects):
        p = f"/o{i}.o"
        inputs[p] = o
        names.append(p)
    args = ["-o", out] + names
    if link_script is not None:
        inputs["/link.script"] = link_script.encode()
        args.insert(0, "-l")
        args.insert(1, "/link.script")
    res = _run_wasm("rgblink", args, inputs, [out])
    data = res[out]
    if data is None:
        raise RuntimeError("rgblink produced no output")
    return data


def fix(rom: bytes, pad_value: int = 0x00, **kwargs) -> bytes:
    args = ["-o", "/out.gbc", "-p", f"{pad_value:d}"]
    for flag in ("v", "C", "j", "g", "O"):
        if kwargs.get(flag.lower().replace("c", "c")):
            pass
    for key, value in kwargs.items():
        if value is True:
            args.append(f"-{key}")
        elif value not in (None, False):
            args.append(f"-{key}")
            args.append(str(value))
    args.append("/in.gbc")
    res = _run_wasm("rgbfix", args, {"/in.gbc": rom}, ["/out.gbc"])
    data = res["/out.gbc"]
    if data is None:
        raise RuntimeError("rgbfix produced no output")
    return data
