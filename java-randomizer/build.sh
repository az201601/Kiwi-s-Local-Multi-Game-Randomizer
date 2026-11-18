#!/bin/bash
# Build the Harvest Moon GB Randomizer grandpa-letter patcher (Java 8+).
set -e
cd "$(dirname "$0")"

echo "=== Harvest Moon GB Randomizer - Java patcher ==="
java -version 2>&1 | head -n 1
javac -version 2>&1

mkdir -p out/classes
echo "Compiling..."
javac -encoding UTF-8 -d out/classes $(find src -name "*.java")

echo "Packaging jar..."
jar cfe out/randomizer.jar com.kiwi.randomizer.Main -C out/classes .

echo ""
echo "Build OK: out/randomizer.jar"
echo "Run:  java -jar out/randomizer.jar --rom \"../Harvest Moon GB (USA) (SGB Enhanced).gbc\" --out \"../Harvest Moon GB Randomizer.gbc\" --text grandpa_welcome.txt"
