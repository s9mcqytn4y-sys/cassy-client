#!/usr/bin/env bash
# Runs the live instrumentation package once, then retries only the classes that failed (S3-Q6).
# KDS gate lines (tag CassyKdsGate) from every attempt land in artifacts/KdsLatency.json.
set -eu

runner=com.azuredevid.cassy.dev.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p artifacts
adb logcat -c

status=0
adb shell am instrument -w -e package com.azuredevid.cassy.live "$runner" | tee instrument.log
if ! grep -q "^OK (" instrument.log; then
  failed=$(grep -oE '^(Error|Failure) in [A-Za-z0-9_]+\(com\.azuredevid\.cassy\.live\.[A-Za-z0-9_]+\)' instrument.log \
    | sed -E 's/.*\((.*)\)/\1/' | sort -u | paste -sd, -)
  if [ -n "$failed" ]; then
    echo "retrying failed classes: $failed"
    adb shell am instrument -w -e class "$failed" "$runner" | tee instrument-retry.log
  else
    echo "no failed class parsed, retrying the whole package"
    adb shell am instrument -w -e package com.azuredevid.cassy.live "$runner" | tee instrument-retry.log
  fi
  grep -q "^OK (" instrument-retry.log || status=1
fi

adb logcat -d -v raw -s CassyKdsGate:I | grep '^{' > artifacts/KdsLatency.json || true
exit "$status"
