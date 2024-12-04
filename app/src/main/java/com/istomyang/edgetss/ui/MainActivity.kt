package com.istomyang.edgetss.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import com.istomyang.edgetss.ui.theme.EdgeTSSTheme
import com.istomyang.edgetss.ui.view.LocalMainViewModel
import com.istomyang.edgetss.ui.view.MainView
import com.istomyang.edgetss.ui.view.MainViewModel

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel by viewModels<MainViewModel>()
        viewModel.ensureLocalInitialized()

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalMainViewModel provides viewModel) {
                EdgeTSSTheme {
                    MainView()
                }
            }
        }
    }
}



