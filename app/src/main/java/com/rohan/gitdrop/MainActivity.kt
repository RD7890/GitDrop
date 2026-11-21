package com.rohan.gitdrop

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rohan.gitdrop.ui.HomeScreen
import com.rohan.gitdrop.ui.SettingsScreen
import com.rohan.gitdrop.ui.SplashScreen
import com.rohan.gitdrop.ui.theme.GitDropTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            val dark = when (vm.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            GitDropTheme(dark) { AppNav(vm) }
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val ease = FastOutSlowInEasing
    NavHost(
        navController = nav,
        startDestination = "splash",
        enterTransition = { fadeIn(tween(320, easing = ease)) + slideInHorizontally(tween(320, easing = ease)) { it / 10 } },
        exitTransition = { fadeOut(tween(220, easing = ease)) },
        popEnterTransition = { fadeIn(tween(320, easing = ease)) },
        popExitTransition = { fadeOut(tween(220, easing = ease)) + slideOutHorizontally(tween(320, easing = ease)) { it / 10 } },
    ) {
        composable(
            "splash",
            enterTransition = { fadeIn(tween(0)) },
            exitTransition = { fadeOut(tween(400, easing = ease)) },
        ) {
            SplashScreen {
                nav.navigate("home") { popUpTo("splash") { inclusive = true } }
            }
        }
        composable("home") { HomeScreen(vm, onSettings = { nav.navigate("settings") }) }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
