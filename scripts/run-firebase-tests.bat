@echo off
REM Firebase Test Lab Runner Script for Windows
REM This script builds APKs and runs instrumentation tests on Firebase Test Lab

setlocal enabledelayedexpansion

REM Configuration
set PROJECT_ID=health-tracker-app
set RESULTS_BUCKET=gs://health-tracker-test-results
set RESULTS_DIR=test-results-%date:~-4,4%%date:~-10,2%%date:~-7,2%-%time:~0,2%%time:~3,2%%time:~6,2%
set RESULTS_DIR=%RESULTS_DIR: =0%

echo === Firebase Test Lab Runner ===
echo.

REM Check if gcloud is installed
where gcloud >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: gcloud CLI is not installed
    echo Install it from: https://cloud.google.com/sdk/docs/install
    exit /b 1
)

REM Check if authenticated
gcloud auth list --filter=status:ACTIVE --format="value(account)" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Not authenticated. Running gcloud auth login...
    gcloud auth login
)

REM Set project
echo Setting project to: %PROJECT_ID%
gcloud config set project %PROJECT_ID%

REM Build APKs
echo Building debug APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo Error building debug APK
    exit /b 1
)

echo Building instrumentation test APK...
call gradlew.bat assembleDebugAndroidTest
if %ERRORLEVEL% neq 0 (
    echo Error building test APK
    exit /b 1
)

REM Check if APKs exist
set APP_APK=app\build\outputs\apk\debug\app-debug.apk
set TEST_APK=app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk

if not exist "%APP_APK%" (
    echo Error: App APK not found at %APP_APK%
    exit /b 1
)

if not exist "%TEST_APK%" (
    echo Error: Test APK not found at %TEST_APK%
    exit /b 1
)

echo APKs built successfully
echo.

REM Run tests on Firebase Test Lab
echo Running tests on Firebase Test Lab...
echo This may take several minutes...
echo.

gcloud firebase test android run ^
  --type instrumentation ^
  --app "%APP_APK%" ^
  --test "%TEST_APK%" ^
  --device model=Pixel2,version=28,locale=en,orientation=portrait ^
  --device model=Pixel3,version=29,locale=en,orientation=portrait ^
  --device model=Pixel4,version=30,locale=en,orientation=portrait ^
  --device model=Pixel5,version=31,locale=en,orientation=portrait ^
  --timeout 30m ^
  --results-bucket=%RESULTS_BUCKET% ^
  --results-dir=%RESULTS_DIR% ^
  --environment-variables coverage=true,clearPackageData=true ^
  --directories-to-pull /sdcard/screenshots ^
  --num-uniform-shards 4 ^
  --performance-metrics ^
  --record-video

if %ERRORLEVEL% equ 0 (
    echo Tests passed successfully!
    echo.
    echo Results available at:
    echo %RESULTS_BUCKET%/%RESULTS_DIR%
    echo.
    
    REM Download results
    echo Downloading test results...
    if not exist "test-results\%RESULTS_DIR%" mkdir "test-results\%RESULTS_DIR%"
    gsutil -m cp -r "%RESULTS_BUCKET%/%RESULTS_DIR%/*" "test-results\%RESULTS_DIR%\"
    
    echo Results downloaded to: test-results\%RESULTS_DIR%
) else (
    echo Tests failed!
    echo.
    echo Check results at:
    echo %RESULTS_BUCKET%/%RESULTS_DIR%
    exit /b 1
)

endlocal
