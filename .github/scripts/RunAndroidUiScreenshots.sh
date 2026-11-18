#!/usr/bin/env bash
set -euo pipefail

BASE_OUT="artifacts/ui-v2.5.0"

png_dims() {
  python3 - "$1" <<'PY'
import struct, sys
with open(sys.argv[1], "rb") as f:
    data = f.read(24)
w, h = struct.unpack(">II", data[16:24])
print(f"{w} {h}")
PY
}

ensure_tablet_landscape() {
  adb shell settings put system accelerometer_rotation 0
  adb shell svc power stayon true
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard

  adb shell wm density 320
  adb shell wm size 1600x2560
  adb shell settings put system user_rotation 1
  sleep 2
  adb exec-out screencap -p > /tmp/orient_check.png
  local dims w h
  dims=$(png_dims /tmp/orient_check.png)
  echo "Tablet display after user_rotation 1: $dims"
  read -r w h <<< "$dims"
  if [ "$w" -lt "$h" ]; then
    adb shell wm size 2560x1600
    adb shell settings put system user_rotation 0
    sleep 2
    adb exec-out screencap -p > /tmp/orient_check.png
    dims=$(png_dims /tmp/orient_check.png)
    echo "Tablet display after swapped wm override: $dims"
  fi
}

ensure_mobile_portrait() {
  adb shell settings put system accelerometer_rotation 0
  adb shell svc power stayon true
  adb shell input keyevent KEYCODE_WAKEUP
  adb shell wm dismiss-keyguard

  adb shell wm density 420
  adb shell wm size 1080x2400
  adb shell settings put system user_rotation 0
  sleep 2
  adb exec-out screencap -p > /tmp/orient_check.png
  local dims w h
  dims=$(png_dims /tmp/orient_check.png)
  echo "Mobile display after user_rotation 0: $dims"
  read -r w h <<< "$dims"
  if [ "$w" -gt "$h" ]; then
    adb shell settings put system user_rotation 1
    sleep 2
    adb exec-out screencap -p > /tmp/orient_check.png
    dims=$(png_dims /tmp/orient_check.png)
    echo "Mobile display after user_rotation 1: $dims"
  fi
}

run_capture_pass() {
  local form_factor="$1"
  local OUT_ROOT="$BASE_OUT/$form_factor"

  mkdir -p \
    "$OUT_ROOT/StartupSplash" \
    "$OUT_ROOT/StorePairing/Modals" \
    "$OUT_ROOT/StorePairing/Errors" \
    "$OUT_ROOT/ServerConfig/Tabs" \
    "$OUT_ROOT/ServerConfig/Modals" \
    "$OUT_ROOT/ServerConfig/Errors" \
    "$OUT_ROOT/PosLandscape/Modals" \
    "$OUT_ROOT/ShiftManagement/Modals" \
    "$OUT_ROOT/CatalogManagement/Modals" \
    "$OUT_ROOT/StoreSettings/Tabs"

  adb shell run-as com.azuredevid.cassy.dev rm -rf files/showcase >/dev/null 2>&1 || true

  # Cold start live launch and capture splash + StorePairingScreen
  adb shell am force-stop com.azuredevid.cassy.dev
  adb shell am start -n com.azuredevid.cassy.dev/com.azuredevid.cassy.MainActivity
  sleep 0.6
  adb exec-out screencap -p > "$OUT_ROOT/StartupSplash/OsSplash.png" || true

  for i in $(seq 1 30); do
    sleep 2
    adb shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1 || true
    if adb shell cat /data/local/tmp/ui.xml 2>/dev/null | grep -q "Scan Store Pairing QR"; then
      echo "[$form_factor] StorePairingScreen rendered at attempt $i"
      break
    fi
  done
  sleep 1
  adb exec-out screencap -p > "$OUT_ROOT/StorePairing/LiveDefaultOnline.png"

  adb shell cat /data/local/tmp/ui.xml > "$OUT_ROOT/live_ui_dump.xml" || true
  python3 - "$OUT_ROOT" <<'PY'
import re, subprocess, sys, time, xml.etree.ElementTree as ET

out_root = sys.argv[1]

def find_center(xml_path, target_text):
    tree = ET.parse(xml_path)
    for node in tree.iter("node"):
        txt = (node.attrib.get("text") or "") + " " + (node.attrib.get("content-desc") or "")
        if target_text in txt:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def tap(xy):
    subprocess.run(["adb", "shell", "input", "tap", str(xy[0]), str(xy[1])], check=True)

xml_file = f"{out_root}/live_ui_dump.xml"
scan_btn = find_center(xml_file, "Scan Store Pairing QR")
sign_in = find_center(xml_file, "Sign In & Connect Store")
hid_btn = find_center(xml_file, "USB Scanner")
print("Centers:", {"scan": scan_btn, "sign_in": sign_in, "hid": hid_btn})

# 1) Open Camera QR Scanner first from clean state
if scan_btn:
    tap(scan_btn)
    time.sleep(3)
    with open(f"{out_root}/StorePairing/Modals/LiveQrCameraScanner.png", "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/data/local/tmp/ui_cam.xml"], check=False)
    subprocess.run(["adb", "pull", "/data/local/tmp/ui_cam.xml", f"{out_root}/live_cam_dump.xml"], check=False)
    close_cam = None
    try:
        close_cam = find_center(f"{out_root}/live_cam_dump.xml", "Close camera scanner")
    except Exception:
        pass
    if close_cam:
        tap(close_cam)
    else:
        subprocess.run(["adb", "shell", "input", "keyevent", "KEYCODE_BACK"], check=True)
    time.sleep(1.5)

# 2) Trigger inline form validation on empty submit
if sign_in:
    tap(sign_in)
    time.sleep(1.5)
    with open(f"{out_root}/StorePairing/Errors/LiveValidation.png", "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)

# 3) Open USB HID / Paste code dialog
if hid_btn:
    tap(hid_btn)
    time.sleep(2)
    with open(f"{out_root}/StorePairing/Modals/LiveHidScannerDialog.png", "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)
PY

  rm -f "$OUT_ROOT/live_ui_dump.xml" "$OUT_ROOT/live_cam_dump.xml"

  # Run Compose Showcase instrumentation tests
  adb shell am force-stop com.azuredevid.cassy.dev
  set +e
  INSTR_OUT=$(adb shell am instrument -w -e class "com.azuredevid.cassy.ui.CassyUiShowcaseTest" com.azuredevid.cassy.dev.test/androidx.test.runner.AndroidJUnitRunner 2>&1)
  INSTR_RC=$?
  set -e
  echo "[$form_factor] Instrumentation output:"
  echo "$INSTR_OUT"

  SHOWCASE_FILES=$(adb shell run-as com.azuredevid.cassy.dev find files/showcase -type f -name '*.png' 2>/dev/null | tr -d '\r' || true)
  for rel in $SHOWCASE_FILES; do
    rel_clean="${rel#./}"
    rel_clean="${rel_clean#files/showcase/}"
    mkdir -p "$OUT_ROOT/$(dirname "$rel_clean")"
    adb exec-out run-as com.azuredevid.cassy.dev cat "files/showcase/$rel_clean" > "$OUT_ROOT/$rel_clean"
  done

  if [ "$INSTR_RC" -ne 0 ] || echo "$INSTR_OUT" | grep -qE "FAILURES!!!|INSTRUMENTATION_ABORTED|INSTRUMENTATION_FAILED|Process crashed"; then
    echo "[$form_factor] Instrumentation test failed"
    exit 1
  fi
}

adb install -r apps/android/app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb install -r -t apps/android/app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb shell pm grant com.azuredevid.cassy.dev android.permission.CAMERA || true
adb shell pm grant com.azuredevid.cassy.dev android.permission.POST_NOTIFICATIONS || true

echo "=== Running Tablet Landscape Capture Pass (2560x1600 @ 320dpi) ==="
ensure_tablet_landscape
run_capture_pass "tablet"

echo "=== Running Mobile Portrait Capture Pass (1080x2400 @ 420dpi) ==="
ensure_mobile_portrait
run_capture_pass "mobile"

python3 - "$BASE_OUT" <<'PY'
import glob, os, struct, sys
out_root = sys.argv[1]
for path in sorted(glob.glob(f"{out_root}/**/*.png", recursive=True)):
    with open(path, "rb") as f:
        hdr = f.read(24)
    if len(hdr) >= 24 and hdr[:8] == b"\x89PNG\r\n\x1a\n":
        w, h = struct.unpack(">II", hdr[16:24])
    else:
        w, h = 0, 0
    print(f"{path}: {os.path.getsize(path)} bytes ({w}x{h})")
PY
