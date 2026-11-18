@echo off
REM Build the Harvest Moon GB Randomizer grandpa-letter patcher (Java 8+).
setlocal
cd /d %~dp0

echo === Harvest Moon GB Randomizer - Java patcher ===
java -version 2>&1 | findstr /C:"version"
javac -version 2>&1

if not exist out\classes mkdir out\classes
echo Compiling...
javac -encoding UTF-8 -d out\classes src\main\java\com\kiwi\randomizer\*.java
if errorlevel 1 goto :fail

echo Packaging jar...
jar cfe out\randomizer.jar com.kiwi.randomizer.Main -C out\classes .
if errorlevel 1 goto :fail

echo.
echo Build OK: out\randomizer.jar
echo Run:  java -jar out\randomizer.jar --rom "..\Harvest Moon GB (USA) (SGB Enhanced).gbc" --out "..\Harvest Moon GB Randomizer.gbc" --text grandpa_welcome.txt
goto :eof

:fail
echo Build FAILED.
exit /b 1
