package com.textbookgo.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.textbookgo.mobile.ui.MainViewModel
import com.textbookgo.mobile.ui.TextbookGoApp
import com.textbookgo.mobile.ui.theme.TextbookGoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TextbookGoApplication).container

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()

            TextbookGoTheme(theme = preferences.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TextbookGoApp(viewModel)
                }
            }
        }
    }
}
