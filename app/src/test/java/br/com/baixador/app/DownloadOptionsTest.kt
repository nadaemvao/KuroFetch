package br.com.baixador.app

import org.junit.Assert.*
import org.junit.Test

class DownloadOptionsTest {
    @Test fun linkFromShare() {
        assertEquals("https://example.com/watch?v=abc", DownloadOptions.sharedUrl("Veja: https://example.com/watch?v=abc"))
        assertNull(DownloadOptions.sharedUrl("não tem link"))
    }
    @Test fun videoLimits() {
        assertEquals("bv*+ba/b", DownloadOptions.selector(DownloadOptions.BEST))
        assertEquals("bv*[height<=?720]+ba/b[height<=?720]", DownloadOptions.selector("720p"))
    }
    @Test fun audioBitrate() {
        assertEquals("0", DownloadOptions.bitrate(DownloadOptions.BEST))
        assertEquals("192K", DownloadOptions.bitrate("192 kbps"))
    }
}
