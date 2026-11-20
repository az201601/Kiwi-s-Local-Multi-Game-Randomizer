#!/usr/bin/env bash
# Builds dist/hm-randomizer.jar.
#
# There is no JDK on this box, so Java sources are compiled by the TeaVM-built
# javac that runs as WebAssembly (npm package @browserrun/java). The result is
# then packaged into a runnable JAR.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG="tools/.javac/node_modules/@browserrun/java"
if [ ! -f "$PKG/compiler.wasm" ]; then
  echo "Fetching WASM javac..."
  mkdir -p tools/.javac
  npm install --prefix tools/.javac --no-audit --no-fund @browserrun/java
fi

mkdir -p build dist
JAVA_COMPILER_PKG="$PKG" node tools/compile-java.mjs src/main/java build/classes.jar
python3 tools/make_jar.py build/classes.jar dist/hm-randomizer.jar nz.kiwi.hmrandom.Main
