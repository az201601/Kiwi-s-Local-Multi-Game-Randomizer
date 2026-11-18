@echo off
setlocal enabledelayedexpansion

REM Build Kiwi's Local Multi-Game Randomizer as a Java 8 compatible runnable JAR.
set "SCRIPT_DIR=%~dp0"
set "SRC_DIR=%SCRIPT_DIR%src\main\java"
set "OUT_DIR=%SCRIPT_DIR%out"
set "CLASSES_DIR=%OUT_DIR%\classes"
set "MANIFEST=%OUT_DIR%\MANIFEST.MF"
set "JAR_OUT=%SCRIPT_DIR%..\KiwisMultiGameRandomizer.jar"
set "SOURCES_LIST=%OUT_DIR%\sources.txt"

if exist "%CLASSES_DIR%" rmdir /s /q "%CLASSES_DIR%"
mkdir "%CLASSES_DIR%"

dir /s /b "%SRC_DIR%\*.java" > "%SOURCES_LIST%"

javac --help 2>&1 | findstr /c:"--release" >nul
if %errorlevel%==0 (
    javac --release 8 -encoding UTF-8 -d "%CLASSES_DIR%" @"%SOURCES_LIST%"
) else (
    javac -source 1.8 -target 1.8 -encoding UTF-8 -d "%CLASSES_DIR%" @"%SOURCES_LIST%"
)
if %errorlevel% neq 0 exit /b %errorlevel%

(
    echo Manifest-Version: 1.0
    echo Main-Class: com.kiwi.randomizer.Main
) > "%MANIFEST%"

jar cfm "%JAR_OUT%" "%MANIFEST%" -C "%CLASSES_DIR%" .
if %errorlevel% neq 0 exit /b %errorlevel%

echo Built Java 8 compatible JAR: %JAR_OUT%
