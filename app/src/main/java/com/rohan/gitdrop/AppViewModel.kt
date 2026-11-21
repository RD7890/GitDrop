package com.rohan.gitdrop

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rohan.gitdrop.data.GitHubApi
import com.rohan.gitdrop.data.NothingToPush
import com.rohan.gitdrop.data.Prefs
import com.rohan.gitdrop.data.PushRecord
import com.rohan.gitdrop.data.PushRequest
import com.rohan.gitdrop.data.PushState
import com.rohan.gitdrop.data.Pusher
import com.rohan.gitdrop.data.Repo
import com.rohan.gitdrop.data.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val secure = SecureStore(app)
    private val api = GitHubApi(app) { secure.token() }
    private val pusher = Pusher(app, api)

    // ---- account ----
    var hasToken by mutableStateOf(secure.token() != null); private set
    var login by mutableStateOf(prefs.login); private set
    var verifying by mutableStateOf(false); private set
    var tokenOk by mutableStateOf(false); private set
    var tokenMessage by mutableStateOf<String?>(null); private set

    // ---- settings ----
    var authorName by mutableStateOf(prefs.authorName); private set
    var authorEmail by mutableStateOf(prefs.authorEmail); private set
    var deleteRemoved by mutableStateOf(prefs.deleteRemoved); private set
    var ignoreText by mutableStateOf(prefs.ignore); private set
    var theme by mutableIntStateOf(prefs.theme); private set

    // ---- selection ----
    var repos by mutableStateOf<List<Repo>>(emptyList()); private set
    var reposLoading by mutableStateOf(false); private set
    var reposError by mutableStateOf<String?>(null); private set

    var repo by mutableStateOf<Repo?>(null); private set
    var branches by mutableStateOf<List<String>>(emptyList()); private set
    var branchesLoading by mutableStateOf(false); private set
    var branch by mutableStateOf<String?>(null); private set
    var isNewBranch by mutableStateOf(false); private set
    var baseBranch by mutableStateOf(""); private set
    var repoEmpty by mutableStateOf(false); private set

    var folderUri by mutableStateOf<Uri?>(null); private set
    var folderName by mutableStateOf<String?>(null); private set
    var targetPath by mutableStateOf(prefs.lastTargetPath); private set
    var message by mutableStateOf(""); private set
    var description by mutableStateOf(""); private set

    var pushState by mutableStateOf<PushState>(PushState.Idle); private set
    var history by mutableStateOf(prefs.history()); private set

    val canPush: Boolean
        get() = hasToken && repo != null && branch != null && folderUri != null &&
            message.isNotBlank() && pushState !is PushState.Running

    init {
        restoreFolder()
        if (hasToken) loadRepos()
    }

    // ------------------------------------------------------------------ account
    fun saveToken(raw: String) {
        val t = raw.trim()
        if (t.isEmpty() || verifying) return
        viewModelScope.launch {
            verifying = true
            tokenMessage = null
            try {
                val who = withContext(Dispatchers.IO) { api.me(t) }
                secure.saveToken(t)
                prefs.login = who
                login = who
                hasToken = true
                tokenOk = true
                tokenMessage = "Connected as @$who"
                loadRepos()
            } catch (e: Exception) {
                tokenOk = false
                tokenMessage = e.message ?: "Could not verify the token."
            } finally {
                verifying = false
            }
        }
    }

    fun removeToken() {
        secure.clear()
        prefs.login = ""
        login = ""
        hasToken = false
        tokenOk = false
        tokenMessage = null
        repos = emptyList()
        repo = null
        branch = null
        branches = emptyList()
    }

    fun setAuthorName(v: String) { authorName = v; prefs.authorName = v }
    fun setAuthorEmail(v: String) { authorEmail = v; prefs.authorEmail = v }
    fun setDeleteRemoved(v: Boolean) { deleteRemoved = v; prefs.deleteRemoved = v }
    fun setIgnoreText(v: String) { ignoreText = v; prefs.ignore = v }
    fun setTheme(v: Int) { theme = v; prefs.theme = v }

    // ------------------------------------------------------------------ repos & branches
    fun loadRepos() {
        if (reposLoading) return
        viewModelScope.launch {
            reposLoading = true
            reposError = null
            try {
                val list = withContext(Dispatchers.IO) { api.repos() }
                repos = list
                if (repo == null && prefs.lastRepo.isNotEmpty()) {
                    list.firstOrNull { it.fullName == prefs.lastRepo }?.let { selectRepo(it, prefs.lastBranch) }
                }
            } catch (e: Exception) {
                reposError = e.message ?: "Could not load repositories."
            } finally {
                reposLoading = false
            }
        }
    }

    fun ensureRepos() {
        if (repos.isEmpty() && !reposLoading) loadRepos()
    }

    fun selectRepo(r: Repo, preferBranch: String? = null) {
        repo = r
        prefs.lastRepo = r.fullName
        branches = emptyList()
        branch = null
        isNewBranch = false
        repoEmpty = false
        loadBranches(preferBranch)
    }

    private fun loadBranches(prefer: String? = null) {
        val r = repo ?: return
        viewModelScope.launch {
            branchesLoading = true
            try {
                val list = withContext(Dispatchers.IO) { api.branches(r.owner, r.name) }
                if (list.isEmpty()) {
                    repoEmpty = true
                    branches = listOf(r.defaultBranch)
                    branch = r.defaultBranch
                } else {
                    repoEmpty = false
                    branches = list
                    branch = when {
                        prefer != null && prefer in list -> prefer
                        r.defaultBranch in list -> r.defaultBranch
                        else -> list.first()
                    }
                }
                isNewBranch = false
                branch?.let { prefs.lastBranch = it }
            } catch (e: Exception) {
                pushState = PushState.Failed(e.message ?: "Could not load branches.")
            } finally {
                branchesLoading = false
            }
        }
    }

    fun selectBranch(name: String) {
        branch = name
        isNewBranch = false
        prefs.lastBranch = name
        // drop any local "new" branch placeholders that are not on GitHub
    }

    fun createBranch(raw: String) {
        val name = raw.trim().replace(' ', '-')
        if (name.isEmpty()) return
        val base = if (isNewBranch) baseBranch else (branch ?: repo?.defaultBranch ?: "main")
        if (name in branches && !isNewBranch) {
            selectBranch(name)
            return
        }
        baseBranch = base
        branch = name
        isNewBranch = true
    }

    // ------------------------------------------------------------------ folder & commit fields
    fun setFolder(uri: Uri) {
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        folderUri = uri
        folderName = DocumentFile.fromTreeUri(app, uri)?.name ?: uri.lastPathSegment
        prefs.lastFolder = uri.toString()
    }

    private fun restoreFolder() {
        val s = prefs.lastFolder
        if (s.isEmpty()) return
        val uri = Uri.parse(s)
        val ok = app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (ok) {
            folderUri = uri
            folderName = DocumentFile.fromTreeUri(app, uri)?.name ?: uri.lastPathSegment
        }
    }

    fun setTargetPath(v: String) { targetPath = v; prefs.lastTargetPath = v }
    fun setMessage(v: String) { message = v }
    fun setDescription(v: String) { description = v }

    // ------------------------------------------------------------------ push
    fun push() {
        val r = repo ?: return
        val b = branch ?: return
        val folder = folderUri ?: return
        if (!canPush) return
        val req = PushRequest(
            owner = r.owner,
            repo = r.name,
            branch = b,
            isNewBranch = isNewBranch,
            baseBranch = baseBranch.ifEmpty { r.defaultBranch },
            repoEmpty = repoEmpty,
            folder = folder,
            targetPath = targetPath,
            message = message,
            description = description,
            deleteRemoved = deleteRemoved,
            ignore = ignoreText.lines().map { it.trim() }.filter { it.isNotEmpty() },
            authorName = authorName,
            authorEmail = authorEmail,
        )
        viewModelScope.launch {
            pushState = PushState.Running("Starting", 0, 0)
            try {
                val rec: PushRecord = pusher.push(req) { label, done, total ->
                    pushState = PushState.Running(label, done, total)
                }
                history = (listOf(rec) + history).take(10)
                prefs.saveHistory(history)
                pushState = PushState.Success(rec)
                message = ""
                description = ""
                if (req.isNewBranch) {
                    isNewBranch = false
                    loadBranches(b)
                }
            } catch (e: NothingToPush) {
                pushState = PushState.Failed(e.message ?: "Nothing to push.", isInfo = true)
            } catch (e: Exception) {
                pushState = PushState.Failed(e.message ?: "Push failed.")
            }
        }
    }

    fun resetPush() {
        if (pushState !is PushState.Running) pushState = PushState.Idle
    }
}
