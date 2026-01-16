#!/bin/bash

# Firebase Test Lab Runner Script
# This script builds APKs and runs instrumentation tests on Firebase Test Lab

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ID="${FIREBASE_PROJECT_ID:-health-tracker-app}"
RESULTS_BUCKET="${RESULTS_BUCKET:-gs://health-tracker-test-results}"
RESULTS_DIR="test-results-$(date +%Y%m%d-%H%M%S)"

echo -e "${GREEN}=== Firebase Test Lab Runner ===${NC}"
echo ""

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    echo -e "${RED}Error: gcloud CLI is not installed${NC}"
    echo "Install it from: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Check if authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" &> /dev/null; then
    echo -e "${YELLOW}Not authenticated. Running gcloud auth login...${NC}"
    gcloud auth login
fi

# Set project
echo -e "${YELLOW}Setting project to: $PROJECT_ID${NC}"
gcloud config set project "$PROJECT_ID"

# Build APKs
echo -e "${YELLOW}Building debug APK...${NC}"
./gradlew assembleDebug

echo -e "${YELLOW}Building instrumentation test APK...${NC}"
./gradlew assembleDebugAndroidTest

# Check if APKs exist
APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

if [ ! -f "$APP_APK" ]; then
    echo -e "${RED}Error: App APK not found at $APP_APK${NC}"
    exit 1
fi

if [ ! -f "$TEST_APK" ]; then
    echo -e "${RED}Error: Test APK not found at $TEST_APK${NC}"
    exit 1
fi

echo -e "${GREEN}APKs built successfully${NC}"
echo ""

# Run tests on Firebase Test Lab
echo -e "${YELLOW}Running tests on Firebase Test Lab...${NC}"
echo "This may take several minutes..."
echo ""

gcloud firebase test android run \
  --type instrumentation \
  --app "$APP_APK" \
  --test "$TEST_APK" \
  --device model=Pixel2,version=28,locale=en,orientation=portrait \
  --device model=Pixel3,version=29,locale=en,orientation=portrait \
  --device model=Pixel4,version=30,locale=en,orientation=portrait \
  --device model=Pixel5,version=31,locale=en,orientation=portrait \
  --timeout 30m \
  --results-bucket="$RESULTS_BUCKET" \
  --results-dir="$RESULTS_DIR" \
  --environment-variables coverage=true,clearPackageData=true \
  --directories-to-pull /sdcard/screenshots \
  --num-uniform-shards 4 \
  --performance-metrics \
  --record-video

# Check test results
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Tests passed successfully!${NC}"
    echo ""
    echo "Results available at:"
    echo "$RESULTS_BUCKET/$RESULTS_DIR"
    echo ""
    
    # Download results
    echo -e "${YELLOW}Downloading test results...${NC}"
    mkdir -p "./test-results/$RESULTS_DIR"
    gsutil -m cp -r "$RESULTS_BUCKET/$RESULTS_DIR/*" "./test-results/$RESULTS_DIR/"
    
    echo -e "${GREEN}Results downloaded to: ./test-results/$RESULTS_DIR${NC}"
else
    echo -e "${RED}Tests failed!${NC}"
    echo ""
    echo "Check results at:"
    echo "$RESULTS_BUCKET/$RESULTS_DIR"
    exit 1
fi
