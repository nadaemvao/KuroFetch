package br.com.baixador.app

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object DownloadState {
    val busy = AtomicBoolean(false)
    val status = MutableStateFlow("Cole ou compartilhe um link para começar.")
    val progress = MutableStateFlow<Int?>(null)
    val result = MutableStateFlow<String?>(null)
}

object DownloadOptions {
    val video = listOf("Melhor disponível", "1080p", "720p", "480p")
    val audio = listOf("Melhor disponível", "320 kbps", "192 kbps", "128 kbps")
    private val urlRegex = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
    fun sharedUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
    fun selector(quality: String): String {
        val limit = if (quality == video[0]) "" else "[height<=?${quality.removeSuffix("p")}]"
        return "bv*$limit+ba/b$limit"
    }
    fun bitrate(quality: String) = if (quality == audio[0]) "0" else quality.substringBefore(' ') + "K"
}
