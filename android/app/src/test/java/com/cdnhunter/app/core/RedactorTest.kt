package com.cdnhunter.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {
    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    @Test fun shareLinksAreRemovedWholesale() {
        val out = Redactor.redact("failed: vless://$uuid@example.com:443?security=reality&pbk=AbCdEf#name rest")
        assertFalse(out.contains(uuid))
        assertFalse(out.contains("example.com"))
        assertTrue(out.contains("vless://<redacted>"))
        assertTrue(out.endsWith("rest"))
    }

    @Test fun bareUuidsAndKeyValuePairsAreMasked() {
        val out = Redactor.redact("""user $uuid password=hunter2 "public-key": "xyz123" sid: 6ba85179e30d4fc2""")
        assertFalse(out.contains(uuid)); assertFalse(out.contains("hunter2"))
        assertFalse(out.contains("xyz123")); assertFalse(out.contains("6ba85179e30d4fc2"))
    }

    @Test fun longOpaqueTokensAreMasked() {
        val token = "A".repeat(20) + "b".repeat(30)
        assertFalse(Redactor.redact("key=$token").contains(token))
    }

    @Test fun ordinaryDiagnosticTextSurvives() {
        val msg = "dial tcp 203.0.113.7:443: i/o timeout"
        assertTrue(Redactor.redact(msg) == msg)
    }

    @Test fun errorsAndLogLinesAreRedactedAtConstruction() {
        val err = ConnectionError.CoreError(ErrorCode.CORE_START_FAILED, "parse error near uuid: $uuid")
        assertFalse(err.technical.contains(uuid))
        ConnLog.clear()
        ConnLog.i("c1", Stage.CONFIG_LOADED, "loaded vmess://abcdef and $uuid")
        assertFalse(ConnLog.dump().contains(uuid))
        assertFalse(ConnLog.dump().contains("abcdef"))
    }

    @Test fun nullAndEmptyAreSafe() {
        assertTrue(Redactor.redact(null) == "")
        assertTrue(Redactor.redact("") == "")
    }
}
