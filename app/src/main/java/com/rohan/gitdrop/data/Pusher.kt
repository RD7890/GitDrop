package com.rohan.gitdrop.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

private data class LocalFile(val path: String, val uri: Uri, val size: Long)

class Pusher(private val ctx: Context, private val api: GitHubApi) {

    private fun ignored(name: String, patterns: List<String>) = patterns.any { p ->
        if (p.startsWith("*")) name.endsWith(p.drop(1)) else name == p
    }

    private fun scan(root: Uri, ignore: List<String>): List<LocalFile> {
        val rootDoc = DocumentFile.fromTreeUri(ctx, root)
            ?: throw IllegalStateException("Cannot open the selected folder. Pick it again.")
        val out = ArrayList<LocalFile>()
        fun walk(dir: DocumentFile, prefix: String) {
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                if (ignored(name, ignore)) continue
                if (f.isDirectory) {
                    walk(f, "$prefix$name/")
                } else if (f.isFile) {
                    val size = f.length()
                    if (size <= MAX_BYTES) out += LocalFile(prefix + name, f.uri, size)
                }
            }
        }
        walk(rootDoc, "")
        return out.sortedBy { it.path }
    }

    /** Git blob SHA-1 ("blob <size>\0" + content) so unchanged files are never re-uploaded. */
    private fun gitSha(uri: Uri, size: Long): String? {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob $size\u0000".toByteArray())
        var count = 0L
        val stream = ctx.contentResolver.openInputStream(uri) ?: return null
        stream.use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                count += n
            }
        }
        if (count != size) return null
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun push(req: PushRequest, onProgress: (String, Int, Int) -> Unit): PushRecord =
        withContext(Dispatchers.IO) {
            api.onNotice = { n -> onProgress(n, 0, 0) }

            onProgress("Scanning folder", 0, 0)
            val files = scan(req.folder, req.ignore)
            if (files.isEmpty()) throw IllegalStateException("The folder has no files to push.")

            val prefix = req.targetPath.trim().trim('/').let { if (it.isEmpty()) "" else "$it/" }

            if (req.repoEmpty) {
                onProgress("Creating first commit", 0, 0)
                api.initEmptyRepo(req.owner, req.repo, req.branch)
                Thread.sleep(1500)
            }

            val fromBranch = if (req.isNewBranch) req.baseBranch else req.branch
            val baseSha = api.refSha(req.owner, req.repo, fromBranch)
            val baseTree = api.commitTree(req.owner, req.repo, baseSha)

            onProgress("Comparing with GitHub", 0, 0)
            val (remote, truncated) = api.tree(req.owner, req.repo, baseTree)

            val shas = HashMap<String, String?>()
            files.forEachIndexed { i, f ->
                ensureActive()
                onProgress("Checking files", i, files.size)
                shas[f.path] = gitSha(f.uri, f.size)
            }

            val changed = files.filter { f ->
                truncated || shas[f.path] == null || remote[prefix + f.path] != shas[f.path]
            }
            val localPaths = files.map { prefix + it.path }.toSet()
            val deletions = if (req.deleteRemoved && !truncated) {
                remote.keys.filter { (prefix.isEmpty() || it.startsWith(prefix)) && it !in localPaths }
            } else emptyList()

            if (changed.isEmpty() && deletions.isEmpty()) throw NothingToPush()

            val sem = Semaphore(3)
            val counter = AtomicInteger(0)
            onProgress("Uploading files", 0, changed.size)
            val blobs = coroutineScope {
                changed.map { f ->
                    async {
                        sem.withPermit {
                            val sha = api.createBlob(req.owner, req.repo, f.uri)
                            onProgress("Uploading files", counter.incrementAndGet(), changed.size)
                            f.path to sha
                        }
                    }
                }.awaitAll()
            }.toMap()

            val entries = ArrayList<JSONObject>()
            changed.forEach { f ->
                entries += JSONObject().put("path", prefix + f.path).put("mode", "100644")
                    .put("type", "blob").put("sha", blobs.getValue(f.path))
            }
            deletions.forEach { p ->
                entries += JSONObject().put("path", p).put("mode", "100644")
                    .put("type", "blob").put("sha", JSONObject.NULL)
            }

            onProgress("Building commit", 0, 0)
            var tree = baseTree
            for (chunk in entries.chunked(200)) {
                tree = api.createTree(req.owner, req.repo, tree, JSONArray(chunk))
            }

            val headline = req.message.trim()
            val message = if (req.description.isBlank()) headline else headline + "\n\n" + req.description.trim()
            val commit = api.createCommit(
                req.owner, req.repo, message, tree, baseSha, req.authorName.trim(), req.authorEmail.trim(),
            )

            onProgress("Updating branch", 0, 0)
            if (req.isNewBranch) api.createRef(req.owner, req.repo, req.branch, commit)
            else api.updateRef(req.owner, req.repo, req.branch, commit)

            PushRecord(
                repo = "${req.owner}/${req.repo}",
                branch = req.branch,
                message = headline,
                sha = commit,
                files = changed.size + deletions.size,
                time = System.currentTimeMillis(),
                url = "https://github.com/${req.owner}/${req.repo}/commit/$commit",
            )
        }

    companion object {
        const val MAX_BYTES = 90L * 1024 * 1024
    }
}
