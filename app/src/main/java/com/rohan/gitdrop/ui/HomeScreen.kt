package com.rohan.gitdrop.ui

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohan.gitdrop.AppViewModel
import com.rohan.gitdrop.data.PushRecord
import com.rohan.gitdrop.data.PushState
import com.rohan.gitdrop.data.Repo

private enum class Sheet { Repo, Branch }

@Composable
fun HomeScreen(vm: AppViewModel, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setFolder(uri)
    }

    val view = LocalView.current
    val running = vm.pushState is PushState.Running
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
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
            // ---------- header
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GitDrop", style = MaterialTheme.typography.headlineLarge)
                    Text("Pick a folder, push it to GitHub.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                if (vm.login.isNotEmpty()) {
                    Surface(shape = CircleShape, color = cs.surfaceVariant) {
                        Text(
                            "@${vm.login}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = onSettings) { PhIcon(Ph.Settings, size = 26.dp) }
            }

            Spacer(Modifier.height(22.dp))

            // ---------- connect callout
            if (!vm.hasToken) {
                Surface(shape = RoundedCornerShape(20.dp), color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        PhIcon(Ph.Key, tint = cs.primary, size = 28.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Connect GitHub first", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                            Text(
                                "Add a personal access token to start pushing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onPrimaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onSettings,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Add token", style = MaterialTheme.typography.labelLarge) }
                Spacer(Modifier.height(24.dp))
            }

            // ---------- pipeline
            PipelineStep(
                icon = Ph.Repo,
                title = "REPOSITORY",
                value = vm.repo?.fullName,
                placeholder = "Choose a repository",
                done = vm.repo != null,
                last = false,
                mono = true,
                enabled = vm.hasToken,
                onClick = {
                    vm.ensureRepos()
                    sheet = Sheet.Repo
                },
            )
            PipelineStep(
                icon = Ph.Branch,
                title = "BRANCH",
                value = vm.branch?.let { if (vm.isNewBranch) "$it  (new)" else it },
                placeholder = "Choose a branch",
                done = vm.branch != null,
                last = false,
                mono = true,
                enabled = vm.repo != null,
                onClick = { sheet = Sheet.Branch },
            )
            PipelineStep(
                icon = Ph.Folder,
                title = "FOLDER ON THIS PHONE",
                value = vm.folderName,
                placeholder = "Pick a folder",
                done = vm.folderUri != null,
                last = false,
                onClick = { picker.launch(null) },
            )
            PipelineStep(
                icon = Ph.Commit,
                title = "COMMIT",
                value = null,
                placeholder = "Describe this change",
                done = vm.message.isNotBlank(),
                last = true,
            ) {
                GdField(vm.message, vm::setMessage, "Commit message")
                GdField(vm.description, vm::setDescription, "Description (optional)", singleLine = false, minLines = 2)
                GdField(vm.targetPath, vm::setTargetPath, "Destination path in repo (optional)")
            }

            Spacer(Modifier.height(24.dp))

            // ---------- push area
            PushArea(vm)

            // ---------- history
            if (vm.history.isNotEmpty()) {
                Spacer(Modifier.height(30.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhIcon(Ph.History, size = 18.dp, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Recent pushes", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                vm.history.forEach { HistoryRow(it) { ctx.openUrl(it.url) } }
            }
        }
    }

    when (sheet) {
        Sheet.Repo -> RepoSheet(vm) { sheet = null }
        Sheet.Branch -> BranchSheet(vm) { sheet = null }
        null -> {}
    }
}

@Composable
private fun PushArea(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val kind = when (vm.pushState) {
        is PushState.Idle -> 0
        is PushState.Running -> 1
        is PushState.Success -> 2
        is PushState.Failed -> 3
    }

    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            (fadeIn(tween(250, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(150)))
                .using(SizeTransform(clip = false) { _, _ -> tween(300, easing = FastOutSlowInEasing) })
        },
        label = "pushArea",
        modifier = Modifier.fillMaxWidth(),
    ) { k ->
        when (k) {
            0 -> Column {
                Button(
                    onClick = vm::push,
                    enabled = vm.canPush,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = cs.onPrimary,
                        disabledContainerColor = cs.surfaceVariant,
                        disabledContentColor = cs.onSurfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                ) {
                    PhIcon(Ph.Push, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Push to GitHub", style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
                }
                if (!vm.canPush) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Choose a repo, branch and folder, and write a commit message.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            1 -> {
                val st = vm.pushState as? PushState.Running
                if (st != null) {
                    val target = if (st.total > 0) st.done.toFloat() / st.total else 0f
                    val frac by animateFloatAsState(target, tween(300, easing = FastOutSlowInEasing), label = "progress")
                    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(st.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (st.total > 0) {
                                    Text(
                                        "${st.done}/${st.total}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            if (st.total > 0) {
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = cs.primary,
                                    trackColor = cs.outlineVariant,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = cs.primary,
                                    trackColor = cs.outlineVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Keep the app open until this finishes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            2 -> {
                val st = vm.pushState as? PushState.Success
                if (st != null) {
                    val r = st.record
                    Surface(shape = RoundedCornerShape(20.dp), color = cs.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PhIcon(Ph.CheckCircle, size = 30.dp, tint = cs.primary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Pushed ${r.files} ${if (r.files == 1) "file" else "files"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = cs.onPrimaryContainer,
                                    )
                                    Text(
                                        "${r.repo} · ${r.branch} · ${r.sha.take(7)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = cs.onPrimaryContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row {
                                TextButton(onClick = { ctx.openUrl(r.url) }) {
                                    PhIcon(Ph.External, size = 18.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("View commit")
                                }
                                TextButton(onClick = vm::resetPush) { Text("Done") }
                            }
                        }
                    }
                }
            }

            else -> {
                val st = vm.pushState as? PushState.Failed
                if (st != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (st.isInfo) cs.surfaceVariant else cs.error.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                PhIcon(
                                    if (st.isInfo) Ph.CheckCircle else Ph.Warning,
                                    size = 28.dp,
                                    tint = if (st.isInfo) cs.primary else cs.error,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(st.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row {
                                if (!st.isInfo) {
                                    TextButton(onClick = vm::push, enabled = vm.canPush) {
                                        PhIcon(Ph.Refresh, size = 18.dp)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Try again")
                                    }
                                }
                                TextButton(onClick = vm::resetPush) { Text("Dismiss") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(r: PushRecord, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cs.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.message, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${r.repo} · ${r.branch}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                DateUtils.getRelativeTimeSpanString(r.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------------ sheets

@Composable
private fun RepoSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    PickerSheet("Choose repository", onDismiss) { close ->
        GdField(q, { q = it }, "Search repositories", leading = Ph.Search)
        Spacer(Modifier.height(12.dp))
        when {
            vm.reposLoading && vm.repos.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = cs.primary, strokeWidth = 3.dp)
            }
            vm.reposError != null && vm.repos.isEmpty() -> Column {
                Text(vm.reposError ?: "", color = cs.error, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = vm::loadRepos) {
                    PhIcon(Ph.Refresh, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Retry")
                }
            }
            else -> {
                val list = vm.repos.filter { it.fullName.contains(q.trim(), ignoreCase = true) }
                if (list.isEmpty()) {
                    Text("No repositories found.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 460.dp)) {
                        items(list, key = { it.fullName }) { r ->
                            RepoRow(r, selected = r.fullName == vm.repo?.fullName) {
                                vm.selectRepo(r)
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoRow(r: Repo, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhIcon(Ph.Repo, size = 20.dp, tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            r.fullName,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (r.isPrivate) {
            Text("private", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        if (selected) PhIcon(Ph.Check, size = 20.dp, tint = cs.primary)
    }
}

@Composable
private fun BranchSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var newName by remember { mutableStateOf("") }
    PickerSheet("Choose branch", onDismiss) { close ->
        if (!vm.repoEmpty) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GdField(newName, { newName = it }, "New branch name", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        vm.createBranch(newName)
                        close()
                    },
                    enabled = newName.isNotBlank(),
                ) { PhIcon(Ph.Plus, tint = if (newName.isNotBlank()) cs.primary else cs.onSurfaceVariant) }
            }
            Text(
                "New branches start from ${if (vm.isNewBranch) vm.baseBranch else (vm.branch ?: "the current branch")}.",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
        }
        if (vm.repoEmpty) {
            Text(
                "This repository is empty. The first push will create the branch.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        if (vm.branchesLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = cs.primary, strokeWidth = 3.dp)
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(vm.branches, key = { it }) { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.selectBranch(b)
                            close()
                        }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PhIcon(Ph.Branch, size = 20.dp, tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            b,
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (b == vm.branch && !vm.isNewBranch) PhIcon(Ph.Check, size = 20.dp, tint = cs.primary)
                    }
                }
            }
        }
    }
}
