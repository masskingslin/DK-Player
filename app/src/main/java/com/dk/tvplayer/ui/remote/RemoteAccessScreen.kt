package com.dk.tvplayer.ui.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.tvplayer.remote.RemoteAccessBridge
import com.dk.tvplayer.remote.RemoteAccessConfig
import com.dk.tvplayer.remote.RemoteAccessService
import com.dk.tvplayer.remote.RemoteAccessState
import com.dk.tvplayer.remote.RemoteContent
import kotlinx.coroutines.launch

/**
 * Settings for the built-in remote access server (see RemoteAccessServer): a switch,
 * live status with the one-time sign-in code, and per-feature permissions. The first
 * time it's turned on, a short onboarding explains what it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteAccessScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val enabled by RemoteAccessConfig.enabled.collectAsState()
    val onboardingDone by RemoteAccessConfig.onboardingDone.collectAsState()
    val content by RemoteAccessConfig.content.collectAsState()
    val running by RemoteAccessState.running.collectAsState()
    val addresses by RemoteAccessState.addresses.collectAsState()
    val otp by RemoteAccessState.otp.collectAsState()
    val sessions by RemoteAccessState.sessions.collectAsState()
    val fingerprint by RemoteAccessState.fingerprint.collectAsState()
    val error by RemoteAccessState.error.collectAsState()

    var showOnboarding by remember { mutableStateOf(false) }
    var mediaExpanded by remember { mutableStateOf(false) }

    fun turnOn() {
        RemoteAccessState.setError(null)
        RemoteAccessConfig.setEnabled(true)
        RemoteAccessService.start(context)
    }

    if (showOnboarding) {
        RemoteAccessOnboarding(
            content = content,
            onToggle = { item, on -> RemoteAccessConfig.setAllowed(item, on) },
            onFinished = {
                RemoteAccessConfig.setOnboardingDone()
                showOnboarding = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote access") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showOnboarding = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About remote access")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable remote access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Control playback and browse this phone from a browser on the same network",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (on) {
                            if (!onboardingDone) showOnboarding = true
                            turnOn()
                        } else {
                            RemoteAccessConfig.setEnabled(false)
                            RemoteAccessService.stop(context)
                        }
                    }
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            if (enabled && running) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server status", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Open this address in a browser on the same Wi-Fi:", style = MaterialTheme.typography.bodySmall)
                        if (addresses.isEmpty()) {
                            Text("No network address found — connect to Wi-Fi.", color = MaterialTheme.colorScheme.error)
                        }
                        addresses.forEach { address ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    address,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { copy(context, address) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("One-time code", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                otp.chunked(3).joinToString(" "),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { RemoteAccessBridge.server?.rotateOtp() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "New code")
                            }
                        }
                        Text(
                            "Enter this in the browser to sign in. It works once, then a new code is made.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (sessions == 1) "1 signed-in browser" else "$sessions signed-in browsers",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            if (sessions > 0) {
                                TextButton(onClick = { RemoteAccessBridge.server?.signOutAll() }) { Text("Sign out all") }
                            }
                        }
                        if (fingerprint.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The browser will warn about the certificate (it's self-signed). Its SHA-256 fingerprint:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(fingerprint, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }
                }
            } else if (enabled) {
                Text("Starting…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Content", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

            val mediaItems = listOf(RemoteContent.VIDEO, RemoteContent.AUDIO, RemoteContent.PLAYLISTS, RemoteContent.SEARCH)
            Column(modifier = Modifier.fillMaxWidth().clickable { mediaExpanded = !mediaExpanded }.padding(vertical = 12.dp)) {
                Text("Media library content", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enabled: " + (mediaItems.filter { content[it] == true }.joinToString(" · ") { it.label }.ifEmpty { "-" }) +
                        "\nDisabled: " + (mediaItems.filter { content[it] != true }.joinToString(" · ") { it.label }.ifEmpty { "-" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mediaExpanded) {
                mediaItems.forEach { item -> ContentCheckRow(item, content[item] == true, indent = true) }
            }
            listOf(RemoteContent.FILE_BROWSER, RemoteContent.HISTORY, RemoteContent.CONTROL, RemoteContent.LOGS).forEach { item ->
                ContentCheckRow(item, content[item] == true)
            }
            Text(
                "File browser also covers downloading files. Changes apply immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun ContentCheckRow(item: RemoteContent, checked: Boolean, indent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { RemoteAccessConfig.setAllowed(item, !checked) }
            .padding(start = if (indent) 24.dp else 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(item.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = { RemoteAccessConfig.setAllowed(item, it) })
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Remote access address", text))
    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
}

private data class OnboardingPage(val icon: ImageVector, val title: String, val text: String)

/** Five-step intro (what it is, control, encryption, authentication, content access). */
@Composable
private fun RemoteAccessOnboarding(
    content: Map<RemoteContent, Boolean>,
    onToggle: (RemoteContent, Boolean) -> Unit,
    onFinished: () -> Unit
) {
    val pages = listOf(
        OnboardingPage(Icons.Default.Wifi, "Remote access",
            "Welcome to DK Player's remote access. By enabling it you will be able to control this device's playback and more from a browser."),
        OnboardingPage(Icons.Default.PhoneAndroid, "Control your device",
            "Control your device playback, browse the media library and download files from any browser on your network."),
        OnboardingPage(Icons.Default.Lock, "Data encryption",
            "The remote access is encrypted (HTTPS) to prevent your data from leaking. Your browser will warn that the certificate is self-signed — that's expected."),
        OnboardingPage(Icons.Default.VpnKey, "Authentication",
            "With one-time password authentication, unwanted access to your data is prevented. Each sign-in needs a fresh code shown on this screen."),
        OnboardingPage(Icons.Default.Language, "Content access", "Choose what can be accessed and what can't")
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(page.icon, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(24.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        page.text,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (index == pages.lastIndex) {
                        Spacer(Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            AccessToggle(Icons.Default.VideoLibrary, "Media library",
                                content[RemoteContent.VIDEO] == true && content[RemoteContent.AUDIO] == true) { on ->
                                listOf(RemoteContent.VIDEO, RemoteContent.AUDIO, RemoteContent.PLAYLISTS, RemoteContent.SEARCH)
                                    .forEach { onToggle(it, on) }
                            }
                            AccessToggle(Icons.Default.Folder, "File browser", content[RemoteContent.FILE_BROWSER] == true) {
                                onToggle(RemoteContent.FILE_BROWSER, it)
                            }
                            AccessToggle(Icons.Default.PlayCircle, "Playback control", content[RemoteContent.CONTROL] == true) {
                                onToggle(RemoteContent.CONTROL, it)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to switch on or off", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!last) TextButton(onClick = onFinished) { Text("Skip") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (last) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }) { Text(if (last) "Done" else "Next") }
            }
        }
    }
}

@Composable
private fun AccessToggle(icon: ImageVector, label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier.width(88.dp).clickable { onChange(!on) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon, contentDescription = null,
                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text(if (on) "On" else "Off", style = MaterialTheme.typography.labelSmall,
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
