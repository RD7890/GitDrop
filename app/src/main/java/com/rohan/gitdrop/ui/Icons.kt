package com.rohan.gitdrop.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.*

/**
 * Every icon in the app comes from Phosphor and is referenced through this one object.
 * If the library's import paths ever differ, this is the only file to touch.
 */
object Ph {
    val Branch: ImageVector get() = PhosphorIcons.Regular.GitBranch
    val Commit: ImageVector get() = PhosphorIcons.Regular.GitCommit
    val Folder: ImageVector get() = PhosphorIcons.Regular.FolderOpen
    val Repo: ImageVector get() = PhosphorIcons.Regular.BookBookmark
    val Settings: ImageVector get() = PhosphorIcons.Regular.GearSix
    val Back: ImageVector get() = PhosphorIcons.Regular.ArrowLeft
    val Next: ImageVector get() = PhosphorIcons.Regular.CaretRight
    val Down: ImageVector get() = PhosphorIcons.Regular.CaretDown
    val CheckCircle: ImageVector get() = PhosphorIcons.Regular.CheckCircle
    val Check: ImageVector get() = PhosphorIcons.Regular.Check
    val History: ImageVector get() = PhosphorIcons.Regular.ClockCounterClockwise
    val Push: ImageVector get() = PhosphorIcons.Regular.CloudArrowUp
    val Theme: ImageVector get() = PhosphorIcons.Regular.CircleHalf
    val External: ImageVector get() = PhosphorIcons.Regular.ArrowSquareOut
    val Key: ImageVector get() = PhosphorIcons.Regular.Key
    val Plus: ImageVector get() = PhosphorIcons.Regular.Plus
    val Refresh: ImageVector get() = PhosphorIcons.Regular.ArrowClockwise
    val Warning: ImageVector get() = PhosphorIcons.Regular.WarningCircle
    val Eye: ImageVector get() = PhosphorIcons.Regular.Eye
    val EyeOff: ImageVector get() = PhosphorIcons.Regular.EyeSlash
    val Search: ImageVector get() = PhosphorIcons.Regular.MagnifyingGlass
}
