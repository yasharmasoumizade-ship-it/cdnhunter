package io.github.saeeddev94.xray.activity

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.saeeddev94.xray.helper.CdnScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * CDN clean-IP scanner screen. Programmatic UI (no XML binding) for robustness.
 */
class ScanActivity : AppCompatActivity() {

    private var job: Job? = null
    private lateinit var statusText: TextView
    private lateinit var resultsLayout: LinearLayout
    private lateinit var scanButton: Button
    private var cdn = "Cloudflare"
    private var running = false

    private val bg = Color.parseColor("#0A0E21")
    private val card = Color.parseColor("#1C1C1E")
    private val card2 = Color.parseColor("#2C2C2E")
    private val blue = Color.parseColor("#0A84FF")
    private val green = Color.parseColor("#30D158")
    private val txt = Color.parseColor("#FFFFFF")
    private val txt2 = Color.parseColor("#8E8E93")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "CDN Scanner"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // CDN selector
        val cdnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Cloudflare", "Fastly", "Akamai", "All").forEach { name ->
            val b = Button(this).apply {
                text = name
                textSize = 12f
                setTextColor(if (name == cdn) Color.WHITE else txt2)
                setBackgroundColor(if (name == cdn) blue else card2)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.setMargins(dp(2), 0, dp(2), 0); layoutParams = lp
                setOnClickListener { cdn = name; refreshCdnButtons(cdnRow) }
            }
            cdnRow.addView(b)
        }
        root.addView(cdnRow)

        // Scan button
        scanButton = Button(this).apply {
            text = "Start Scan"
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
            textSize = 16f
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52))
            lp.setMargins(0, dp(12), 0, dp(8)); layoutParams = lp
            setOnClickListener { if (running) stopScan() else startScan() }
        }
        root.addView(scanButton)

        // Status
        statusText = TextView(this).apply {
            text = "Tap Start to find clean IPs"
            setTextColor(txt2); textSize = 13f
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(statusText)

        // Results scroll
        val scroll = ScrollView(this)
        resultsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(resultsLayout)
        root.addView(scroll)

        setContentView(root)
    }

    private fun refreshCdnButtons(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val b = row.getChildAt(i) as Button
            val sel = b.text.toString() == cdn
            b.setTextColor(if (sel) Color.WHITE else txt2)
            b.setBackgroundColor(if (sel) blue else card2)
        }
    }

    private fun startScan() {
        running = true
        scanButton.text = "Stop"
        resultsLayout.removeAllViews()
        statusText.text = "Scanning $cdn..."
        job = lifecycleScope.launch {
            val results = CdnScanner.scan(
                cdn = cdn, maxIps = 800, concurrency = 100, timeoutMs = 1500,
                onProgress = { scanned, found, total ->
                    runOnUiThread { statusText.text = "Scanned $scanned/$total · Found $found clean IPs" }
                },
                onFound = { r -> runOnUiThread { addResultRow(r) } }
            )
            runOnUiThread {
                running = false
                scanButton.text = "Start Scan"
                statusText.text = "Done · ${results.size} clean IPs (tap to copy)"
                // Re-render sorted by speed
                resultsLayout.removeAllViews()
                results.take(50).forEach { addResultRow(it) }
            }
        }
    }

    private fun stopScan() {
        CdnScanner.requestStop()
        job?.cancel()
        running = false
        scanButton.text = "Start Scan"
        statusText.text = "Stopped"
    }

    private fun addResultRow(r: CdnScanner.Result) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(card)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(3), 0, dp(3)); layoutParams = lp
            setOnClickListener { copyIp(r.ip) }
        }
        val ipText = TextView(this).apply {
            text = r.ip; setTextColor(txt); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val msColor = if (r.ms < 300) green else Color.parseColor("#FFD60A")
        val msText = TextView(this).apply {
            text = "${r.ms}ms"; setTextColor(msColor); textSize = 13f
        }
        row.addView(ipText); row.addView(msText)
        resultsLayout.addView(row)
    }

    private fun copyIp(ip: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ip", ip))
        Toast.makeText(this, "$ip copied", Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        CdnScanner.requestStop()
        job?.cancel()
    }
}
