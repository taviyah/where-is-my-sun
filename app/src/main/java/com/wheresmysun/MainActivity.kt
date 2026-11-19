package com.wheresmysun

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.wheresmysun.ui.WheresMySunApp
import com.wheresmysun.ui.theme.WheresMySunTheme

class MainActivity : ComponentActivity() {
    private val vm: SkyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val eink = vm.einkActive
            // Status bar icons must be dark on the white e-ink theme and light on the dark one.
            LaunchedEffect(eink) {
                val style = if (eink) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            WheresMySunTheme(eink = eink) {
                WheresMySunApp(vm)
            }
        }
    }
}
