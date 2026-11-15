#!/usr/bin/env bash
set -euo pipefail

mkdir -p artifacts/ui-v2.1.0/showcase

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

  adb exec-out screencap -p > /tmp/orient_check.png
  local dims w h
  dims=$(png_dims /tmp/orient_check.png)
  echo "Native display: $dims"
  read -r w h <<< "$dims"

  if [ "$w" -lt "$h" ]; then
    adb shell settings put system user_rotation 1
    sleep 2
    adb exec-out screencap -p > /tmp/orient_check.png
    dims=$(png_dims /tmp/orient_check.png)
    echo "After user_rotation 1: $dims"
    read -r w h <<< "$dims"
  fi

  if [ "$w" -lt 2000 ] || [ "$w" -lt "$h" ]; then
    adb shell wm density 320
    adb shell wm size 1600x2560
    adb shell settings put system user_rotation 1
    sleep 2
    adb exec-out screencap -p > /tmp/orient_check.png
    dims=$(png_dims /tmp/orient_check.png)
    echo "After wm override: $dims"
    read -r w h <<< "$dims"
    if [ "$w" -lt "$h" ]; then
      adb shell wm size 2560x1600
      adb shell settings put system user_rotation 0
      sleep 2
      adb exec-out screencap -p > /tmp/orient_check.png
      dims=$(png_dims /tmp/orient_check.png)
      echo "After swapped wm override: $dims"
    fi
  fi
}

ensure_tablet_landscape

adb install -r apps/android/app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb install -r -t apps/android/app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
adb shell pm grant com.azuredevid.cassy.dev android.permission.CAMERA || true
adb shell pm grant com.azuredevid.cassy.dev android.permission.POST_NOTIFICATIONS || true

# Cold start live launch and capture splash + StorePairingScreen
adb shell am force-stop com.azuredevid.cassy.dev
adb shell am start -n com.azuredevid.cassy.dev/com.azuredevid.cassy.MainActivity
sleep 0.6
adb exec-out screencap -p > artifacts/ui-v2.1.0/01_os_splash_live.png || true

for i in $(seq 1 30); do
  sleep 2
  adb shell uiautomator dump /data/local/tmp/ui.xml >/dev/null 2>&1 || true
  if adb shell cat /data/local/tmp/ui.xml 2>/dev/null | grep -q "Scan Store Pairing QR"; then
    echo "StorePairingScreen rendered at attempt $i"
    break
  fi
done
sleep 1
adb exec-out screencap -p > artifacts/ui-v2.1.0/00_live_launch_store_pairing.png

adb shell cat /data/local/tmp/ui.xml > artifacts/ui-v2.1.0/live_ui_dump.xml || true
python3 - <<'PY'
import re, subprocess, time, xml.etree.ElementTree as ET

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

xml_file = "artifacts/ui-v2.1.0/live_ui_dump.xml"
scan_btn = find_center(xml_file, "Scan Store Pairing QR")
sign_in = find_center(xml_file, "Sign In & Connect Store")
hid_btn = find_center(xml_file, "USB Scanner")
print("Centers:", {"scan": scan_btn, "sign_in": sign_in, "hid": hid_btn})

# 1) Open Camera QR Scanner first from clean state
if scan_btn:
    tap(scan_btn)
    time.sleep(3)
    with open("artifacts/ui-v2.1.0/05_live_pairing_qr_scanner.png", "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)
    # Close Camera Scanner cleanly via UI button if present, else KEYCODE_BACK
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/data/local/tmp/ui_cam.xml"], check=False)
    subprocess.run(["adb", "pull", "/data/local/tmp/ui_cam.xml", "artifacts/ui-v2.1.0/live_cam_dump.xml"], check=False)
    close_cam = None
    try:
        close_cam = find_center("artifacts/ui-v2.1.0/live_cam_dump.xml", "Close Camera") or find_center("artifacts/ui-v2.1.0/live_cam_dump.xml", "Manual")
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
    with open("artifacts/ui-v2.1.0/03_live_pairing_validation.png", "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)

# 3) Open USB HID / Paste QR Payload dialog
if hid_btn:
    tap(hid_btn)
    time.sleep(2)
    with open("artifacts/ui-v2.1.0/04_live_pairing_hid_dialog.png", "wb") as f:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f, check=True)
PY

rm -f artifacts/ui-v2.1.0/live_ui_dump.xml artifacts/ui-v2.1.0/live_cam_dump.xml

# Run Compose Showcase instrumentation tests
adb shell am force-stop com.azuredevid.cassy.dev
set +e
INSTR_OUT=$(adb shell am instrument -w -e class "com.azuredevid.cassy.ui.CassyUiShowcaseTest" com.azuredevid.cassy.dev.test/androidx.test.runner.AndroidJUnitRunner 2>&1)
INSTR_RC=$?
set -e
echo "$INSTR_OUT"

FILES=$(adb shell run-as com.azuredevid.cassy.dev ls files/showcase 2>/dev/null | tr -d '\r' || true)
for f in $FILES; do
  adb exec-out run-as com.azuredevid.cassy.dev cat "files/showcase/$f" > "artifacts/ui-v2.1.0/showcase/$f"
done

cp -f artifacts/ui-v2.1.0/showcase/startup_splash.png artifacts/ui-v2.1.0/01_startup_splash.png || true
cp -f artifacts/ui-v2.1.0/showcase/store_pairing.png artifacts/ui-v2.1.0/02_pairing_default_online.png || true
cp -f artifacts/ui-v2.1.0/showcase/store_pairing_validation_offline.png artifacts/ui-v2.1.0/03_pairing_validation_offline.png || true
cp -f artifacts/ui-v2.1.0/showcase/store_pairing_hid_dialog.png artifacts/ui-v2.1.0/04_pairing_hid_dialog.png || true
cp -f artifacts/ui-v2.1.0/showcase/store_pairing_scanner.png artifacts/ui-v2.1.0/05_pairing_qr_scanner.png || true

python3 - <<'PY'
import glob, os, struct
for path in sorted(glob.glob("artifacts/ui-v2.1.0/**/*.png", recursive=True)):
    with open(path, "rb") as f:
        hdr = f.read(24)
    if len(hdr) >= 24 and hdr[:8] == b"\x89PNG\r\n\x1a\n":
        w, h = struct.unpack(">II", hdr[16:24])
    else:
        w, h = 0, 0
    print(f"{path}: {os.path.getsize(path)} bytes ({w}x{h})")
PY

if [ "$INSTR_RC" -ne 0 ] || echo "$INSTR_OUT" | grep -qE "FAILURES!!!|INSTRUMENTATION_ABORTED|INSTRUMENTATION_FAILED|Process crashed"; then
  echo "Instrumentation test failed"
  exit 1
fi
