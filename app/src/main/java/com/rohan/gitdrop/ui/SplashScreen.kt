package com.rohan.gitdrop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rohan.gitdrop.ui.theme.Brand
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val ring = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        text.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(550)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(Brand), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        alpha = ring.value
                        val s = 0.88f + 0.12f * ring.value
                        scaleX = s
                        scaleY = s
                    }
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) { PhIcon(Ph.Branch, size = 54.dp, tint = Color.White) }

            Spacer(Modifier.height(26.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = text.value
                    translationY = (1f - text.value) * 12.dp.toPx()
                },
            ) {
                Text("GitDrop", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Phone se seedha GitHub",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}
