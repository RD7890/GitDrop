package com.rohan.gitdrop.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

@Composable
fun PhIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 22.dp, tint: androidx.compose.ui.graphics.Color = androidx.compose.material3.LocalContentColor.current) {
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
fun monoStyle() = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)

/** One node of the git-graph style pipeline on the Home screen. */
@Composable
fun PipelineStep(
    icon: ImageVector,
    title: String,
    value: String?,
    placeholder: String,
    done: Boolean,
    last: Boolean,
    mono: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val spec = tween<androidx.compose.ui.graphics.Color>(300, easing = FastOutSlowInEasing)
    val nodeBg = animateColorAsState(if (done) cs.primary else cs.background, spec, label = "nodeBg").value
    val nodeBorder = animateColorAsState(if (done) cs.primary else cs.outline, spec, label = "nodeBorder").value
    val nodeTint = animateColorAsState(if (done) cs.onPrimary else cs.onSurfaceVariant, spec, label = "nodeTint").value
    val line = animateColorAsState(if (done) cs.primary else cs.outlineVariant, spec, label = "line").value

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(34.dp).background(nodeBg, CircleShape).border(2.dp, nodeBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { PhIcon(icon, size = 18.dp, tint = nodeTint) }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(line))
        }
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 14.dp).alpha(if (enabled) 1f else 0.5f)) {
            val inner: @Composable ColumnScope.() -> Unit = {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            value ?: placeholder,
                            style = if (mono && value != null) monoStyle() else MaterialTheme.typography.titleMedium,
                            color = if (value == null) cs.onSurfaceVariant else cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (onClick != null) PhIcon(Ph.Next, size = 18.dp, tint = cs.onSurfaceVariant)
                    }
                    content?.let {
                        Spacer(Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = it)
                    }
                }
            }
            if (onClick != null && content == null) {
                Surface(
                    onClick = onClick,
                    enabled = enabled,
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) { Column(content = inner) }
            } else {
                Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(content = inner)
                }
            }
        }
    }
}

@Composable
fun GdField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    visual: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        leadingIcon = if (leading != null) ({ PhIcon(leading, size = 20.dp) }) else null,
        trailingIcon = trailing,
        visualTransformation = visual,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = cs.primary,
            focusedLabelColor = cs.primary,
            cursorColor = cs.primary,
            unfocusedBorderColor = cs.outline,
        ),
    )
}

/** Bottom sheet that slides out smoothly before it is removed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            content(close)
        }
    }
}
