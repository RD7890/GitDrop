package com.rohan.gitdrop.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gitdrop_prefs", Context.MODE_PRIVATE)

    var login: String
        get() = sp.getString("login", "") ?: ""
        set(v) = sp.edit().putString("login", v).apply()

    var authorName: String
        get() = sp.getString("author_name", "") ?: ""
        set(v) = sp.edit().putString("author_name", v).apply()

    var authorEmail: String
        get() = sp.getString("author_email", "") ?: ""
        set(v) = sp.edit().putString("author_email", v).apply()

    var deleteRemoved: Boolean
        get() = sp.getBoolean("delete_removed", false)
        set(v) = sp.edit().putBoolean("delete_removed", v).apply()

    var ignore: String
        get() = sp.getString("ignore", DEFAULT_IGNORE) ?: DEFAULT_IGNORE
        set(v) = sp.edit().putString("ignore", v).apply()

    /** 0 = system, 1 = light, 2 = dark */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    var lastRepo: String
        get() = sp.getString("last_repo", "") ?: ""
        set(v) = sp.edit().putString("last_repo", v).apply()

    var lastBranch: String
        get() = sp.getString("last_branch", "") ?: ""
        set(v) = sp.edit().putString("last_branch", v).apply()

    var lastFolder: String
        get() = sp.getString("last_folder", "") ?: ""
        set(v) = sp.edit().putString("last_folder", v).apply()

    var lastTargetPath: String
        get() = sp.getString("last_target", "") ?: ""
        set(v) = sp.edit().putString("last_target", v).apply()

    fun history(): List<PushRecord> {
        val raw = sp.getString("history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PushRecord(
                    o.getString("repo"), o.getString("branch"), o.getString("message"),
                    o.getString("sha"), o.getInt("files"), o.getLong("time"), o.getString("url"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(list: List<PushRecord>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("repo", it.repo).put("branch", it.branch).put("message", it.message)
                    .put("sha", it.sha).put("files", it.files).put("time", it.time).put("url", it.url)
            )
        }
        sp.edit().putString("history", arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_IGNORE = ".git\nnode_modules\n.gradle\n.idea\nbuild\n.DS_Store"
    }
}
