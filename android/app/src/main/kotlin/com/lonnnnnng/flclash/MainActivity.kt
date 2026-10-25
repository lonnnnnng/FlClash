package com.lonnnnnng.flclash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.lonnnnnng.flclash.ui.KClashApp
import com.lonnnnnng.flclash.ui.MainViewModel
import com.lonnnnnng.flclash.ui.theme.KClashTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KClashTheme {
                KClashApp(viewModel)
            }
        }
    }
}
