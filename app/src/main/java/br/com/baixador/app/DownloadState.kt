package br.com.baixador.app

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class DisplayMessage(@StringRes val resource: Int, val arguments: List<Any> = emptyList())

object DownloadState {
    val busy = AtomicBoolean(false)
    val status = MutableStateFlow<DisplayMessage?>(null)
    val progress = MutableStateFlow<Int?>(null)
    val result = MutableStateFlow<String?>(null)
}

object DownloadOptions {
    const val BEST = "best"
    val video = listOf(BEST, "1080p", "720p", "480p")
    val audio = listOf(BEST, "320 kbps", "192 kbps", "128 kbps")
    private val urlRegex = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
    fun sharedUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
    fun selector(quality: String): String {
        val limit = if (quality == BEST) "" else "[height<=?${quality.removeSuffix("p")}]"
        return "bv*$limit+ba/b$limit"
    }
    fun bitrate(quality: String) = if (quality == BEST) "0" else quality.substringBefore(' ') + "K"
}
