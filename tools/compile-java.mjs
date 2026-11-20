// Compiles Java sources with the TeaVM-built javac running as WebAssembly.
//
// We have no JDK on this machine (only a JRE), so we drive the teavm-javac
// compiler module directly. It is a real OpenJDK javac; it just runs in WASM.
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const pkg = process.env.JAVA_COMPILER_PKG;
const srcDir = process.argv[2];
const outJar = process.argv[3];
if (!pkg || !srcDir || !outJar) {
  console.error('usage: JAVA_COMPILER_PKG=<dir> node compile-java.mjs <srcDir> <outJar>');
  process.exit(2);
}

const { load } = await import(pathToFileURL(path.join(pkg, 'compiler.wasm-runtime.js')).href);
const wasm = fs.readFileSync(path.join(pkg, 'compiler.wasm'));
const sdk = fs.readFileSync(path.join(pkg, 'compile-classlib-teavm.bin'));

const teavm = await load(wasm);
const compiler = teavm.exports.createCompiler();
compiler.setSdk(new Int8Array(sdk));
compiler.onDiagnostic((d) => {
  console.error(`javac: ${d.severity}: ${d.fileName}:${d.lineNumber}: ${d.message}`);
});

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(p));
    else if (entry.name.endsWith('.java')) out.push(p);
  }
  return out;
}

const files = walk(srcDir).sort();
for (const file of files) {
  // teavm-javac expects the source name to match the public type it declares.
  compiler.addSourceFile(path.basename(file), fs.readFileSync(file, 'utf8'));
}

const ok = compiler.compile();
if (!ok) {
  console.error('compilation failed');
  process.exit(1);
}
fs.writeFileSync(outJar, Buffer.from(compiler.getOutputJar()));
console.log(`compiled ${files.length} source file(s) -> ${outJar}`);
