package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.TransactionType
import com.example.ui.AppTab
import com.example.ui.CashFlowUiState
import com.example.ui.CashFlowViewModel
import com.example.ui.dialogs.AddEditItemDialog
import com.example.ui.dialogs.RecordActualDialog
import com.example.ui.dialogs.SafetyThresholdDialog
import com.example.ui.dialogs.StartingCashDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ItemsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TableScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThresholdDanger
import com.example.util.NotificationHelper
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.createNotificationChannel(this)

        setContent {
            val viewModel: CashFlowViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = uiState.settings.themeMode) {
                CashFlowApp(viewModel = viewModel, uiState = uiState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashFlowApp(
    viewModel: CashFlowViewModel = viewModel(),
    uiState: CashFlowUiState
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Request notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Display user feedback messages in snackbar
    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    // Back handling: if not on Dashboard, navigate back to Dashboard
    BackHandler(enabled = uiState.selectedTab != AppTab.DASHBOARD) {
        viewModel.selectTab(AppTab.DASHBOARD)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldPrimary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "CashFlow",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Forecast & Actuals Tracker",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    val projection = uiState.projection
                    if (projection != null) {
                        val isBreached = projection.isThresholdBreachedAvg || projection.isThresholdBreachedMax
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isBreached) ThresholdDanger.copy(alpha = 0.15f) else EmeraldPrimary.copy(alpha = 0.15f),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { viewModel.showThresholdDialog() }
                                .testTag("top_bar_safety_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBreached) Icons.Default.Warning else Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = if (isBreached) ThresholdDanger else EmeraldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isBreached) "Alert" else "Safe",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBreached) ThresholdDanger else EmeraldPrimary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = uiState.selectedTab == AppTab.DASHBOARD,
                    onClick = { viewModel.selectTab(AppTab.DASHBOARD) },
                    icon = { Icon(Icons.Default.ShowChart, contentDescription = "Projection") },
                    label = { Text("Projection", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_projection")
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == AppTab.TABLE,
                    onClick = { viewModel.selectTab(AppTab.TABLE) },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Ledger") },
                    label = { Text("Ledger", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_ledger")
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == AppTab.ITEMS,
                    onClick = { viewModel.selectTab(AppTab.ITEMS) },
                    icon = { Icon(Icons.Default.Repeat, contentDescription = "Items") },
                    label = { Text("Items", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_items")
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == AppTab.SETTINGS,
                    onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmeraldPrimary)
                }
            } else {
                when (uiState.selectedTab) {
                    AppTab.DASHBOARD -> DashboardScreen(
                        uiState = uiState,
                        onSelectHorizon = { viewModel.selectHorizon(it) },
                        onSelectScenarioFilter = { viewModel.setScenarioFilter(it) },
                        onDismissBanner = { viewModel.dismissBanner() },
                        onViewInLedger = { viewModel.selectTab(AppTab.TABLE) },
                        onEditStartingCash = { viewModel.showStartingCashDialog() },
                        onAdjustThreshold = { viewModel.showThresholdDialog() },
                        onRecordActual = { viewModel.openRecordActualDialog(it) },
                        onAddItem = { viewModel.openAddItemDialog(TransactionType.EXPENSE, isPeriodic = true) }
                    )

                    AppTab.TABLE -> TableScreen(
                        uiState = uiState,
                        onSetTableFilter = { viewModel.setTableFilter(it) },
                        onRecordActual = { viewModel.openRecordActualDialog(it) }
                    )

                    AppTab.ITEMS -> ItemsScreen(
                        uiState = uiState,
                        onEditItem = { viewModel.openEditItemDialog(it) },
                        onToggleActive = { viewModel.toggleItemActive(it) },
                        onDeleteItem = { viewModel.deleteItem(it) },
                        onAddItem = { isPeriodic -> viewModel.openAddItemDialog(TransactionType.EXPENSE, isPeriodic = isPeriodic) }
                    )

                    AppTab.SETTINGS -> SettingsScreen(
                        uiState = uiState,
                        onEditStartingCash = { viewModel.showStartingCashDialog() },
                        onEditThreshold = { viewModel.showThresholdDialog() },
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        onRestoreBackup = { viewModel.restoreBackupFromJson(it) }
                    )
                }
            }
        }
    }

    // --- Interactive Dialog Overlays ---
    if (uiState.isStartingCashDialogVisible) {
        StartingCashDialog(
            currentAmount = uiState.settings.startingCashAmount,
            currentDate = java.time.LocalDate.ofEpochDay(uiState.settings.startingDateEpochDay),
            onConfirm = { amount, date -> viewModel.updateStartingCash(amount, date) },
            onDismiss = { viewModel.hideStartingCashDialog() }
        )
    }

    if (uiState.isThresholdDialogVisible) {
        SafetyThresholdDialog(
            currentThreshold = uiState.settings.minimumThresholdAmount,
            currentNotify = uiState.settings.notifyBelowThreshold,
            onConfirm = { threshold, notify -> viewModel.updateThreshold(threshold, notify) },
            onDismiss = { viewModel.hideThresholdDialog() }
        )
    }

    if (uiState.isItemDialogVisible && uiState.editingItem != null) {
        AddEditItemDialog(
            item = uiState.editingItem!!,
            onSave = { viewModel.saveItem(it) },
            onDelete = if (uiState.editingItem!!.id != 0L) {
                { viewModel.deleteItem(it); viewModel.hideItemDialog() }
            } else null,
            onDismiss = { viewModel.hideItemDialog() }
        )
    }

    if (uiState.isActualDialogVisible && uiState.recordingActualEvent != null) {
        val event = uiState.recordingActualEvent!!
        RecordActualDialog(
            event = event,
            onSaveActual = { amount, notes ->
                viewModel.saveActual(event.itemId, event.date, amount, notes)
            },
            onRemoveActual = {
                viewModel.removeActual(event.itemId, event.date)
            },
            onDismiss = { viewModel.hideRecordActualDialog() }
        )
    }
}
