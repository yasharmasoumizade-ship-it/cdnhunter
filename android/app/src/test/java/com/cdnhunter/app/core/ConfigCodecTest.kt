package com.cdnhunter.app.core

import com.cdnhunter.app.vpn.ConfigUriParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigCodecTest {

    private fun sample(name: String = "My Server 🇩🇪 #1"): InternalConnectionConfig = InternalConnectionConfig.fromProxyMap(
        linkedMapOf(
            "type" to "vless", "server" to "a.example.com", "port" to 443, "uuid" to "b831381d-6324-4d53-ad4f-8cda48b30811",
            "tls" to true, "alpn" to listOf("h2", "http/1.1"), "ws-opts" to linkedMapOf("path" to "/p", "headers" to linkedMapOf("Host" to "h")),
        ), name,
    )!!

    @Test fun storedEntryIsASingleLineWithNoSeparatorCharacters() {
        val uri = ConfigCodec.encodeStored(sample())
        assertTrue(uri.startsWith("cdnjson://"))
        assertFalse(uri.contains('\n')); assertFalse(uri.contains('\r')); assertFalse(uri.contains('\u0001')); assertFalse(uri.contains(' '))
    }

    @Test fun roundTripPreservesEverythingIncludingTypes() {
        val c = sample()
        val back = ConfigCodec.decodeStored(ConfigCodec.encodeStored(c))!!
        assertEquals(c.proxy, back)
        assertEquals(443, back["port"]); assertEquals(true, back["tls"])
        assertEquals("My Server 🇩🇪 #1", ConfigCodec.storedName(ConfigCodec.encodeStored(c)))
    }

    @Test fun theUriParserUnderstandsStoredEntriesLikeAnyOtherLink() {
        val uri = ConfigCodec.encodeStored(sample())
        val parsed = ConfigUriParser.parseToProxy(uri)!!
        assertEquals("a.example.com", parsed["server"])
    }

    @Test fun corruptStoredEntriesFailSoftly() {
        for (bad in listOf("cdnjson://", "cdnjson://!!!notbase64!!!", "cdnjson://bm90IGpzb24", "cdnjson://e30" /* {} */))
            assertTrue("input $bad", ConfigCodec.decodeStored(bad) == null || ConfigCodec.decodeStored(bad)!!.isEmpty())
        assertTrue(ConfigUriParser.parse("cdnjson://!!!") is ConfigUriParser.UriParseResult.Failure)
    }

    @Test fun base64EncodeDecodeRoundTripsAllLengths() {
        val rnd = java.util.Random(7)
        for (n in 0..200) {
            val data = ByteArray(n).also { rnd.nextBytes(it) }
            assertArrayEquals(data, ConfigCodec.base64Decode(ConfigCodec.base64UrlEncode(data)))
        }
    }

    @Test fun base64DecodeIsLenientLikeRealSubscriptions() {
        val expected = "hello world?>>".toByteArray()
        val std = java.util.Base64.getEncoder().encodeToString(expected)
        val url = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(expected)
        val mime = std.chunked(4).joinToString("\r\n")
        for (v in listOf(std, url, mime, " $std \n")) assertArrayEquals(expected, ConfigCodec.base64Decode(v))
        assertNull(ConfigCodec.base64Decode("not*base64"))
    }
}
