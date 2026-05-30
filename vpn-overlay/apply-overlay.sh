#!/usr/bin/env bash
# Applies CDN Hunter customizations onto a cloned SaeedDev94/Xray checkout.
# Usage: ./apply-overlay.sh <path-to-app-src> <path-to-overlay-dir>
set -e

APP="$1"
OVR="$2"
PKG="$APP/app/src/main/java/io/github/saeeddev94/xray"

echo "==> Copying scanner + AutoIP files"
cp "$OVR/CdnScanner.kt" "$PKG/helper/CdnScanner.kt"
cp "$OVR/AutoIpHelper.kt" "$PKG/helper/AutoIpHelper.kt"
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

echo "==> Injecting geoip/geosite asset-copy into MainActivity.onCreate"
python3 - "$PKG/activity/MainActivity.kt" <<'PY'
import sys
f = sys.argv[1]
s = open(f).read()
if "geoip.dat" not in s:
    anchor = "    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n"
    inj = anchor + ('        listOf("geoip.dat", "geosite.dat").forEach { n ->\n'
                    '            val df = java.io.File(filesDir, n)\n'
                    '            if (!df.exists()) try { assets.open(n).use { i -> df.outputStream().use { o -> i.copyTo(o) } } } catch (e: Exception) {}\n'
                    '        }\n')
    s2 = s.replace(anchor, inj, 1)
    if s2 != s:
        open(f, "w").write(s2)
        print("asset-copy injected")
    else:
        print("WARN: onCreate anchor not found")
PY

echo "==> Injecting TLS fragmentation into LinkHelper.outbounds()"
python3 - "$PKG/helper/LinkHelper.kt" <<'PY'
import sys
f = sys.argv[1]
s = open(f).read()
if "fragment" not in s:
    # 1) add dialerProxy to proxy outbound right after tag is set
    a1 = '        proxy.put("tag", "proxy")\n'
    inj1 = a1 + ('        run {\n'
                 '            val _ss = proxy.optJSONObject("streamSettings") ?: JSONObject()\n'
                 '            val _so = _ss.optJSONObject("sockopt") ?: JSONObject()\n'
                 '            _so.put("dialerProxy", "fragment")\n'
                 '            _ss.put("sockopt", _so)\n'
                 '            proxy.put("streamSettings", _ss)\n'
                 '        }\n')
    s = s.replace(a1, inj1, 1)
    # 2) add fragment outbound right after proxy is added to list
    a2 = '        outbounds.put(proxy)\n'
    inj2 = a2 + ('        val fragment = JSONObject()\n'
                 '        fragment.put("protocol", "freedom")\n'
                 '        fragment.put("tag", "fragment")\n'
                 '        val fragSettings = JSONObject()\n'
                 '        fragSettings.put("domainStrategy", "AsIs")\n'
                 '        val frag = JSONObject()\n'
                 '        frag.put("packets", "tlshello")\n'
                 '        frag.put("length", "100-200")\n'
                 '        frag.put("interval", "10-20")\n'
                 '        fragSettings.put("fragment", frag)\n'
                 '        fragment.put("settings", fragSettings)\n'
                 '        val fragSockopt = JSONObject()\n'
                 '        fragSockopt.put("TcpNoDelay", true)\n'
                 '        val fragSs = JSONObject()\n'
                 '        fragSs.put("sockopt", fragSockopt)\n'
                 '        fragment.put("streamSettings", fragSs)\n'
                 '        outbounds.put(fragment)\n')
    s = s.replace(a2, inj2, 1)
    open(f, "w").write(s)
    print("fragmentation injected")
else:
    print("fragment already present")
PY

echo "==> Injecting Auto-IP into TProxyService (scan before connect)"
python3 - "$PKG/service/TProxyService.kt" <<'PY'
import sys
f = sys.argv[1]
s = open(f).read()
if "AutoIpHelper" not in s:
    # Add import
    if "import io.github.saeeddev94.xray.helper.AutoIpHelper" not in s:
        s = s.replace("import io.github.saeeddev94.xray.helper.", "import io.github.saeeddev94.xray.helper.AutoIpHelper\nimport io.github.saeeddev94.xray.helper.", 1)
    # Find where config file is written/used before xray start
    # In TProxyService, config is written to settings.xrayConfig() then xray starts
    # We patch: if AutoIP enabled, read config, patch IP, rewrite file
    anchor = "        XrayCore.start(config.dir, config.file)"
    if anchor not in s:
        anchor = "XrayCore.start("
        idx = s.find(anchor)
        if idx > 0:
            line_start = s.rfind("\n", 0, idx) + 1
            anchor = s[line_start:s.find("\n", idx)+1]
    
    inj = ('        // Auto-IP: patch config with best scanned IP before starting\n'
           '        if (AutoIpHelper.isAutoEnabled(applicationContext)) {\n'
           '            val cfgFile = java.io.File(filesDir, "config.json")\n'
           '            if (cfgFile.exists()) {\n'
           '                val lastIp = AutoIpHelper.getLastIp(applicationContext)\n'
           '                if (lastIp.isNotBlank()) {\n'
           '                    val patched = AutoIpHelper.patchConfigWithIp(cfgFile.readText(), lastIp)\n'
           '                    cfgFile.writeText(patched)\n'
           '                }\n'
           '            }\n'
           '        }\n')
    if "AutoIpHelper.isAutoEnabled" not in s:
        s = s.replace(anchor, inj + anchor, 1)
    open(f, "w").write(s)
    print("Auto-IP injected into TProxyService")
else:
    print("Auto-IP already present")
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
