package com.cdnhunter.app

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a global uncaught-exception handler that writes the full crash
 * stack trace to a file before letting the app die normally. This lets us
 * retrieve real crash details from a device we can't attach a debugger to —
 * open Settings -> "Last crash log" and copy it.
 */
class CdnHunterApp : Application() {
    companion object {
        const val CRASH_LOG_FILE = "last_crash.txt"
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val report = buildString {
                    appendLine("CDN Hunter crash report")
                    appendLine("Time: $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("----------------------------------------")
                    append(sw.toString())
                }
                File(filesDir, CRASH_LOG_FILE).writeText(report)
            } catch (e: Exception) {
                // If logging itself fails, don't block the crash from proceeding.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
