package com.example.handsfree.ui

import com.example.handsfree.ui.uiText
import com.example.handsfree.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.handsfree.apps.AppDiscoveryManager
import com.example.handsfree.apps.LaunchableApp
import com.example.handsfree.apps.LaunchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppBrowser(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { AppDiscoveryManager(context) }
    val owner = LocalLifecycleOwner.current
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            apps = withContext(Dispatchers.IO) { manager.discover() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = context.uiText(R.string.apps_failed)
        } finally { loading = false }
    }
    val matches = remember(apps, query) { apps.filter { it.matches(query) } }
    Column(modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(context.uiText(R.string.your_apps), style = MaterialTheme.typography.headlineSmall)
        Text(context.uiText(R.string.choose_open), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text(context.uiText(R.string.search_apps)) }, placeholder = { Text(context.uiText(R.string.search_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text(context.uiText(R.string.clear)) } })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (loading) context.uiText(R.string.loading_apps) else context.uiText(R.string.apps_count, matches.size),
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { refresh++ }, enabled = !loading) { Text(context.uiText(R.string.refresh)) }
        }
        feedback?.let {
            Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.primary)
        }
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            matches.isEmpty() -> {
                Text(if (query.isBlank()) context.uiText(R.string.no_apps) else context.uiText(R.string.no_match),
                    style = MaterialTheme.typography.titleMedium)
                Text(context.uiText(R.string.apps_hint))
            }
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(matches, key = { it.packageName }) { app ->
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button,
                        onClickLabel = context.uiText(R.string.open_named_app, app.name)) {
                        feedback = when (manager.open(app.packageName)) {
                            LaunchResult.REQUESTED -> context.uiText(R.string.opening_app, app.name)
                            LaunchResult.UNAVAILABLE -> context.uiText(R.string.app_unavailable, app.name)
                            LaunchResult.BLOCKED -> context.uiText(R.string.app_blocked, app.name)
                        }
                        refresh++
                    }.heightIn(min = 80.dp).padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
