@echo off
setlocal
title Echo Music - Instalador
cd /d "%~dp0"

rem JDK 21: the usual folders on C: or E:
for /d %%D in ("C:\Program Files\Java\jdk-21*" "E:\Program Files\Java\jdk-21*") do (
    if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
)
if defined JAVA_HOME echo Usando JDK 21: %JAVA_HOME%

rem .msi: what the automatic updates install. .exe: the same installer for a first manual install.
call .\gradlew.bat packageMsi packageExe
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo No se pudo generar el instalador.
    pause
    exit /b 1
)

rem Checksum next to the .msi (upload it to the release too).
for %%F in ("build\compose\binaries\main\msi\*.msi") do (
    powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%%~fF').Hash.ToLower() + '  %%~nxF' | Set-Content -Encoding ascii '%%~fF.sha256'"
)

echo.
echo Instaladores listos en build\compose\binaries\main\msi y \exe
explorer "build\compose\binaries\main\msi"
