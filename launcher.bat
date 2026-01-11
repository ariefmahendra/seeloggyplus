@echo off
REM SeeLoggy+ Launcher for Windows
REM This script reads configuration from launcher.properties and starts the application

setlocal enabledelayedexpansion

REM Default values
set DEFAULT_MEM=4
set MAX_MEM=%DEFAULT_MEM%
set CUSTOM_JAVA_HOME=

REM Config file path
set CONFIG_FILE=launcher.properties

REM Read configuration from properties file if it exists
if exist "%CONFIG_FILE%" (
    for /f "tokens=1,2 delims==" %%a in ('type "%CONFIG_FILE%"') do (
        set KEY=%%a
        set VALUE=%%b
        
        REM Trim whitespace
        set KEY=!KEY: =!
        set VALUE=!VALUE: =!
        
        if /i "!KEY!"=="max.memory.gb" set MAX_MEM=!VALUE!
        if /i "!KEY!"=="java.home" set CUSTOM_JAVA_HOME=!VALUE!
    )
)

REM Determine which Java to use
set JAVA_CMD=java

if not "!CUSTOM_JAVA_HOME!"=="" (
    REM Use custom Java path from config
    set JAVA_CMD=!CUSTOM_JAVA_HOME!\bin\java.exe
    echo Using custom Java: !JAVA_CMD!
) else if not "%JAVA_HOME%"=="" (
    REM Use JAVA_HOME environment variable
    set JAVA_CMD=%JAVA_HOME%\bin\java.exe
    echo Using JAVA_HOME: %JAVA_HOME%
) else (
    REM Use java from PATH
    echo Using Java from PATH
)

echo Starting SeeLoggy+ with %MAX_MEM%GB max memory...

REM Launch application (JavaFX modules are already bundled in fat JAR)
"!JAVA_CMD!" -Xmx%MAX_MEM%g -jar seeloggyplus.jar

endlocal
