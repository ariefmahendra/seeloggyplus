@echo off
echo Cleaning build...
call gradlew.bat clean

echo.
echo Building project with Lombok annotation processing...
call gradlew.bat build -x test --info

echo.
echo Build complete!
pause

