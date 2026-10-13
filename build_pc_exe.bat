@echo off
echo ===================================================
echo     NAFI TV 24 - Windows PC Installer Builder (.exe)
echo ===================================================
echo.
echo Checking Java Environment...
java -version
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH! Please install JDK 17 or JDK 21.
    pause
    exit /b
)

echo.
echo Building NAFI TV 24 Windows (.exe) Package...
call gradlew.bat :desktop:packageExe

echo.
echo ===================================================
if %errorlevel% equ 0 (
    echo [SUCCESS] .exe installer generated successfully!
    echo Location: desktop\build\compose\binaries\main\exe\
) else (
    echo [FAILED] Build failed! Check the logs above.
)
echo ===================================================
pause
