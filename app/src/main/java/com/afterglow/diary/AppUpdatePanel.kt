package com.afterglow.diary

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
internal fun AppUpdatePanel(controller: AppUpdateController, onBack: () -> Unit, onClose: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(context.packageManager.canRequestPackageInstalls()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed = context.packageManager.canRequestPackageInstalls()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = context.packageManager.canRequestPackageInstalls() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    NotebookPanel("Dream Diary", onClose, onDismiss = onBack, onBack = onBack, backLabel = "Back to settings", background = LibraryPaper) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("app-updates")) {
            val wide = maxWidth >= 680.dp
            @Composable fun Heading() {
                Text("App updates", Modifier.padding(top = 16.dp), color = Ink, fontFamily = DiarySerif,
                    fontSize = if (wide) 30.sp else 36.sp, lineHeight = 44.sp, letterSpacing = (-1).sp)
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(Accent, CircleShape))
                    Text("Version ${controller.installedVersion}", fontSize = 13.sp, color = Pencil)
                }
            }
            @Composable fun UpdateControls() {
                val release = state.release
                val title = when (state.phase) {
                    UpdatePhase.Idle -> "Keep your diary up to date"
                    UpdatePhase.Checking -> "Checking for updates…"
                    UpdatePhase.Current -> "You’re up to date"
                    UpdatePhase.Available -> "A new version is ready"
                    UpdatePhase.Downloading -> "Downloading your update"
                    UpdatePhase.Verifying -> "Checking the update file…"
                    UpdatePhase.Ready -> "Ready to install"
                    UpdatePhase.Cancelling -> "Stopping the download…"
                    UpdatePhase.Failed -> "The update couldn’t finish"
                }
                val detail = when (state.phase) {
                    UpdatePhase.Available -> "Dream Diary ${release?.version} · ${"%.1f".format((release?.bytes ?: 0) / 1048576.0)} MB"
                    UpdatePhase.Downloading -> "Keep the app open while it downloads."
                    UpdatePhase.Ready -> if (allowed) "Android will ask you to confirm. Your dreams and settings stay in place."
                        else "Allow updates from Dream Diary in Android’s settings. Then return here to install."
                    UpdatePhase.Current -> "You have the latest available version."
                    UpdatePhase.Idle -> "Check GitHub for the latest Dream Diary release."
                    UpdatePhase.Failed -> state.error.orEmpty()
                    else -> "Your notebook stays safely on this device."
                }
                GroupedSection(Modifier.padding(top = if (wide) 18.dp else 32.dp)) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(title, color = Ink, fontFamily = DiarySerif, fontSize = 25.sp, lineHeight = 32.sp, modifier = Modifier.testTag("update-status"))
                        Text(detail, color = Pencil, fontSize = 14.sp, lineHeight = 22.sp)
                        if (state.error != null && state.phase != UpdatePhase.Failed) Text(state.error!!, color = Pencil, fontSize = 13.sp)
                        if (state.phase == UpdatePhase.Downloading && release != null) {
                            LinearProgressIndicator(progress = { (state.downloaded.toFloat() / release.bytes).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().testTag("update-progress"), color = Accent, trackColor = Hairline)
                            Text("${(100L * state.downloaded / release.bytes).coerceIn(0, 100)}%", color = Pencil, fontSize = 12.sp)
                        } else if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = Hairline)
                        when (state.phase) {
                            UpdatePhase.Idle, UpdatePhase.Current, UpdatePhase.Failed -> {
                                if (state.phase == UpdatePhase.Failed && release != null) Button(controller::download, Modifier.fillMaxWidth().testTag("download-update")) { Text("Download again") }
                                Button(controller::check, Modifier.fillMaxWidth().testTag("check-updates")) { Text(if (state.phase == UpdatePhase.Idle) "Check for updates" else "Check again") }
                            }
                            UpdatePhase.Available -> Button(controller::download, Modifier.fillMaxWidth().testTag("download-update")) { Text("Download update") }
                            UpdatePhase.Ready -> Button(onClick = {
                                allowed = context.packageManager.canRequestPackageInstalls()
                                if (!allowed) runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                                    .onFailure { controller.installerUnavailable() }
                                else scope.launch {
                                    controller.installerIntent()?.let { intent -> runCatching { context.startActivity(intent) }.onFailure { controller.installerUnavailable() } }
                                }
                            }, modifier = Modifier.fillMaxWidth().testTag("install-update")) { Text(if (allowed) "Install update" else "Allow app updates") }
                            UpdatePhase.Downloading, UpdatePhase.Checking -> TextButton(controller::cancel, Modifier.align(Alignment.CenterHorizontally).testTag("cancel-update")) { Text("Cancel") }
                            else -> Unit
                        }
                    }
                }
                Text("Updates are checked only when you ask. Your dreams, drawings and recordings are never sent with update requests.",
                    Modifier.padding(horizontal = 4.dp, vertical = 20.dp), fontSize = 12.sp, lineHeight = 19.sp, color = Pencil)
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE))) }.onFailure { controller.installerUnavailable() } },
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Release notes on GitHub ↗", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.height(24.dp))
            }
            if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                Column(Modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { Heading() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { UpdateControls() }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Heading(); UpdateControls() }
        }
    }
}
