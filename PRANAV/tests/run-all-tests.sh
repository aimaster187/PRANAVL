#!/usr/bin/env bash
set -euo pipefail

echo "=== [1/5] PRANAV Node.js Backend & Telegram Multi-Bot Integration Tests ==="
node --test PRANAV/backend/tests/backend.test.js

echo "=== [2/5] PRANAV Admin -> Backend -> Client SDK E2E Contract Tests ==="
node --test PRANAV/tests/e2e_contract.test.js

echo "=== [3/5] PRANAV DEX Build + Sample Authorized Target APK Multi-DEX Integration & Inspector ==="
bash PRANAV/tests/build_and_verify_sample_apk.sh

echo "=== [4/5] PRANAV Firestore Security Rules Emulator Tests ==="
firebase emulators:exec --only firestore --project demo-pranav-rules "node --test firestore.test.js"

echo "=== [5/5] PRANAV Android Admin App Robolectric Unit & CUJ Tests ==="
gradle :app:testDebugUnitTest

echo "ALL PRANAV ECOSYSTEM TEST SUITES PASSED."
