package com.rohan.gitdrop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rohan.gitdrop.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(vm.hasToken, vm.tokenOk) {
        if (vm.hasToken && vm.tokenOk) tokenInput = ""
    }

    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }

    Scaffold(containerColor = cs.background) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { PhIcon(Ph.Back, size = 24.dp) }
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(12.dp))

            // ---------------- GitHub access
            Section("GITHUB ACCESS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhIcon(
                        if (vm.hasToken) Ph.CheckCircle else Ph.Key,
                        tint = if (vm.hasToken) cs.primary else cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (vm.hasToken) "Connected${if (vm.login.isNotEmpty()) " as @${vm.login}" else ""}" else "Not connected",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                GdField(
                    value = tokenInput,
                    onChange = { tokenInput = it },
                    label = if (vm.hasToken) "Replace token" else "Personal access token",
                    leading = Ph.Key,
                    visual = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailing = {
                        IconButton(onClick = { showToken = !showToken }) {
                            PhIcon(if (showToken) Ph.EyeOff else Ph.Eye, size = 20.dp)
                        }
                    },
                )
                Button(
                    onClick = { vm.saveToken(tokenInput) },
                    enabled = tokenInput.isNotBlank() && !vm.verifying,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                        disabledContainerColor = cs.outlineVariant,
                        disabledContentColor = cs.onSurfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (vm.verifying) {
                        CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), color = cs.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Text("Verify & save", style = MaterialTheme.typography.labelLarge)
                    }
                }
                vm.tokenMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = if (vm.tokenOk) cs.primary else cs.error)
                }
                Text(
                    "Fine-grained token: give it Contents → Read and write on the repos you push to. " +
                        "To push files inside .github/workflows, also enable Workflows → Read and write. " +
                        "Classic token: the repo scope (plus workflow).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                TextButton(onClick = { ctx.openUrl("https://github.com/settings/personal-access-tokens/new") }) {
                    PhIcon(Ph.External, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Create a token on GitHub")
                }
                if (vm.hasToken) {
                    if (!confirmRemove) {
                        TextButton(onClick = { confirmRemove = true }) {
                            Text("Remove token from this phone", color = cs.error)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Sure?", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.removeToken(); confirmRemove = false }) { Text("Yes, remove", color = cs.error) }
                            TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---------------- commit identity
            Section("COMMIT IDENTITY") {
                GdField(vm.authorName, vm::setAuthorName, "Author name (optional)", leading = Ph.Commit)
                GdField(
                    vm.authorEmail, vm::setAuthorEmail, "Author email (optional)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Text(
                    "Leave both empty and GitHub uses your profile identity. Both are needed to override it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(22.dp))

            // ---------------- push behaviour
            Section("PUSH BEHAVIOUR") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Delete files removed locally", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Files in the repo (inside the destination path) that are not in your folder get deleted in the commit.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = vm.deleteRemoved,
                        onCheckedChange = vm::setDeleteRemoved,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = cs.primary,
                            uncheckedThumbColor = cs.onSurfaceVariant,
                            uncheckedTrackColor = cs.outlineVariant,
                            uncheckedBorderColor = cs.outline,
                        ),
                    )
                }
                GdField(vm.ignoreText, vm::setIgnoreText, "Ignore list (one per line)", singleLine = false, minLines = 3)
                Text(
                    "Matches file or folder names. Use *.ext for extensions. Files over 90 MB are always skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(22.dp))

            // ---------------- appearance
            Section("APPEARANCE") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhIcon(Ph.Theme, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                }
                Segmented(listOf("System", "Light", "Dark"), vm.theme, vm::setTheme)
            }

            Spacer(Modifier.height(22.dp))

            // ---------------- about
            Section("ABOUT") {
                Text("GitDrop ${if (version.isNotEmpty()) "v$version" else ""}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your token is encrypted with a key kept in the Android Keystore and only ever sent to api.github.com. " +
                        "Icons by Phosphor (MIT).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.background).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val spec = tween<Color>(250, easing = FastOutSlowInEasing)
            val bg by animateColorAsState(if (i == selected) cs.primary else Color.Transparent, spec, label = "segBg")
            val fg by animateColorAsState(if (i == selected) cs.onPrimary else cs.onSurfaceVariant, spec, label = "segFg")
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = fg, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
