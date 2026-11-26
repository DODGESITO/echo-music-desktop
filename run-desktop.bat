@echo off
setlocal
title Echo Music Desktop

echo ========================================================
echo        ECHO MUSIC - VERSION DE ESCRITORIO
echo ========================================================
echo.
echo Iniciando entorno y cargando la ventana de la aplicacion...
echo.

cd /d "%~dp0"

if exist "C:\Program Files\Java\jdk-21.0.12\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.12"
) else (
    for /d %%D in ("C:\Program Files\Java\jdk-21*") do (
        if exist "%%D\bin\java.exe" set "JAVA_HOME=%%D"
    )
)

if defined JAVA_HOME (
    echo Usando JDK 21: %JAVA_HOME%
)

call gradlew.bat run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================================
    echo   Hubo un problema al ejecutar la aplicacion.
    echo ========================================================
    echo.
    pause
)
