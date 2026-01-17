@echo off
echo ========================================
echo AI Services Test Script
echo ========================================
echo.

echo [1/5] Checking API Key in local.properties...
findstr "OPENAI_API_KEY" local.properties
if %ERRORLEVEL% EQU 0 (
    echo ✓ API Key found in local.properties
) else (
    echo ✗ API Key NOT found in local.properties
    echo Please add: OPENAI_API_KEY=your-key-here
    pause
    exit /b 1
)
echo.

echo [2/5] Cleaning build...
call gradlew clean
echo.

echo [3/5] Building app...
call gradlew assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo ✗ Build failed!
    pause
    exit /b 1
)
echo ✓ Build successful
echo.

echo [4/5] Installing app...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo ✗ Installation failed!
    echo Make sure device is connected: adb devices
    pause
    exit /b 1
)
echo ✓ App installed
echo.

echo [5/5] Starting app and monitoring logs...
echo.
echo Starting app...
adb shell am start -n com.healthtracker/.presentation.MainActivity
echo.
echo Monitoring AI service logs (Press Ctrl+C to stop)...
echo ========================================
adb logcat -s OpenAIService:D EdgeTTSService:D AIChatViewModel:D

pause
