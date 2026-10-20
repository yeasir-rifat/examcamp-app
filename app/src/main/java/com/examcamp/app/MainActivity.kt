package com.examcamp.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.examcamp.app.ui.AppRoot
import com.examcamp.app.ui.theme.ExamcampTheme
import com.examcamp.app.ui.theme.ThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ExamcampTheme(mode = settings.theme, lang = settings.lang) {
                AppRoot(vm = vm, activity = this@MainActivity, dark = dark)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.events.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onStop() {
        vm.flushAll()
        super.onStop()
    }
}
