package com.example.arduinoide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IdeTheme {
                val vm: IdeViewModel = viewModel()
                if (vm.showLibraries) LibraryScreen(vm) else IdeScreen(vm)
            }
        }
    }
}
