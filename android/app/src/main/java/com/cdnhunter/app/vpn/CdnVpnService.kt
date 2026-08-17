*** Begin Patch
*** Update File: android/app/src/main/java/com/cdnhunter/app/vpn/CdnVpnService.kt
@@
-                android.util.Log.i("CdnVpn", "Config length: ${config.length}")
-                if (com.cdnhunter.app.BuildConfig.DEBUG) {
-                    android.util.Log.i("CdnVpn", "Config first 200: ${config.take(200)}")
-                    android.util.Log.d("CdnVpn", "Full mihomo config: $config")
-                }
+                // Do NOT log full config contents — it contains secrets (UUID/password/SNI/keys).
+                // Log only non-sensitive metadata (length and a short SHA256) useful for debugging.
+                try {
+                    val cfgHash = java.security.MessageDigest.getInstance("SHA-256")
+                        .digest(config.toByteArray())
+                        .joinToString("") { String.format("%02x", it) }
+                    android.util.Log.i("CdnVpn", "Config length: ${config.length} SHA256:${cfgHash.take(12)}")
+                } catch (e: Exception) {
+                    android.util.Log.i("CdnVpn", "Config length: ${config.length}")
+                }
@@
-                    android.util.Log.i("CdnVpn", "Config first 200: ${config.take(200)}")
-                    android.util.Log.d("CdnVpn", "Full mihomo config: $config")
+                    // In debug builds, emit only a truncated, redacted preview: first 80 and last 32
+                    val previewHead = config.take(80)
+                    val previewTail = config.takeLast(32)
+                    android.util.Log.i("CdnVpn", "Config preview: ${previewHead}...${previewTail}")
+                    // Do NOT log the full config, credentials, or keys.
                 }
*** End Patch
