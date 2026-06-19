package com.shenlong.sports

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsScore
import com.shenlong.sports.data.AthleteStatus
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shenlong.sports.ui.screens.AthleteManagementScreen
import com.shenlong.sports.ui.screens.LapCountingScreen
import com.shenlong.sports.ui.screens.QrScanScreen
import com.shenlong.sports.ui.screens.RaceSetupScreen
import com.shenlong.sports.ui.screens.ResultsScreen
import com.shenlong.sports.ui.theme.DragonRed
import com.shenlong.sports.ui.theme.ShenlongTheme
import com.shenlong.sports.util.FeedbackHelper
import java.util.Locale

class MainActivity : ComponentActivity() {

    lateinit var feedbackHelper: FeedbackHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        feedbackHelper = FeedbackHelper(applicationContext)
        feedbackHelper.setActivity(this)

        setContent {
            ShenlongTheme {
                ShenlongAppContent(feedbackHelper)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        feedbackHelper.onActivityResumed()
    }

    override fun onDestroy() {
        super.onDestroy()
        feedbackHelper.release()
    }
}

@Composable
fun ShenlongAppContent(feedbackHelper: FeedbackHelper) {
    val navController = rememberNavController()
    val viewModel: com.shenlong.sports.viewmodel.RaceViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Toast 消息
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // 全局语音事件处理（记圈页和扫码页都能触发语音）
    LaunchedEffect(state.voiceEvent) {
        state.voiceEvent?.let {
            feedbackHelper.handleEvent(it)
            viewModel.clearVoice()
        }
    }

    val screens = listOf(
        Screen.Setup,
        Screen.Athletes,
        Screen.Counting,
        Screen.Results
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = DragonRed,
                            indicatorColor = DragonRed,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Setup.route
            ) {
                composable(Screen.Setup.route) {
                    RaceSetupScreen(
                        config = state.config,
                        onSave = { viewModel.updateConfig(it) },
                        onNext = { navController.navigate(Screen.Athletes.route) }
                    )
                }
                composable(Screen.Athletes.route) {
                    AthleteManagementScreen(
                        athletes = state.athletes,
                        onAdd = { num, name, team -> viewModel.addAthlete(num, name, team) },
                        onBatchImport = { lines -> viewModel.batchImport(lines) },
                        onToggleDns = { viewModel.toggleDns(it) },
                        onDelete = { viewModel.deleteAthlete(it) },
                        onNext = { navController.navigate(Screen.Counting.route) }
                    )
                }
                composable(Screen.Counting.route) {
                    LapCountingScreen(
                        state = state,
                        ttsReady = feedbackHelper.isTtsReady,
                        toneEnabled = feedbackHelper.toneEnabled,
                        voiceEnabled = feedbackHelper.voiceEnabled,
                        vibrationEnabled = feedbackHelper.vibrationEnabled,
                        onStart = { viewModel.startTiming() },
                        onReset = { viewModel.resetRace() },
                        onAddLapAll = { viewModel.addLapToAll() },
                        onAddLap = { viewModel.addLap(it) },
                        onSubtractLap = { viewModel.subtractLap(it) },
                        onDnf = { viewModel.markDnf(it) },
                        onConsumeToast = { viewModel.clearToast() },
                        onGoResults = { navController.navigate(Screen.Results.route) },
                        onToneToggle = { feedbackHelper.toneEnabled = it },
                        onVoiceToggle = { feedbackHelper.voiceEnabled = it },
                        onVibrationToggle = { feedbackHelper.vibrationEnabled = it },
                        onQrScan = { navController.navigate("qr_scan") }
                    )
                }
                composable("qr_scan") {
                    // 只传正在记圈的运动员（ACTIVE），完赛的不再识别
                    val activeNumbers = state.athletes
                        .filter { it.status == AthleteStatus.ACTIVE }
                        .map { it.number }
                    QrScanScreen(
                        athleteNumbers = activeNumbers,
                        cooldownSeconds = state.config.qrCooldownSeconds,
                        onLapRecorded = { viewModel.addLap(it, fromQrScan = true) },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Results.route) {
                    val results = remember(state.athletes) { viewModel.buildResults() }
                    ResultsScreen(
                        config = state.config,
                        results = results,
                        elapsedMs = state.elapsedMs
                    )
                }
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Setup : Screen("setup", "比赛设置", Icons.Filled.Settings)
    data object Athletes : Screen("athletes", "运动员", Icons.Filled.Group)
    data object Counting : Screen("counting", "记圈", Icons.Filled.DirectionsRun)
    data object Results : Screen("results", "成绩", Icons.Filled.SportsScore)
}
