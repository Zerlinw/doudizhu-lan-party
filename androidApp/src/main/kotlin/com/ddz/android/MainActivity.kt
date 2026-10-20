package com.ddz.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddz.android.ui.DdzApp
import com.ddz.android.ui.LobbyViewModel
import com.ddz.android.ui.theme.DdzTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DdzTheme {
                val lobbyViewModel: LobbyViewModel = viewModel()
                DdzApp(viewModel = lobbyViewModel)
            }
        }
    }
}
