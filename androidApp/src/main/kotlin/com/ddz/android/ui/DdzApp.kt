package com.ddz.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ddz.android.ui.screens.GameScreen
import com.ddz.android.ui.screens.HomeScreen
import com.ddz.android.ui.screens.JoinScreen
import com.ddz.android.ui.screens.LobbyScreen

/** Root composable: a single activity, state-driven screen switching. */
@Composable
fun DdzApp(viewModel: LobbyViewModel) {
    val ui by viewModel.ui.collectAsState()

    BackHandler(enabled = ui.screen != Screen.HOME) {
        when (ui.screen) {
            Screen.JOIN -> viewModel.backHome()
            Screen.LOBBY, Screen.TABLE -> viewModel.leave()
            Screen.HOME -> Unit
        }
    }

    when (ui.screen) {
        Screen.HOME -> HomeScreen(
            ui = ui,
            onNameChange = viewModel::onNameChange,
            onCreate = viewModel::createRoom,
            onJoin = viewModel::showJoin,
            onPractice = viewModel::startPractice,
            onClearError = viewModel::clearError,
        )

        Screen.JOIN -> JoinScreen(
            ui = ui,
            onBack = viewModel::backHome,
            onRefresh = viewModel::beginScan,
            onJoin = viewModel::join,
            onManualJoin = viewModel::joinManual,
            onClearError = viewModel::clearError,
        )

        Screen.LOBBY -> LobbyScreen(
            ui = ui,
            onStart = viewModel::startGame,
            onLeave = viewModel::leave,
            onKick = viewModel::kick,
            onClearError = viewModel::clearError,
        )

        Screen.TABLE -> GameScreen(
            ui = ui,
            onPlay = viewModel::play,
            onPass = viewModel::pass,
            onBid = viewModel::bid,
            onRestart = viewModel::startGame,
            onLeave = viewModel::leave,
            onClearError = viewModel::clearError,
        )
    }
}
