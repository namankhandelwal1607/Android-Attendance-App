@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo        Attendance App - Phone Deployment Tool
echo ========================================================
echo.

:: Check if adb is available in PATH or winget directory
where adb >nul 2>nul
if %errorlevel% neq 0 (
    if exist "%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe" (
        set "PATH=%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools;%PATH%"
    ) else (
        echo [ERROR] ADB is not detected. Please ensure Android Platform Tools is installed.
        pause
        exit /b 1
    )
)

echo [1/4] Checking ADB connection...
adb start-server >nul 2>nul

echo [2/4] Waiting for your Android phone to connect...
echo       Please ensure:
echo       1. Phone is connected to PC via USB cable.
echo       2. USB Debugging is turned ON in Developer Options.
echo       3. Check your phone screen for 'Allow USB debugging?' prompt and tap 'Allow'.
echo.

adb wait-for-device

:: Check device status
for /f "tokens=1,2" %%A in ('adb devices ^| findstr /v "List of devices"') do (
    if "%%B"=="unauthorized" (
        echo.
        echo [ACTION REQUIRED] Your device is connected but UNAUTHORIZED!
        echo Look at your phone's screen right now and tap 'Allow' or 'OK' on the USB Debugging popup.
        echo Check the box 'Always allow from this computer'.
        echo.
        echo Waiting for authorization...
        :wait_auth
        timeout /t 2 /nobreak >nul
        for /f "tokens=1,2" %%C in ('adb devices ^| findstr "%%A"') do (
            if "%%D"=="device" goto device_ready
            if "%%D"=="unauthorized" goto wait_auth
        )
    )
)

:device_ready
echo.
echo [3/4] Device connected and authorized!
echo Installing AttendanceApp.apk to your phone...
echo.

adb install -r -d "%~dp0AttendanceApp.apk"

if %errorlevel% equ 0 (
    echo.
    echo [4/4] APK installed successfully!
    echo Launching Attendance App on your phone...
    adb shell am start -n com.attendance.app/.MainActivity
    echo.
    echo ========================================================
    echo  SUCCESS: Attendance App is now running on your phone!
    echo ========================================================
) else (
    echo.
    echo ========================================================
    echo [ERROR] Installation failed.
    echo - If you have Xiaomi/Redmi: Enable 'Install via USB' in Developer Options.
    echo - If phone locked: Unlock phone screen and try again.
    echo ========================================================
)

echo.
pause
