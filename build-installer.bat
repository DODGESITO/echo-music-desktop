@echo off
setlocal
title Echo Music - Instalador
cd /d "%~dp0"

rem JDK 21: the usual folders on C: or E:
for /d %%D in ("C:\Program Files\Java\jdk-21*" "E:\Program Files\Java\jdk-21*") do (
    if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
)
if defined JAVA_HOME echo Usando JDK 21: %JAVA_HOME%

call gradlew.bat packageExe
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo No se pudo generar el instalador.
    pause
    exit /b 1
)
echo.
echo Instalador listo en build\compose\binaries\main\exe
explorer "build\compose\binaries\main\exe"
