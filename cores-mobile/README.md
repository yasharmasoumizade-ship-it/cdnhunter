# cores-mobile

Builds ONE gomobile AAR (`libcores.aar`) containing both engines:

- `com.cdnhunter.mihomo.mobile.*`  — the existing mihomo façade (`mihomo-mobile/mobile.go`, copied in by CI)
- `com.cdnhunter.mihomo.libbox.*`  — sing-box's official `experimental/libbox`

Why one AAR: each gomobile AAR embeds its own Go runtime; two of them cannot coexist in one APK.

Pinned in `go.mod`: mihomo v1.19.29, sing-box v1.14.2. Toolchain: SagerNet's gomobile fork v0.1.12 (what
sing-box itself builds libbox with). ABIs: arm64-v8a + armeabi-v7a (the app's ABI splits).

Rollback: set `UNIFIED_AAR: 'false'` in `.github/workflows/build-unified.yml` (restores the mihomo-only build
of `mihomo-mobile/`) — valid only while no Kotlin code references `com.cdnhunter.mihomo.libbox`.
