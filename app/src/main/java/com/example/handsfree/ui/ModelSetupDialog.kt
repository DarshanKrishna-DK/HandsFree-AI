package com.example.handsfree.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.handsfree.R
import com.example.handsfree.agent.ModelArtifact
import com.example.handsfree.agent.VerifiedModelStore
import kotlinx.coroutines.*
import java.io.File

/** Model setup is independent of agent actions and never starts microphone capture. */
@Composable
fun ModelSetupDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { VerifiedModelStore(File(context.noBackupFilesDir, "models"), ModelArtifact.GEMMA) }
    var busy by remember { mutableStateOf(true) }
    var verified by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var importing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) {
        try {
            verified = withContext(Dispatchers.IO) { val job = currentCoroutineContext(); store.verify { job.ensureActive() } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = R.string.model_check_failed }
        finally { busy = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true; failure = null; importing = true; progress = 0f
            operation = scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val job = currentCoroutineContext()
                        val input = context.contentResolver.openInputStream(uri) ?: error("No file access")
                        var lastPercent = -1
                        store.import(input, checkActive = { job.ensureActive() }) { bytes ->
                            val percent = (bytes * 100 / store.artifact.bytes).toInt()
                            if (percent != lastPercent) { lastPercent = percent; progress = percent / 100f }
                        }
                    }
                    verified = true
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failure = R.string.model_import_failed }
                finally { busy = false; importing = false }
            }
        }
    }
    AlertDialog(onDismissRequest = { operation?.cancel(); onClose() }, title = { Text(context.uiText(R.string.model_setup)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(context.uiText(R.string.model_setup_description))
            Text(context.uiText(R.string.model_size))
            Text(context.uiText(failure ?: when {
                importing -> R.string.model_importing
                busy -> R.string.model_checking
                verified -> R.string.model_verified
                else -> R.string.model_missing
            }))
            if (importing) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            else if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(enabled = !busy, onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ModelArtifact.SOURCE))) }
                    .onSuccess { failure = null }
                    .onFailure { failure = R.string.model_browser_failed }
            }, modifier = Modifier.fillMaxWidth()) { Text(context.uiText(R.string.model_source)) }
            Button(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(context.uiText(R.string.model_import))
            }
            if (store.modelFile.exists()) TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                Text(context.uiText(R.string.model_remove))
            }
        }
    }, confirmButton = { TextButton(onClick = { operation?.cancel(); onClose() }) { Text(context.uiText(if (busy) R.string.stop else R.string.done)) } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(context.uiText(R.string.model_remove)) },
        text = { Text(context.uiText(R.string.model_remove_confirm)) },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false; busy = true
            operation = scope.launch {
                try {
                    if (withContext(Dispatchers.IO) { store.delete() }) { verified = false; failure = null }
                    else failure = R.string.model_remove_failed
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failure = R.string.model_remove_failed }
                finally { busy = false }
            }
        }) { Text(context.uiText(R.string.model_remove)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(context.uiText(R.string.not_now)) } })
}
