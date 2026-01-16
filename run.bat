@echo off
REM ============================================
REM Health Tracker - All-in-One Build & Debug Tool
REM ============================================

REM Configuration
set "JAVA_HOME=C:\Users\Mohammad Tabish\.jdks\ms-17.0.17"
set "ANDROID_SDK=C:\Users\Mohammad Tabish\AppData\Local\Android\Sdk"
set "ADB=%ANDROID_SDK%\platform-tools\adb.exe"
set "PACKAGE=com.healthtracker"

:MENU
cls
echo ============================================
echo   HEALTH TRACKER - BUILD ^& DEBUG TOOL
echo ============================================
echo.
echo [1] Full Build ^& Install ^& Run
echo [2] Quick Reinstall (no rebuild)
echo [3] Check Compilation Errors
echo [4] View Live Logs (Logcat)
echo [5] Check App Crashes
echo [6] Device Info
echo [7] Clean Build
echo [8] Uninstall App
echo [9] Launch App Only
echo [0] Exit
echo.
set /p choice="Select option (0-9): "

if "%choice%"=="1" goto FULL_BUILD
if "%choice%"=="2" goto QUICK_INSTALL
if "%choice%"=="3" goto CHECK_ERRORS
if "%choice%"=="4" goto LOGCAT
if "%choice%"=="5" goto CHECK_CRASHES
if "%choice%"=="6" goto DEVICE_INFO
if "%choice%"=="7" goto CLEAN_BUILD
if "%choice%"=="8" goto UNINSTALL
if "%choice%"=="9" goto LAUNCH_ONLY
if "%choice%"=="0" goto END
goto MENU

REM ============================================
REM [1] FULL BUILD & INSTALL & RUN
REM ============================================
:FULL_BUILD
cls
echo ============================================
echo   FULL BUILD ^& INSTALL ^& RUN
echo ============================================
echo.

echo [1/7] Validating Java...
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java not found at: %JAVA_HOME%
    echo Update JAVA_HOME in this script
    pause
    goto MENU
)
"%JAVA_HOME%\bin\java.exe" -version
echo [OK] Java found
echo.

echo [2/7] Checking device connection...
"%ADB%" devices
"%ADB%" devices | find "device" | find /v "List" > nul
if errorlevel 1 (
    echo [WARNING] No device detected!
    echo Connect device or start emulator
    pause
)
echo.

echo [3/7] Cleaning previous build...
REM Skipping clean for faster builds - uncomment below if needed
REM call gradlew.bat clean
echo [OK] Skipped clean (using cached build)
echo.

echo [4/7] Building APK (this may take time)...
call gradlew.bat assembleDebug --stacktrace > build_output.txt 2>&1
if errorlevel 1 (
    echo.
    echo [ERROR] Build FAILED!
    echo.
    echo Showing errors:
    echo ----------------------------------------
    type build_output.txt | findstr /i "error: FAILURE"
    echo ----------------------------------------
    echo.
    echo Full log saved to: build_output.txt
    echo.
    pause
    goto MENU
)
echo [OK] Build successful!
echo.

echo [5/7] Verifying APK...
if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo [ERROR] APK not found!
    pause
    goto MENU
)
for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do echo [OK] APK size: %%~zA bytes
echo.

echo [6/7] Installing APK...
"%ADB%" install -r "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 (
    echo [WARNING] Install failed, trying uninstall first...
    "%ADB%" uninstall %PACKAGE%
    "%ADB%" install "app\build\outputs\apk\debug\app-debug.apk"
    if errorlevel 1 (
        echo [ERROR] Installation failed!
        pause
        goto MENU
    )
)
echo [OK] Installation successful!
echo.

echo [7/7] Launching app...
"%ADB%" shell am start -n %PACKAGE%/.presentation.MainActivity
echo [OK] App launched!
echo.

echo Starting logcat in 3 seconds...
timeout /t 3 /nobreak > nul
"%ADB%" logcat -c
echo.
echo ============================================
echo   LIVE LOGS (Press Ctrl+C to stop)
echo ============================================
echo.
"%ADB%" logcat -v time | findstr /i "healthtracker AndroidRuntime FATAL Exception Error"
pause
goto MENU

REM ============================================
REM [2] QUICK REINSTALL
REM ============================================
:QUICK_INSTALL
cls
echo ============================================
echo   QUICK REINSTALL
echo ============================================
echo.

if not exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo [ERROR] APK not found! Run Full Build first (option 1)
    pause
    goto MENU
)

echo Uninstalling old version...
"%ADB%" uninstall %PACKAGE%
echo.

echo Installing APK...
"%ADB%" install "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 (
    echo [ERROR] Installation failed!
    pause
    goto MENU
)
echo [OK] Installed!
echo.

echo Launching app...
"%ADB%" shell am start -n %PACKAGE%/.presentation.MainActivity
echo [OK] Launched!
echo.

echo Starting logcat...
"%ADB%" logcat -c
"%ADB%" logcat -v time | findstr /i "healthtracker"
pause
goto MENU

REM ============================================
REM [3] CHECK COMPILATION ERRORS
REM ============================================
:CHECK_ERRORS
cls
echo ============================================
echo   CHECKING COMPILATION ERRORS
echo ============================================
echo.

echo Compiling domain module...
call gradlew.bat :domain:compileKotlin --stacktrace 2>&1 > domain_errors.txt
findstr /i "error: warning:" domain_errors.txt
echo.

echo Compiling data module...
call gradlew.bat :data:compileDebugKotlin --stacktrace 2>&1 > data_errors.txt
findstr /i "error: warning:" data_errors.txt
echo.

echo Compiling app module...
call gradlew.bat :app:compileDebugKotlin --stacktrace 2>&1 > app_errors.txt
findstr /i "error: warning:" app_errors.txt
echo.

echo Full logs saved to:
echo   - domain_errors.txt
echo   - data_errors.txt
echo   - app_errors.txt
echo.
pause
goto MENU

REM ============================================
REM [4] VIEW LIVE LOGS
REM ============================================
:LOGCAT
cls
echo ============================================
echo   LIVE LOGCAT (Press Ctrl+C to stop)
echo ============================================
echo.

"%ADB%" logcat -c
"%ADB%" logcat -v time *:V | findstr /i "healthtracker AndroidRuntime FATAL Exception Error"
pause
goto MENU

REM ============================================
REM [5] CHECK APP CRASHES
REM ============================================
:CHECK_CRASHES
cls
echo ============================================
echo   CHECKING CRASHES ^& ERRORS
echo ============================================
echo.

echo Recent Crashes:
echo ----------------------------------------
"%ADB%" logcat -d -v time | findstr /i "FATAL AndroidRuntime"
echo.

echo Recent Errors:
echo ----------------------------------------
"%ADB%" logcat -d -v time | findstr /i "healthtracker.*Error"
echo.

echo App-specific Logs:
echo ----------------------------------------
"%ADB%" logcat -d -v time | findstr /i "healthtracker" | more
echo.

pause
goto MENU

REM ============================================
REM [6] DEVICE INFO
REM ============================================
:DEVICE_INFO
cls
echo ============================================
echo   DEVICE INFORMATION
echo ============================================
echo.

echo Connected Devices:
"%ADB%" devices
echo.

echo Android Version:
"%ADB%" shell getprop ro.build.version.release
echo.

echo Device Model:
"%ADB%" shell getprop ro.product.model
echo.

echo SDK Version:
"%ADB%" shell getprop ro.build.version.sdk
echo.

echo Installed App Info:
"%ADB%" shell pm list packages | findstr healthtracker
"%ADB%" shell dumpsys package %PACKAGE% | findstr "versionName versionCode"
echo.

echo Battery Level:
"%ADB%" shell dumpsys battery | findstr level
echo.

pause
goto MENU

REM ============================================
REM [7] CLEAN BUILD
REM ============================================
:CLEAN_BUILD
cls
echo ============================================
echo   CLEAN BUILD
echo ============================================
echo.

echo Cleaning build directories...
call gradlew.bat clean
echo.

echo Clearing Gradle cache...
call gradlew.bat --stop
echo.

echo [OK] Clean completed!
echo Run option 1 to build fresh
echo.

pause
goto MENU

REM ============================================
REM [8] UNINSTALL APP
REM ============================================
:UNINSTALL
cls
echo ============================================
echo   UNINSTALL APP
echo ============================================
echo.

echo Uninstalling %PACKAGE%...
"%ADB%" uninstall %PACKAGE%
echo.

echo [OK] Uninstalled!
echo.

pause
goto MENU

REM ============================================
REM [9] LAUNCH APP ONLY
REM ============================================
:LAUNCH_ONLY
cls
echo ============================================
echo   LAUNCH APP
echo ============================================
echo.

echo Launching %PACKAGE%...
"%ADB%" shell am start -n %PACKAGE%/.presentation.MainActivity
echo.

echo [OK] Launched!
echo.

echo Starting logcat...
"%ADB%" logcat -c
"%ADB%" logcat -v time | findstr /i "healthtracker"
pause
goto MENU

REM ============================================
REM EXIT
REM ============================================
:END
cls
echo.
echo Goodbye!
echo.
exit /b 0
