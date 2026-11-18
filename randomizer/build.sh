#!/usr/bin/env bash
set -euo pipefail

# Build Kiwi's Local Multi-Game Randomizer as a Java 8 compatible runnable JAR.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_DIR="$SCRIPT_DIR/src/main/java"
CLASSES_DIR="$SCRIPT_DIR/out/classes"
MANIFEST="$SCRIPT_DIR/out/MANIFEST.MF"
JAR_OUT="$REPO_ROOT/KiwisMultiGameRandomizer.jar"

rm -rf "$CLASSES_DIR"
mkdir -p "$CLASSES_DIR"

# Collect all Java sources
SOURCES=()
while IFS= read -r -d '' file; do
  SOURCES+=("$file")
done < <(find "$SRC_DIR" -name "*.java" -print0 | sort -z)

# Use --release 8 on JDK 9+, or -source 1.8 -target 1.8 on JDK 8
if javac --help 2>&1 | grep -q -- "--release"; then
  javac --release 8 -encoding UTF-8 -d "$CLASSES_DIR" "${SOURCES[@]}"
else
  javac -source 1.8 -target 1.8 -encoding UTF-8 -d "$CLASSES_DIR" "${SOURCES[@]}"
fi

cat > "$MANIFEST" << 'EOF'
Manifest-Version: 1.0
Main-Class: com.kiwi.randomizer.Main
EOF

jar cfm "$JAR_OUT" "$MANIFEST" -C "$CLASSES_DIR" .
echo "Built Java 8 compatible JAR: $JAR_OUT"
