package com.cdnhunter.app.vpn

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * The one place this app opens a SharedPreferences file.
 *
 * Both files it fronts hold things worth encrypting at rest: `cdnhunter_vpn` keeps
 * every saved server as its full URI (address plus UUID/password) and `cdnhunter_settings`
 * keeps the subscription URLs, each of which is on its own enough to fetch a whole
 * server list. Until now both were plain XML in the app's data directory, readable by
 * anything with a root shell or a backup of that directory.
 *
 * [get] returns an [EncryptedSharedPreferences] under a parallel file name, migrating
 * whatever the plaintext file still holds on the first call after the update. The
 * migration is deliberately copy → verify → mark → clear, in that order:
 *
 *  - Plaintext data is never deleted until the encrypted copy has been read back and
 *    compared entry by entry, so a failure partway through cannot lose saved servers.
 *  - Nothing is marked migrated until that verification passes, so a process death
 *    mid-migration just means the next launch tries again.
 *  - Re-running is a no-op in the sense that matters: the copy writes the same keys to
 *    the same values, so a partially-completed run followed by a full one converges on
 *    exactly the source data rather than duplicating or interleaving it.
 *
 * If anything at all goes wrong — a device whose keystore cannot generate or unwrap the
 * master key is the realistic case — [get] hands back the plaintext file instead. The app
 * keeps working on unencrypted prefs and retries the migration on the next launch, which
 * is the right trade here: a VPN client that loses its server list is broken, one that
 * stores it in the clear for another launch is merely no worse than the previous release.
 */
object SecurePrefs {

    private const val TAG = "SecurePrefs"

    /** Saved configs, active config id, connection bookkeeping. */
    const val VPN = "cdnhunter_vpn"

    /** [AppSettings]' own file — subscriptions, DNS, split tunneling, appearance. */
    const val SETTINGS = "cdnhunter_settings"

    /**
     * Written into the *encrypted* file, not the plaintext one, and only after the copy
     * has been verified. Keeping the marker on the destination side means a half-written
     * destination is always detectable: no marker, so migrate again.
     */
    private const val KEY_MIGRATED = "__secureprefs_migrated_v1"

    /** Encrypted counterpart of a plaintext file name. */
    private fun encryptedName(name: String) = name + "_enc"

    /**
     * One instance per file for the lifetime of the process. [EncryptedSharedPreferences]
     * is more expensive to open than the plain kind (it unwraps keys through the
     * keystore), and holding one instance also means every reader sees the same
     * in-memory state — the same guarantee `getSharedPreferences` gives.
     */
    private val cache = HashMap<String, SharedPreferences>()

    @Synchronized
    fun get(ctx: Context, name: String): SharedPreferences {
        cache[name]?.let { return it }
        val plain = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)
        val prefs = try {
            val encrypted = open(ctx, name)
            if (!encrypted.getBoolean(KEY_MIGRATED, false)) {
                migrate(plain, encrypted, name)
            } else if (plain.all.isNotEmpty()) {
                // Migrated on an earlier launch, but the process died between marking it
                // and clearing the source. Finish the job.
                clearPlaintext(plain, name)
            }
            // Only trust the encrypted file once it says it holds the data.
            if (encrypted.getBoolean(KEY_MIGRATED, false)) encrypted else plain
        } catch (e: Throwable) {
            // Throwable, not Exception: a keystore that cannot satisfy the master key
            // spec surfaces as errors outside the Exception hierarchy on some OEM
            // builds, and falling back is always better than taking the app down.
            debug { Log.w(TAG, "Encrypted prefs unavailable for $name; using plaintext", e) }
            plain
        }
        cache[name] = prefs
        return prefs
    }

    /** Convenience for the two known files. */
    fun vpn(ctx: Context): SharedPreferences = get(ctx, VPN)

    fun settings(ctx: Context): SharedPreferences = get(ctx, SETTINGS)

    private fun open(ctx: Context, name: String): SharedPreferences {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            encryptedName(name),
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Copies every entry of [plain] into [encrypted], reads them all back, and only then
     * records the marker and clears the source. Uses `commit()` throughout, never
     * `apply()`: the whole point is to know whether the write reached disk before
     * deciding to delete the original.
     */
    private fun migrate(plain: SharedPreferences, encrypted: SharedPreferences, name: String) {
        val source = plain.all
        if (source.isEmpty()) {
            // Fresh install, or an already-cleared source. Nothing to copy, but the
            // marker still needs setting so later launches skip straight through.
            encrypted.edit().putBoolean(KEY_MIGRATED, true).commit()
            return
        }

        val editor = encrypted.edit()
        for ((k, v) in source) {
            if (k == KEY_MIGRATED) continue // never let a stale marker ride along
            when (v) {
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
                is Long -> editor.putLong(k, v)
                is Float -> editor.putFloat(k, v)
                is String -> editor.putString(k, v)
                is Set<*> -> editor.putStringSet(k, v.filterIsInstance<String>().toSet())
                // A null value, or a type SharedPreferences cannot hold, is not something
                // this app writes; skipping it here would make the verification below
                // fail loudly rather than silently dropping it.
                else -> debug { Log.w(TAG, "Skipping unsupported pref type for key in $name") }
            }
        }
        if (!editor.commit()) {
            debug { Log.w(TAG, "Encrypted write failed for $name; keeping plaintext") }
            return
        }

        if (!verify(source, encrypted, name)) return

        if (!encrypted.edit().putBoolean(KEY_MIGRATED, true).commit()) {
            // Data is there but unmarked: next launch re-copies from the intact source
            // and tries the marker again. Costs one redundant copy, loses nothing.
            debug { Log.w(TAG, "Could not mark $name migrated; will retry next launch") }
            return
        }

        clearPlaintext(plain, name)
        debug { Log.d(TAG, "Migrated ${source.size} entries of $name to encrypted prefs") }
    }

    /**
     * Every source entry must be present in [encrypted] with an equal value. Sets are
     * compared as sets because SharedPreferences makes no ordering promise about them.
     */
    private fun verify(
        source: Map<String, Any?>,
        encrypted: SharedPreferences,
        name: String,
    ): Boolean {
        val readBack = encrypted.all
        for ((k, expected) in source) {
            if (k == KEY_MIGRATED) continue
            val actual = readBack[k]
            val same = when {
                expected is Set<*> && actual is Set<*> -> expected.toSet() == actual.toSet()
                else -> expected == actual
            }
            if (!same) {
                debug { Log.w(TAG, "Verification failed for a key in $name; keeping plaintext") }
                return false
            }
        }
        return true
    }

    private fun clearPlaintext(plain: SharedPreferences, name: String) {
        if (!plain.edit().clear().commit()) {
            debug { Log.w(TAG, "Could not clear plaintext $name; encrypted copy is authoritative") }
        }
    }

    /**
     * Diagnostics here name pref files and entry counts, never values — and even that
     * only in a debug build, matching how the rest of this package treats anything
     * derived from a config.
     */
    private inline fun debug(block: () -> Unit) {
        if (com.cdnhunter.app.BuildConfig.DEBUG) block()
    }
}
