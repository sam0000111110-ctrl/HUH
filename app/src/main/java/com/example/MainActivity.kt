package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.AyerViewModel
import com.example.ui.MainScreen
import com.example.ui.locale.ProvideAppLanguage
import com.example.ui.theme.AyerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AyerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkPref by viewModel.isDarkTheme.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val useDark = isDarkPref ?: systemInDark

            AyerTheme(darkTheme = useDark) {
                ProvideAppLanguage(language = appLanguage) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

