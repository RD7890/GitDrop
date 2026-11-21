package com.rohan.gitdrop.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubException(val code: Int, message: String) : Exception(message)

/** Streams a file as {"encoding":"base64","content":"..."} without loading it fully in memory. */
private class Base64BlobBody(private val ctx: Context, private val uri: Uri) : RequestBody() {
    override fun contentType() = "application/json".toMediaType()

    override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8("{\"encoding\":\"base64\",\"content\":\"")
        val input = ctx.contentResolver.openInputStream(uri) ?: throw GitHubException(0, "Cannot read $uri")
        input.use { ins ->
            val buf = ByteArray(3 * 12288) // multiple of 3 so chunk padding never lands mid-stream
            while (true) {
                var n = 0
                while (n < buf.size) {
                    val r = ins.read(buf, n, buf.size - n)
                    if (r < 0) break
                    n += r
                }
                if (n == 0) break
                sink.writeUtf8(Base64.encodeToString(buf, 0, n, Base64.NO_WRAP))
                if (n < buf.size) break
            }
        }
        sink.writeUtf8("\"}")
    }
}

class GitHubApi(private val ctx: Context, private val tokenProvider: () -> String?) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    /** Called when the client has to wait (rate limit). */
    var onNotice: ((String) -> Unit)? = null

    private fun enc(branch: String) = Uri.encode(branch, "/")

    private fun friendly(code: Int, msg: String): String = when (code) {
        401 -> "GitHub rejected the token (401). It may be expired or mistyped."
        403 -> (if (msg.isBlank()) "Forbidden" else msg) + " (403). Check that the token has Contents: read & write on this repo."
        404 -> "Not found (404). The repo or branch is missing, or the token has no access to it."
        else -> if (msg.isBlank()) "GitHub error $code" else "$msg ($code)"
    }

    private fun request(method: String, path: String, body: RequestBody?, tokenOverride: String? = null): String {
        var attempt = 0
        while (true) {
            val token = tokenOverride ?: tokenProvider() ?: throw GitHubException(401, "No token yet. Add one in Settings.")
            val req = Request.Builder()
                .url("https://api.github.com$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "GitDrop-Android")
                .method(method, body)
                .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) return text
                val msg = try { JSONObject(text).optString("message", "") } catch (e: Exception) { "" }
                val limited = (resp.code == 403 || resp.code == 429) &&
                    (resp.header("retry-after") != null || resp.header("x-ratelimit-remaining") == "0" ||
                        msg.contains("rate limit", ignoreCase = true))
                if (limited && attempt < 4) {
                    attempt++
                    val reset = resp.header("x-ratelimit-reset")?.toLongOrNull()
                    val wait = resp.header("retry-after")?.toLongOrNull()
                        ?: reset?.let { (it - System.currentTimeMillis() / 1000).coerceIn(1, 120) }
                        ?: 60L
                    onNotice?.invoke("GitHub asked us to slow down. Waiting ${wait}s")
                    Thread.sleep(wait * 1000)
                } else {
                    throw GitHubException(resp.code, friendly(resp.code, msg))
                }
            }
        }
    }

    private fun jsonBody(o: JSONObject): RequestBody = o.toString().toRequestBody(jsonType)

    fun me(token: String? = null): String =
        JSONObject(request("GET", "/user", null, token)).getString("login")

    fun repos(): List<Repo> {
        val out = mutableListOf<Repo>()
        for (page in 1..5) {
            val arr = JSONArray(request("GET", "/user/repos?per_page=100&sort=pushed&page=$page", null))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optBoolean("archived")) continue
                out += Repo(
                    fullName = o.getString("full_name"),
                    name = o.getString("name"),
                    owner = o.getJSONObject("owner").getString("login"),
                    isPrivate = o.optBoolean("private"),
                    defaultBranch = o.optString("default_branch", "main"),
                )
            }
            if (arr.length() < 100) break
        }
        return out
    }

    fun branches(owner: String, repo: String): List<String> {
        val out = mutableListOf<String>()
        for (page in 1..3) {
            val arr = JSONArray(request("GET", "/repos/$owner/$repo/branches?per_page=100&page=$page", null))
            for (i in 0 until arr.length()) out += arr.getJSONObject(i).getString("name")
            if (arr.length() < 100) break
        }
        return out
    }

    fun refSha(owner: String, repo: String, branch: String): String =
        JSONObject(request("GET", "/repos/$owner/$repo/git/ref/heads/${enc(branch)}", null))
            .getJSONObject("object").getString("sha")

    fun commitTree(owner: String, repo: String, commitSha: String): String =
        JSONObject(request("GET", "/repos/$owner/$repo/git/commits/$commitSha", null))
            .getJSONObject("tree").getString("sha")

    /** path -> blob sha, plus whether GitHub truncated the listing. */
    fun tree(owner: String, repo: String, treeSha: String): Pair<Map<String, String>, Boolean> {
        val o = JSONObject(request("GET", "/repos/$owner/$repo/git/trees/$treeSha?recursive=1", null))
        val arr = o.getJSONArray("tree")
        val map = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            if (e.optString("type") == "blob") map[e.getString("path")] = e.getString("sha")
        }
        return map to o.optBoolean("truncated", false)
    }

    fun createBlob(owner: String, repo: String, uri: Uri): String =
        JSONObject(request("POST", "/repos/$owner/$repo/git/blobs", Base64BlobBody(ctx, uri))).getString("sha")

    fun createTree(owner: String, repo: String, baseTree: String, entries: JSONArray): String =
        JSONObject(
            request(
                "POST", "/repos/$owner/$repo/git/trees",
                jsonBody(JSONObject().put("base_tree", baseTree).put("tree", entries)),
            )
        ).getString("sha")

    fun createCommit(
        owner: String, repo: String, message: String, tree: String, parent: String,
        authorName: String, authorEmail: String,
    ): String {
        val body = JSONObject().put("message", message).put("tree", tree).put("parents", JSONArray().put(parent))
        if (authorName.isNotBlank() && authorEmail.isNotBlank()) {
            body.put("author", JSONObject().put("name", authorName).put("email", authorEmail))
        }
        return JSONObject(request("POST", "/repos/$owner/$repo/git/commits", jsonBody(body))).getString("sha")
    }

    fun updateRef(owner: String, repo: String, branch: String, sha: String) {
        request(
            "PATCH", "/repos/$owner/$repo/git/refs/heads/${enc(branch)}",
            jsonBody(JSONObject().put("sha", sha).put("force", false)),
        )
    }

    fun createRef(owner: String, repo: String, branch: String, sha: String) {
        request(
            "POST", "/repos/$owner/$repo/git/refs",
            jsonBody(JSONObject().put("ref", "refs/heads/$branch").put("sha", sha)),
        )
    }

    /** Brand-new empty repos reject the Git Data API, so make a first commit via the Contents API. */
    fun initEmptyRepo(owner: String, repo: String, branch: String) {
        request(
            "PUT", "/repos/$owner/$repo/contents/.gitkeep",
            jsonBody(
                JSONObject().put("message", "Initial commit").put("content", "Cg==").put("branch", branch)
            ),
        )
    }
}
