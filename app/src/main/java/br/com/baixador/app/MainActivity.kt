package br.com.baixador.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat

private val Ink = Color(0xFF000000)
private val Panel = Color(0xFF171717)
private val Stroke = Color(0xFF555555)
private val Muted = Color(0xFFB6B6B6)
private val Light = Color(0xFFF5F5F5)
private val AppColors = darkColorScheme(primary = Light, onPrimary = Ink, background = Ink, onBackground = Light, surface = Panel, onSurface = Light, outline = Stroke, error = Color(0xFFFFB4AB))

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf("")
    private val sharedError = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent { MaterialTheme(colorScheme = AppColors) { Screen() } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val value = DownloadOptions.sharedUrl(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        if (value == null) sharedError.intValue = R.string.shared_link_invalid
        else { incoming.value = value; sharedError.intValue = 0 }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Screen() {
        var link by rememberSaveable { mutableStateOf(incoming.value) }
        var type by rememberSaveable { mutableStateOf("video") }
        var quality by rememberSaveable { mutableStateOf(DownloadOptions.BEST) }
        var expanded by remember { mutableStateOf(false) }
        var folder by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getString("folder", null)) }
        val status by DownloadState.status.collectAsState()
        val progress by DownloadState.progress.collectAsState()
        val result by DownloadState.result.collectAsState()
        var busy by remember { mutableStateOf(DownloadState.busy.get()) }
        val clipboard = LocalClipboardManager.current
        LaunchedEffect(incoming.value) { if (incoming.value.isNotEmpty()) { link = incoming.value; incoming.value = "" } }
        LaunchedEffect(status) { busy = DownloadState.busy.get() }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                folder = uri.toString(); getPreferences(MODE_PRIVATE).edit().putString("folder", folder).apply()
            } catch (_: SecurityException) { DownloadState.status.value = DisplayMessage(R.string.folder_access_failed) }
        }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        Surface(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), color = Ink) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
                Image(painter = painterResource(R.drawable.hero_kurofetch), contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f), color = Stroke)
                        Text("KUROFETCH", modifier = Modifier.padding(horizontal = 12.dp), color = Light,
                            fontFamily = FontFamily.Serif, fontSize = 30.sp, letterSpacing = 2.sp, maxLines = 1)
                        HorizontalDivider(Modifier.weight(1f), color = Stroke)
                    }
                    if (sharedError.intValue != 0) Text(stringResource(sharedError.intValue), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    FieldTitle(stringResource(R.string.paste_link))
                    OutlinedTextField(value = link, onValueChange = { link = it; sharedError.intValue = 0 },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://", color = Muted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 3,
                        trailingIcon = { TextButton(onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isNotBlank()) { link = text; sharedError.intValue = 0 }
                            else sharedError.intValue = R.string.clipboard_empty
                        }) { Text(stringResource(R.string.paste), fontSize = 11.sp, color = Light) } },
                        shape = RoundedCornerShape(12.dp), colors = textFieldColors())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeChoice(stringResource(R.string.video), type == "video", Modifier.weight(1f)) { type = "video"; quality = DownloadOptions.BEST }
                        ModeChoice(stringResource(R.string.audio), type == "audio", Modifier.weight(1f)) { type = "audio"; quality = DownloadOptions.BEST }
                    }
                    FieldTitle(stringResource(R.string.quality))
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(value = qualityLabel(quality), onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = textFieldColors())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            (if (type == "video") DownloadOptions.video else DownloadOptions.audio).forEach { option ->
                                DropdownMenuItem(text = { Text(qualityLabel(option)) }, onClick = { quality = option; expanded = false })
                            }
                        }
                    }
                    FieldTitle(stringResource(R.string.save_to))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Stroke),
                            contentPadding = PaddingValues(horizontal = 12.dp)) {
                            Text(stringResource(if (folder == null) R.string.downloads_menu else R.string.chosen_folder_menu), color = Light, fontSize = 14.sp, maxLines = 1)
                        }
                        if (folder != null) TextButton(onClick = { folder = null; getPreferences(MODE_PRIVATE).edit().remove("folder").apply() }) { Text(stringResource(R.string.downloads), color = Light, fontSize = 12.sp) }
                    }
                    Button(enabled = !busy, onClick = {
                        val url = DownloadOptions.sharedUrl(link.trim())
                        if (url == null) sharedError.intValue = R.string.link_invalid
                        else if (DownloadState.busy.compareAndSet(false, true)) {
                            sharedError.intValue = 0; DownloadState.progress.value = null; DownloadState.result.value = null
                            DownloadState.status.value = DisplayMessage(R.string.download_preparing)
                            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            try {
                                ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, DownloadService::class.java).apply {
                                    putExtra("url", url); putExtra("audio", type == "audio"); putExtra("quality", quality); putExtra("folder", folder)
                                })
                            } catch (e: Exception) { DownloadState.busy.set(false); DownloadState.status.value = DisplayMessage(R.string.download_start_failed) }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Light, contentColor = Ink, disabledContainerColor = Color(0xFF555555), disabledContentColor = Light)) {
                        Text(stringResource(if (busy) R.string.downloading_action else R.string.download_action), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontSize = 16.sp)
                    }
                    if (busy || result != null || status != null) {
                        Column(Modifier.fillMaxWidth().border(1.dp, Stroke, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            status?.let { Text(stringResource(it.resource, *it.arguments.toTypedArray()), color = Light, style = MaterialTheme.typography.bodySmall) }
                            if (busy) {
                                if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Light, trackColor = Stroke)
                                else LinearProgressIndicator(progress = { (progress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = Light, trackColor = Stroke)
                            }
                            if (result != null) TextButton(onClick = { openResult(Uri.parse(result)) }) { Text(stringResource(R.string.open_file), color = Light) }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }

    @Composable private fun qualityLabel(quality: String): String = if (quality == DownloadOptions.BEST) stringResource(R.string.quality_best) else quality

    @Composable private fun FieldTitle(text: String) { Text(text, color = Light, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    @Composable private fun ModeChoice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Box(modifier.height(52.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0xFF262626) else Ink)
            .border(1.dp, if (selected) Light else Stroke, RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) Light else Muted, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
    @Composable private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Light, unfocusedTextColor = Light, focusedContainerColor = Panel, unfocusedContainerColor = Panel,
        focusedBorderColor = Light, unfocusedBorderColor = Stroke, cursorColor = Light)

    private fun openResult(uri: Uri) {
        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri) ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (_: Exception) { DownloadState.status.value = DisplayMessage(R.string.file_open_failed) }
    }
}
