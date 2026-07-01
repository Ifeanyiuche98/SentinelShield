package com.sentinelshield.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinelshield.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val realTimeProtection by viewModel.realTimeProtection.collectAsState()
    val networkMonitoring by viewModel.networkMonitoring.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val autoScanOnInstall by viewModel.autoScanOnInstall.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val scanFrequency by viewModel.scanFrequency.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Protection Section
        SettingsSection(title = "Protection") {
            SettingsToggle(
                icon = Icons.Filled.Shield,
                title = "Real-time Protection",
                subtitle = "Monitor device for threats in real-time",
                checked = realTimeProtection,
                onCheckedChange = { viewModel.toggleRealTimeProtection(it) }
            )

            SettingsToggle(
                icon = Icons.Filled.Wifi,
                title = "Network Monitoring",
                subtitle = "Monitor network traffic via local VPN",
                checked = networkMonitoring,
                onCheckedChange = { viewModel.toggleNetworkMonitoring(it) }
            )

            SettingsToggle(
                icon = Icons.Filled.InstallMobile,
                title = "Auto-scan New Apps",
                subtitle = "Automatically scan newly installed apps",
                checked = autoScanOnInstall,
                onCheckedChange = { viewModel.toggleAutoScanOnInstall(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications Section
        SettingsSection(title = "Notifications") {
            SettingsToggle(
                icon = Icons.Filled.Notifications,
                title = "Push Notifications",
                subtitle = "Receive alerts for detected threats",
                checked = notificationsEnabled,
                onCheckedChange = { viewModel.toggleNotifications(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Scan Settings
        SettingsSection(title = "Scan Settings") {
            var expanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = { expanded = !expanded }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Scan Frequency", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(text = scanFrequency.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ExpandMore, contentDescription = null)
                }

                if (expanded) {
                    Column(modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp)) {
                        listOf("hourly", "daily", "weekly", "manual").forEach { freq ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = scanFrequency == freq,
                                    onClick = {
                                        viewModel.setScanFrequency(freq)
                                        expanded = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = freq.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Appearance Section
        SettingsSection(title = "Appearance") {
            SettingsToggle(
                icon = Icons.Filled.DarkMode,
                title = "Dark Mode",
                subtitle = "Use dark theme with gold accents",
                checked = darkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Section
        SettingsSection(title = "About") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "SentinelShield", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Custom Android Security App", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Built by Ifeanyi Raymond Uche", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
