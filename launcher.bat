@echo off
setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"
set "CONSOLE_MODE=0"
if /i "%~1"=="--console" set "CONSOLE_MODE=1"
cd /d "%SCRIPT_DIR%"

set DEFAULT_MEM=4
set MAX_MEM=%DEFAULT_MEM%
set CUSTOM_JAVA_HOME=

set "CONFIG_FILE=%SCRIPT_DIR%launcher.properties"

if exist "%CONFIG_FILE%" (
    for /f "tokens=1,2 delims==" %%a in ('type "%CONFIG_FILE%"') do (
        set "KEY=%%a"
        set "VALUE=%%b"
        
        set KEY=!KEY: =!
        
        if /i "!KEY!"=="max.memory.gb" if defined VALUE set "MAX_MEM=!VALUE: =!"
        if /i "!KEY!"=="java.home" if defined VALUE set "CUSTOM_JAVA_HOME=!VALUE!"
    )
)

set JAVA_CMD=java
set SCRIPT_DIR=%~dp0

if not "!CUSTOM_JAVA_HOME!"=="" (
    set JAVA_CMD=!CUSTOM_JAVA_HOME!\bin\java.exe
    echo Using custom Java: !JAVA_CMD!
) else if exist "%SCRIPT_DIR%jre\bin\java.exe" (
    set JAVA_CMD=%SCRIPT_DIR%jre\bin\java.exe
    echo Using bundled JRE: !JAVA_CMD!
) else if not "%JAVA_HOME%"=="" (
    set JAVA_CMD=%JAVA_HOME%\bin\java.exe
    echo Using JAVA_HOME: %JAVA_HOME%
) else (
    echo Using Java from PATH
)

if "%~1"=="--apply-update" if not "%~2"=="" (
    > "%SCRIPT_DIR%current" echo %~2
    echo Applied update version %~2
)

set APP_JAR=%SCRIPT_DIR%seeloggyplus.jar
if exist "%SCRIPT_DIR%current" (
    set /p CURRENT_VER=<"%SCRIPT_DIR%current"
    if exist "%SCRIPT_DIR%versions\!CURRENT_VER!\seeloggyplus.jar" (
        set APP_JAR=%SCRIPT_DIR%versions\!CURRENT_VER!\seeloggyplus.jar
    )
)

echo Starting SeeLoggy+ with %MAX_MEM%GB max memory...

if "!CONSOLE_MODE!"=="1" (
    "!JAVA_CMD!" -Xmx%MAX_MEM%g -jar "!APP_JAR!"
) else (
    set "JAVA_CMD=!JAVA_CMD:java.exe=javaw.exe!"
    if "!JAVA_CMD!"=="java" set "JAVA_CMD=javaw"
    start "" "!JAVA_CMD!" -Xmx%MAX_MEM%g -jar "!APP_JAR!"
)

endlocal
