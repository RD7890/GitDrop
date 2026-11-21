package com.rohan.gitdrop.data

import android.net.Uri

data class Repo(
    val fullName: String,
    val name: String,
    val owner: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
)

data class PushRecord(
    val repo: String,
    val branch: String,
    val message: String,
    val sha: String,
    val files: Int,
    val time: Long,
    val url: String,
)

sealed interface PushState {
    data object Idle : PushState
    data class Running(val label: String, val done: Int, val total: Int) : PushState
    data class Success(val record: PushRecord) : PushState
    data class Failed(val message: String, val isInfo: Boolean = false) : PushState
}

data class PushRequest(
    val owner: String,
    val repo: String,
    val branch: String,
    val isNewBranch: Boolean,
    val baseBranch: String,
    val repoEmpty: Boolean,
    val folder: Uri,
    val targetPath: String,
    val message: String,
    val description: String,
    val deleteRemoved: Boolean,
    val ignore: List<String>,
    val authorName: String,
    val authorEmail: String,
)

class NothingToPush : Exception("Everything is already up to date. There are no changes to push.")
