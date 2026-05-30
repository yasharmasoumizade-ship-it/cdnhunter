#!/usr/bin/env bash
# Applies CDN Hunter customizations onto a cloned SaeedDev94/Xray checkout.
# Usage: ./apply-overlay.sh <path-to-app-src> <path-to-overlay-dir>
set -e

APP="$1"
OVR="$2"
PKG="$APP/app/src/main/java/io/github/saeeddev94/xray"

echo "==> Copying scanner files"
cp "$OVR/CdnScanner.kt" "$PKG/helper/CdnScanner.kt"
cp "$OVR/ScanActivity.kt" "$PKG/activity/ScanActivity.kt"

echo "==> Injecting into AndroidManifest"
python3 - "$APP/app/src/main/AndroidManifest.xml" <<'PY'
import sys, re
f = sys.argv[1]
s = open(f).read()
if "ScanActivity" not in s:
    inj = '        <activity\n            android:name=".activity.ScanActivity"\n            android:parentActivityName=".activity.MainActivity" />\n    </application>'
    s = s.replace("    </application>", inj, 1)
    open(f, "w").write(s)
    print("manifest patched")
PY

echo "==> Injecting drawer menu item"
python3 - "$APP/app/src/main/res/menu/menu_drawer.xml" <<'PY'
import sys
f = sys.argv[1]
s = open(f).read()
if "cdnScanner" not in s:
    item = ('        <item\n'
            '            android:id="@+id/cdnScanner"\n'
            '            android:icon="@drawable/baseline_alt_route"\n'
            '            android:title="@string/cdnScanner" />\n')
    # inject before the LAST </group>
    idx = s.rfind("</group>")
    s = s[:idx] + item + s[idx:]
    open(f, "w").write(s)
    print("menu patched")
PY

echo "==> Injecting nav handler in MainActivity"
python3 - "$PKG/activity/MainActivity.kt" <<'PY'
import sys, re
f = sys.argv[1]
s = open(f).read()
if "R.id.cdnScanner" not in s:
    # add after the R.id.assets -> Intent(...) line
    pat = re.compile(r'(R\.id\.assets -> Intent\(applicationContext, AssetsActivity::class\.java\))')
    repl = r'R.id.cdnScanner -> Intent(applicationContext, ScanActivity::class.java)\n            \1'
    s2 = pat.sub(repl, s, count=1)
    if s2 != s:
        open(f, "w").write(s2)
        print("MainActivity patched")
    else:
        print("WARN: nav anchor not found")
PY

echo "==> Patching strings.xml (appName + cdnScanner)"
python3 - "$APP/app/src/main/res/values/strings.xml" <<'PY'
import sys, re
f = sys.argv[1]
s = open(f).read()
s = re.sub(r'<string name="appName">[^<]*</string>', '<string name="appName">CDN Hunter</string>', s)
if "cdnScanner" not in s:
    s = s.replace("</resources>", '    <string name="cdnScanner">CDN Scanner</string>\n</resources>')
open(f, "w").write(s)
print("strings patched")
PY

echo "==> Overlay applied successfully"
